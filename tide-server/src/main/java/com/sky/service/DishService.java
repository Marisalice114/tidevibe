package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;

import java.util.List;

public interface DishService {

    /**
     * 新增商品和口味
     * @param dishDTO
     */
    void saveWithFlavor(DishDTO dishDTO);

    /**
     * 分页查询商品
     * @param dishPageQueryDTO
     * @return
     */
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 批量删除商品
     * @param ids
     */
    void deleteBatch(List<Long> ids);

    /**
     * 起售或停售商品
     * @param id
     * @return
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据id查询商品
     * @param id
     * @return
     */
    DishVO getByIdWithFlavor(Long id);

    /**
     * 修改商品
     * @param dishDTO
     */
    void updateWithFlavor(DishDTO dishDTO);

    /**
     * 根据分类id查询商品
     * @param categoryId
     * @return
     */
    List<Dish> list(Long categoryId);

    /**
     * 动态条件查询商品
     * @param dish
     * @return
     */
    List<DishVO> dyList(Dish dish);
}
