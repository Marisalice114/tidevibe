package com.sky.service;

import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;

import java.time.LocalDateTime;

public interface WorkspaceService {
    /**
     * 获取今日运营数据
     * @return
     */
    BusinessDataVO getBusinessData();

    /**
     * 获取指定范围运营数据
     * @return
     */
    BusinessDataVO getBusinessData(LocalDateTime beginTime, LocalDateTime endTime);

    /**
     * 查看起售停售套餐
     * @return
     */
    SetmealOverViewVO getOverviewSetmeals();

    /**
     * 查看起售停售商品
     * @return
     */
    DishOverViewVO getOverviewDishes();

    /**
     * 查看所有订单情况
     * @return
     */
    OrderOverViewVO getOverviewOrders();

}
