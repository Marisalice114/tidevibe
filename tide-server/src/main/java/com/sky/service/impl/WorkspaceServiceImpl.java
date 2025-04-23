package com.sky.service.impl;

import com.sky.entity.Dish;
import com.sky.entity.Orders;
import com.sky.entity.Setmeal;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private DishMapper dishMapper;

    /**
     * 获取今日运营数据
     * @return
     */
    @Override
    public BusinessDataVO getBusinessData(LocalDateTime beginTime, LocalDateTime endTime) {
        // 需要获取新增用户数，订单完成率，营业额，平均客单价，有效订单数

        // 新增用户数
        Integer newUserCount = 0;
        List<Map<String, Object>> newUserStatistics = userMapper.getNewUserStatistics(beginTime, endTime);
        if (newUserStatistics != null && !newUserStatistics.isEmpty()) {
            Map<String, Object> todayData = newUserStatistics.get(0);
            Number countNumber = (Number) todayData.get("user_count");
            newUserCount = countNumber != null ? countNumber.intValue() : 0;
        }

        // 订单完成率
        List<Map<String, Object>> orderCountData = orderMapper.getCountByDateRange(beginTime, endTime, null);
        List<Map<String, Object>> validOrderCountData = orderMapper.getCountByDateRange(beginTime, endTime, Orders.COMPLETED);

        // 处理订单数据
        Map<LocalDate, Integer> orderCountDataMap = convertToDateCountMap(orderCountData);
        Map<LocalDate, Integer> validOrderCountDataMap = convertToDateCountMap(validOrderCountData);

        // 计算时间范围内的总订单数，而不是单天的
        Integer orderCount = orderCountDataMap.values().stream().mapToInt(Integer::intValue).sum();
        Integer validOrderCount = validOrderCountDataMap.values().stream().mapToInt(Integer::intValue).sum();

        Double orderCompletionRate = 0.0;
        if (orderCount > 0) {
            orderCompletionRate = (double) validOrderCount / orderCount;
            // 保留2位小数
            orderCompletionRate = Math.round(orderCompletionRate * 100) / 100.0;
        }

        // 营业额
        Map<String, Object> params = new HashMap<>();
        params.put("begin", beginTime);
        params.put("end", endTime);
        params.put("status", Orders.COMPLETED);

        Double turnover = 0.0;
        List<Map<String, Object>> turnoverData = orderMapper.getSumByDateRange(params);
        if (turnoverData != null && !turnoverData.isEmpty()) {
            Map<String, Object> todayData = turnoverData.get(0);
            Number totalAmount = (Number) todayData.get("total_amount");
            turnover = totalAmount != null ? totalAmount.doubleValue() : 0.0;
        }

        // 平均客单价
        Double unitPrice = 0.0;
        if (validOrderCount > 0) {
            unitPrice = turnover / validOrderCount;
            // 保留2位小数
            unitPrice = Math.round(unitPrice * 100) / 100.0;
        }

        // 返回业务数据VO
        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUserCount)  // 确保与VO中的字段名匹配
                .build();
    }

    // 保留原来的方法，用于获取今日数据，作为一个无参调用
    @Override
    public BusinessDataVO getBusinessData() {
        LocalDate today = LocalDate.now();
        LocalDateTime beginTime = LocalDateTime.of(today, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(today, LocalTime.MAX);

        return getBusinessData(beginTime, endTime);
    }

    /**
     * 获取套餐总览数据
     * @return
     */
    @Override
    public SetmealOverViewVO getOverviewSetmeals() {
        // 查询起售的套餐
        List<Setmeal> onsaleSetmeals = setmealMapper.list(Setmeal.builder().status(1).build());
        // 查询停售的套餐
        List<Setmeal> offsaleSetmeals = setmealMapper.list(Setmeal.builder().status(0).build());

        // 直接使用列表大小
        int sold = onsaleSetmeals.size();
        int discontinued = offsaleSetmeals.size();

        return new SetmealOverViewVO(sold, discontinued);
    }

    /**
     * 获取商品总览数据
     * @return
     */
    @Override
    public DishOverViewVO getOverviewDishes() {
        //查询起售的商品
        List<Dish> onsaleDishes = dishMapper.list(Dish.builder().status(1).build());
        //查询停售的商品
        List<Dish> offsaleDishes = dishMapper.list(Dish.builder().status(0).build());
        int sold = onsaleDishes.size();
        int discontinued = offsaleDishes.size();
        return new DishOverViewVO(sold, discontinued);
    }

    /**
     * 获取订单总览数据
     * @return
     */
    @Override
    public OrderOverViewVO getOverviewOrders() {
        OrderOverViewVO orderOverViewVO = orderMapper.getOverview();
        return orderOverViewVO;
    }


    // 辅助方法：将数据库查询结果转换为日期-数量映射
    private Map<LocalDate, Integer> convertToDateCountMap(List<Map<String, Object>> data) {
        Map<LocalDate, Integer> resultMap = new HashMap<>();
        if (data != null) {
            for (Map<String, Object> map : data) {
                Object dateObj = map.get("order_date");
                LocalDate date;

                // 处理不同类型的日期对象
                if (dateObj instanceof Date) {
                    date = ((Date) dateObj).toLocalDate();
                } else if (dateObj instanceof java.sql.Date) {
                    date = ((java.sql.Date) dateObj).toLocalDate();
                } else if (dateObj instanceof LocalDate) {
                    date = (LocalDate) dateObj;
                } else {
                    // 跳过无法识别的日期格式
                    continue;
                }

                Number countNumber = (Number) map.get("order_count");
                Integer count = countNumber != null ? countNumber.intValue() : 0;
                resultMap.put(date, count);
            }
        }
        return resultMap;
    }

}
