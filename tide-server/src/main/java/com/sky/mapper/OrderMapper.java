package com.sky.mapper;


import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.OrderStatisticsVO;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    /**
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber} and user_id = #{userId}")
    Orders getByNumberAndUserId(String orderNumber,Long userId);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);


    /**
     * 分页查询订单
     * @param ordersPageQueryDTO
     * @return
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);


    /**
     * 根据id查询订单
     * @param id
     * @return
     */
    @Select( "select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 统计订单各个状态数量
     * @return
     */
    OrderStatisticsVO statistics();

    /**
     * 查询超时订单
     * @param status
     * @param orderTime
     * @return
     */
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime orderTime);

    /**
     * 根据动态条件统计数量
     * @param map
     * @return
     */
    Double sumByMap(Map map);

    /**
     * 单次查询
     * @param map
     * @return
     */
    @MapKey("date")
    List<Map<String, Object>> getSumByDateRange(Map map);

    /**
     * 根据动态条件获取每天的订单数
     * @param beginTime
     * @param endTime
     * @return
     */
    @MapKey("date")
    List<Map<String, Object>> getCountByDateRange(
            @Param("begin") LocalDateTime beginTime,
            @Param("end") LocalDateTime endTime,
            @Param("status") Integer status
    );

    /**
     * 根据动态条件查询销量排名top10
     * @param params
     * @return
     */
    @MapKey("name")
    List<Map<String, Object>> getTop10SalesByDateRange(Map<String, Object> params);

    /**
     * 获取订单总览
     * @return
     */
    OrderOverViewVO getOverview();

    /**
     * 携带版本号的订单更新
     * @param orders
     * @return
     */
    int updateWithVersion(Orders orders);
}
