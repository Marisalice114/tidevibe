package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.annotation.OptimisticLock;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.service.PaymentService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private UserMapper userMapper;

//    @Autowired
//    private WeChatPayUtil weChatPayUtil;

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private WebSocketServer webSocketServer;

    // 使用内存缓存记录处理中的订单，防止重复处理
    private final Map<String, Boolean> processingOrders = new ConcurrentHashMap<>();
    @Autowired
    private RedissonClient redissonClient;

    /**
     * 乐观锁添加
     * 乐观锁的核心思想是假设数据一般情况下不会发生冲突，只在数据提交更新时检查是否有冲突。它通过版本号机制实现：
     * 防止数据覆盖：当多个事务同时修改同一条记录时，只有版本号匹配的事务才能成功更新，避免后面的事务无意中覆盖前面事务的更新
     * 不阻塞读操作：相比悲观锁，乐观锁不会锁定资源，允许其他事务继续读取数据
     * 减少死锁风险：不需要长时间持有锁，降低了死锁的风险
     * 提高并发性能：特别适合读多写少的场景
     */

    /**
     * 提交订单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        // 获取用户ID，用于创建锁的唯一标识
        Long userId = BaseContext.getCurrentId();

        // 创建分布式锁，使用用户ID作为锁的一部分，确保同一用户的订单操作是串行的
        String lockKey = "order:submit:" + userId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，最多等待2秒，锁定时间为30秒(自动释放)
            boolean isLocked = lock.tryLock(2, 30, TimeUnit.SECONDS);

            //isLocked变量代表的是"当前线程是否成功获取到了锁"，而不是"锁是否已经被上锁"
            if (!isLocked) {
                log.error("获取分布式锁失败，可能存在并发下单：userId={}", userId);
                throw new OrderBusinessException(MessageConstant.ORDER_FAILD);
            }

            log.info("获取分布式锁成功：{}", lockKey);

            // 处理各种业务异常（地址簿为空/购物车数据为空）
            AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
            if (addressBook == null){
                throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
            }

            ShoppingCart shoppingCart = ShoppingCart.builder()
                    .userId(userId)
                    .build();
            List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
            if(shoppingCartList == null || shoppingCartList.size() == 0){
                throw new AddressBookBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
            }

            // 获取当前用户
            User user = userMapper.getById(userId);

            // 拼写当前的地址
            StringBuilder fullAddress = new StringBuilder();

            // 添加省份名称
            if (addressBook.getProvinceName() != null) {
                fullAddress.append(addressBook.getProvinceName());
            }

            // 添加城市名称
            if (addressBook.getCityName() != null) {
                fullAddress.append(addressBook.getCityName());
            }

            // 添加区/县名称
            if (addressBook.getDistrictName() != null) {
                fullAddress.append(addressBook.getDistrictName());
            }

            // 添加详细地址
            if (addressBook.getDetail() != null) {
                fullAddress.append(addressBook.getDetail());
            }

            // 向订单表插入1条数据
            Orders orders = new Orders();
            BeanUtils.copyProperties(ordersSubmitDTO,orders);
            orders.setOrderTime(LocalDateTime.now());
            orders.setPayStatus(Orders.UN_PAID);
            orders.setStatus(Orders.PENDING_PAYMENT);
            orders.setNumber(String.valueOf(System.currentTimeMillis()));
            orders.setPhone(addressBook.getPhone());
            orders.setConsignee(addressBook.getConsignee());
            orders.setUserName(user.getName());
            orders.setAddress(fullAddress.toString());
            orders.setUserId(userId);

            orderMapper.insert(orders);

            // 向订单明细表插入n条数据
            List<OrderDetail> orderDetailList = new ArrayList<>();
            for(ShoppingCart cart : shoppingCartList){
                OrderDetail orderDetail = new OrderDetail();
                BeanUtils.copyProperties(cart,orderDetail);
                orderDetail.setOrderId(orders.getId());//设置当前订单明细关联的订单id
                orderDetailList.add(orderDetail);
            }

            orderDetailMapper.insertBatch(orderDetailList);

            // 下单成功后清空购物车数据
            shoppingCartMapper.deleteShoppingCart(shoppingCart);

            // 封装VO返回结果
            OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                    .id(orders.getId())
                    .orderNumber(orders.getNumber())
                    .orderAmount(orders.getAmount())
                    .orderTime(orders.getOrderTime())
                    .build();

            return orderSubmitVO;

        } catch (InterruptedException e) {
            log.error("获取锁被中断", e);
            Thread.currentThread().interrupt();
            throw new OrderBusinessException(MessageConstant.ORDER_FAILD);
        } catch (OrderBusinessException | AddressBookBusinessException e) {
            // 直接抛出业务异常
            throw e;
        } catch (Exception e) {
            log.error("下单过程中发生异常", e);
            throw new OrderBusinessException(MessageConstant.ORDER_FAILD);
        } finally {
            // 释放锁，确保即使发生异常也能释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("释放分布式锁：{}", lockKey);
            }
        }
    }
    /**
     * 这里添加分布式锁的逻辑如下
     * 1.创建锁
     * 用用户id创关键一个唯一锁表示，用redission客户端获得锁对象
     * 2.获取锁
     * 最多等待2秒，锁的自动释放时间为30秒
     * 3.业务处理
     * 在finally块中检查当前线程是否持有锁
     * 如果持有锁，则释放锁：lock.unlock()
     * 确保无论业务成功还是异常，锁都能被释放
     */



    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) {
        try {
            // 获取订单号
            String orderNumber = ordersPaymentDTO.getOrderNumber();
            log.info("处理订单支付请求: {}", orderNumber);

            // 如果订单正在处理中，等待处理完成
            if (processingOrders.containsKey(orderNumber)) {
                log.info("订单 {}.正在处理中", orderNumber);
                try {
                    Thread.sleep(500); // 短暂等待
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 标记订单处理中
            processingOrders.put(orderNumber, true);

            try {
                // 获取用户信息
                Long userId = BaseContext.getCurrentId();
                User user = userMapper.getById(userId);

                // 查询订单
                Orders ordersDB = orderMapper.getByNumberAndUserId(orderNumber, userId);

                // 检查订单是否存在
                if (ordersDB == null) {
                    throw new OrderBusinessException("订单不存在");
                }

                // 检查是否为当前用户的订单
                if (!ordersDB.getUserId().equals(userId)) {
                    throw new OrderBusinessException("不是当前用户的订单");
                }

                // 对已支付的订单，直接返回支付参数，而不抛出异常
                if (ordersDB.getPayStatus() == Orders.PAID) {
                    log.info("订单 {} 已支付，直接返回支付参数", orderNumber);
                    return createPaymentVO(orderNumber);
                }

                // 调用支付服务
                JSONObject jsonObject = paymentService.createPayment(
                        orderNumber,
                        ordersDB.getAmount(),
                        "苍穹外卖订单",
                        user.getOpenid()
                );

                // 开发环境下自动处理支付成功
                if (paymentService.handlePaymentResult(orderNumber)) {
                    // 同步标记订单为已支付
                    paySuccess(orderNumber);
                    log.info("订单 {} 支付成功", orderNumber);
                }

                // 转换为前端需要的VO对象
                OrderPaymentVO paymentVO = paymentService.convertToPaymentVO(jsonObject);
                log.info("订单 {} 支付参数生成成功", orderNumber);

                return paymentVO;

            } finally {
                // 确保移除处理中标记
                processingOrders.remove(orderNumber);
            }

        } catch (Exception e) {
            log.error("支付处理异常", e);
            throw new OrderBusinessException("支付失败：" + e.getMessage());
        }
    }

    /**
     * 创建简单的支付参数，兼容前端直接跳转的情况
     */
    private OrderPaymentVO createPaymentVO(String orderNumber) {
        OrderPaymentVO vo = new OrderPaymentVO();

        // 使用简单的值即可，前端已修改为不使用真实微信支付
        vo.setTimeStamp(String.valueOf(System.currentTimeMillis() / 1000));
        vo.setNonceStr("mock_" + System.currentTimeMillis());
        vo.setPackageStr("prepay_id=mock_prepay_" + System.currentTimeMillis());
        vo.setSignType("RSA");
        vo.setPaySign("MOCK_SIGNATURE_" + System.currentTimeMillis());

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    @OptimisticLock(maxRetries = 3)
    public void paySuccess(String outTradeNo) {
        //当前登录用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumberAndUserId(outTradeNo, userId);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        //通过websocket向客户端浏览器推送消息 type orderId content
        Map map = new HashMap();
        map.put("type",1); //1是来单提醒，2是催单
        map.put("orderId",ordersDB.getId());
        map.put("content","订单号:"+outTradeNo);

        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    /**
     * 分页查询订单
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        //这里获得的orderpagequerydto实际上只有三个属性，一个page，一个pageSize，一个status
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        //进行分页查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        //接口文档中需要显示orderDetailList，但是这个字段在数据库中不存在，所以需要手动设置
        //获取分页查询的结果
        List<Orders> orderList = page.getResult();
        //因为在接口要求中，提到了orderDetailList，所以需要手动设置这个字段
        List<OrderVO> orderVOList = new ArrayList<>();
        if(orderList != null && orderList.size() > 0){
            //分页查询查询到了结果
            for (Orders orders : orderList){
                //遍历查询到的订单
                //为每一个订单建立一个orderVO
                //这里需要把order类扩展为orderVO类，从而实现订单详情的展示
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders,orderVO);
                //现在需要基于ordervo来查询订单详情
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
                //将查询到的详细订单信息输入到orderVO中
                //这里的vo其实是做了一种取巧的方法，之前都是把所有相关属性都写一遍
                //而这里用到的vo实际上是将order类中的信息全归为了orderDishes订单商品信息
                //将orderDetailList中的所有商品信息都整合到List<OrderDetail>中
                orderVO.setOrderDetailList(orderDetailList);
                //这里建立好了一个orderVO，所以可以进行添加到orderVOList中
                orderVOList.add(orderVO);
            }

        }
        return new PageResult(page.getTotal(), orderVOList);
    }

    @Override
    public OrderVO getById(Long id) {
        if(id != null){
            Orders orders = orderMapper.getById(id);
            List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders,orderVO);
            orderVO.setOrderDetailList(orderDetailList);
            return orderVO;
        }
        throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
    }

    /**
     * 再来一单
     * @param id
     */
    @Override
    @Transactional
    public void repetition(Long id) {
        // 获取当前用户id
        Long userId = BaseContext.getCurrentId();
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        if(orderDetailList == null || orderDetailList.isEmpty()) {
            throw new OrderBusinessException("订单详情不存在");
        }

        // 可选：先清空购物车
        shoppingCartMapper.deleteShoppingCart(ShoppingCart.builder().userId(userId).build());

        // 将订单详情转换为购物车对象
        List<ShoppingCart> validCarts = new ArrayList<>();

        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = new ShoppingCart();
            shoppingCart.setUserId(userId);

            try {
                if(orderDetail.getSetmealId() != null) {
                    // 套餐逻辑
                    Setmeal setmeal = setmealMapper.getById(orderDetail.getSetmealId());
                    if (setmeal == null || setmeal.getStatus() == 0) {
                        throw new OrderBusinessException(MessageConstant.SETMEAL_DISABLED_OR_NOT_EXIST);
                    }

                    shoppingCart.setSetmealId(setmeal.getId());
                    shoppingCart.setName(setmeal.getName());
                    shoppingCart.setImage(setmeal.getImage());
                    // 使用最新价格
                    shoppingCart.setAmount(setmeal.getPrice());
                } else {
                    // 商品逻辑
                    Dish dish = dishMapper.getById(orderDetail.getDishId());
                    if (dish == null || dish.getStatus() == 0) {
                        throw new OrderBusinessException(MessageConstant.DISH_DISABLED_OR_NOT_EXIST);
                    }

                    shoppingCart.setDishId(dish.getId());
                    shoppingCart.setName(dish.getName());
                    shoppingCart.setImage(dish.getImage());
                    shoppingCart.setDishFlavor(orderDetail.getDishFlavor());
                    // 使用最新价格
                    shoppingCart.setAmount(dish.getPrice());
                }

                shoppingCart.setCreateTime(LocalDateTime.now());
                shoppingCart.setNumber(orderDetail.getNumber());
                validCarts.add(shoppingCart);
            } catch (Exception e) {
                throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
            }
        }

        // 批量添加到购物车（如果有批量插入方法）
        if (!validCarts.isEmpty()) {
            for (ShoppingCart cart : validCarts) {
                shoppingCartMapper.insert(cart);
            }
        }
    }

    /**
     * 取消订单
     * @param id
     */
    @Override
    @OptimisticLock(maxRetries = 3)
    public void userCancel(Long id) {
        //取消订单的可进行情况
        //1.待支付和待接单和已接单的情况下可直接取消订单
        //2.待支付的情况下直接取消支付即可，不需要调用退款接口,待接单和已接单情况下需要调用退款接口
        //3.派送中的情况下，不能取消订单
        //4.待接单状态下取消订单需要给用户退款
        //5.取消订单后需要将订单状态修改为已取消

        // 获取订单信息
        Orders orders = orderMapper.getById(id);

        // 检查订单是否存在
        if(orders == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 订单状态: 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        int status = orders.getStatus();

        if(status == Orders.PENDING_PAYMENT) { // 待付款
            // 设置需要更新的字段
            orders.setStatus(Orders.CANCELLED);
            orders.setCancelReason("用户取消");
            orders.setCancelTime(LocalDateTime.now());
            // 乐观锁重试逻辑由切面处理
        }
        else if(status == Orders.TO_BE_CONFIRMED || status == Orders.CONFIRMED) { // 待接单或已接单
            // 设置需要更新的字段
            orders.setPayStatus(Orders.REFUND);
            orders.setStatus(Orders.CANCELLED);
            orders.setCancelReason("用户取消");
            orders.setCancelTime(LocalDateTime.now());
            // TODO 退款机制待添加
            // 乐观锁重试逻辑由切面处理
        }
        else if(status == Orders.DELIVERY_IN_PROGRESS) { // 派送中
            throw new OrderBusinessException(MessageConstant.ORDER_CANCEL_FAIL_FOR_DELIVERING);
        }
        else {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 条件搜索订单
     * @param ordersPageQueryDTO
     * @return
     */
    @Transactional
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        //如果用orders做返回信息的话，会缺少orderDishes字段，该字段的要求是这样的
        //订单包含的商品，以字符串形式展示
        List<OrderVO> orderVOList = new ArrayList<>();
        //获取订单列表
        List<Orders> ordersList = page.getResult();

        if(ordersList != null && !ordersList.isEmpty()) {
            for (Orders orders : ordersList) {
                OrderVO orderVO = convertToOrderVO(orders);
                orderVOList.add(orderVO);
            }
        }

        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 各个状态的订单数量统计
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        log.info("各个状态的订单数量统计");
        OrderStatisticsVO orderStatisticsVO = orderMapper.statistics();
        return orderStatisticsVO;
    }

    /**
     * 根据id查询订单详细信息,包含orderDisheds
     * @param id
     * @return
     */
    @Override
    public OrderVO getByIdWithDishes(Long id) {
        if(id == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        return convertToOrderVO(orders);
    }

    /**
     * 将Orders对象转换为OrderVO对象，并填充orderDishes字段
     * @param orders 订单对象
     * @return 填充好的OrderVO对象
     */
    private OrderVO convertToOrderVO(Orders orders) {
        if (orders == null) {
            return null;
        }

        // 创建OrderVO对象，复制order属性
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);

        // 查询订单详细信息
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        orderVO.setOrderDetailList(orderDetailList);

        // 构建orderDishes字段
        if (orderDetailList != null && !orderDetailList.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < orderDetailList.size(); i++) {
                OrderDetail detail = orderDetailList.get(i);
                sb.append(detail.getName()).append("*").append(detail.getNumber());
                if (i < orderDetailList.size() - 1) {
                    sb.append("，"); // 注意这里使用中文逗号，保持一致性
                }
            }
            orderVO.setOrderDishes(sb.toString());
        }

        return orderVO;
    }

    /**
     * 接单
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        log.info("接单：{}", ordersConfirmDTO);
        Long orderId = ordersConfirmDTO.getId();
        if (orderId == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //这里不找所有字段，而是只设置需要更新的字段，以免出现问题
        Orders orders = Orders.builder()
                    .id(orderId)
                    .status(Orders.CONFIRMED)
                    .build();
        orderMapper.update(orders);
    }


    /**
     * 拒单
     * @param ordersRejectionDTO
     */
    @OptimisticLock(maxRetries = 3)
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        log.info("拒单：{}", ordersRejectionDTO);
        Long orderId = ordersRejectionDTO.getId();
        if (orderId == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //这里不找所有字段，而是只设置需要更新的字段，以免出现问题
        Orders orders = Orders.builder()
                .id(orderId)
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 商户取消订单
     * @param ordersCancelDTO
     */
    @OptimisticLock(maxRetries = 3)
    @Override
    public void cancelOrderByShop(OrdersCancelDTO ordersCancelDTO) {
        //待付款，待接单，派送中三种状态可以进行取消操作，进行取消操作需要选择取消原因
        Long orderId = ordersCancelDTO.getId();
        if (orderId == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Orders orders = orderMapper.getById(orderId);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (orders.getStatus() != Orders.TO_BE_CONFIRMED && orders.getStatus() != Orders.CONFIRMED && orders.getStatus() != Orders.DELIVERY_IN_PROGRESS) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders1 = Orders.builder()
                .id(orderId)
                .status(Orders.CANCELLED)
                .cancelReason(ordersCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders1);
    }

    /**
     * 派送订单
     * @param id
     */
    @OptimisticLock(maxRetries = 3)
    @Override
    public void deliveryOrder(Long id) {
        //获取当前订单
        Orders orders = orderMapper.getById(id);

        //确认订单处于待派送状态
        if (orders.getStatus() != Orders.CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders1 = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .deliveryStatus(1)
                .build();
        orderMapper.update(orders1);
    }

    /**
     * 完成订单
     * @param id
     */
    @Override
    @OptimisticLock(maxRetries = 3)
    public void completeOrder(Long id) {
        //获取当前订单
        Orders orders = orderMapper.getById(id);
        //确认订单处于派送中状态
        if (orders.getStatus() != Orders.DELIVERY_IN_PROGRESS) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders1 = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders1);
    }

    /**
     * 订单催单
     * @param id
     */
    @Override
    public void reminder(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("type", 2);
        map.put("orderId", id);
        map.put("content", "订单号：" + orders.getNumber());

        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }


}
