package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;


    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    @Override
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        log.info("添加购物车:{}",shoppingCartDTO);
        //添加到购物车里的时候，首先需要判断是否存在于购物车内
        //通过套餐/商品id(+口味id)+用户id来进行查找
        //通过拦截器来获取userid
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(BaseContext.getCurrentId())
                .dishId(shoppingCartDTO.getDishId())
                .setmealId(shoppingCartDTO.getSetmealId())
                .dishFlavor(shoppingCartDTO.getDishFlavor())
                .build();
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        //如果存在是update，如果不存在是insert
        if(list != null && list.size() > 0){
            //因为这里通过这些条件来查找，只可能有两种情况，第一种为空，第二种为一条数据
            //所以可以通过get(0)来获取查找到的那条信息
            ShoppingCart cart = list.get(0);
            cart.setNumber(cart.getNumber()+1);//update
            shoppingCartMapper.updateNumberById(cart);
        }else{
            //不同用户需要有自己的购物车
            //先要知道你查询的是套餐还是商品
            Long dishId = shoppingCartDTO.getDishId();

            //判断本次添加到购物车的是商品还是套餐
            if(dishId != null){
                //本次添加到购物车的是商品
                Dish dish = dishMapper.getById(dishId);
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
            }else{
                //本次添加到购物车的是套餐
                Setmeal setmeal = setmealMapper.getById(shoppingCartDTO.getSetmealId());
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }

            shoppingCart.setNumber(1);
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(shoppingCart);
        }

    }

    /**
     * 查看购物车
     * @return
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        return list;
    }


    /**
     * 清空购物车
     */
    @Override
    public void cleanShoppingCart() {
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(userId)
                .build();
        shoppingCartMapper.deleteShoppingCart(shoppingCart);
    }

    /**
     * 删除购物车中的一个商品
     * @param shoppingCartDTO
     */
    @Override
    public void deleteOnlyShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        //先判断该商品的数量，如果是1，则直接删除，否则减少数量
        ShoppingCart shoppingCart = ShoppingCart.builder()
                .userId(BaseContext.getCurrentId())
                .dishId(shoppingCartDTO.getDishId())
                .setmealId(shoppingCartDTO.getSetmealId())
                .dishFlavor(shoppingCartDTO.getDishFlavor())
                .build();
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        //这里找到的应该是一个商品，所以直接get(0)
        ShoppingCart deleteShoppingCart = list.get(0);
        if(deleteShoppingCart.getNumber() == 1){
            //如果数量为1，则直接删除
            shoppingCartMapper.deleteShoppingCart(deleteShoppingCart);
        }else{
            //如果数量不为1，则减少数量
            deleteShoppingCart.setNumber(deleteShoppingCart.getNumber()-1);
            shoppingCartMapper.updateNumberById(deleteShoppingCart);
        }

    }


}
