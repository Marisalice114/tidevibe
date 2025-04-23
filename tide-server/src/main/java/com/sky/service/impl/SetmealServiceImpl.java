package com.sky.service.impl;


import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Set;

@Service
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private CategoryServiceImpl categoryService;

    /**
     * 新增套餐，同时需要保存套餐和商品的关联关系
     * @param setmealDTO
     */
    @Transactional
    @Override
    public void saveWithDish(SetmealDTO setmealDTO) {
        //首先处理菜单基本信息，保存套餐信息到setmeal表中
        Setmeal setmeal = Setmeal.builder()
                .categoryId(setmealDTO.getCategoryId())
                .name(setmealDTO.getName())
                .price(setmealDTO.getPrice())
                .description(setmealDTO.getDescription())
                .image(setmealDTO.getImage())
                .status(StatusConstant.DISABLE)
                .build();

        setmealMapper.insert(setmeal);
        //接着处理setmealDishes，保存套餐和商品的关联关系到setmeal_dish表中
        //通过主键回显来获得套餐的id
        Long setmealId = setmeal.getId();
        setmealDTO.getSetmealDishes().forEach(dish -> dish.setSetmealId(setmealId));
        //插入套餐数据
        setmealDishMapper.insertBatch(setmealDTO.getSetmealDishes());
    }

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(),page.getResult());
    }

    /**
     * 根据id查询套餐
     * @param id
     * @return
     */
    @Transactional
    @Override
    public SetmealVO getById(Long id) {
        //先根据id来查询套餐信息
        Setmeal setmeal = setmealMapper.getById(id);
        //然后根据id来查询套餐和商品的关联表
        List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);



        //创建一个setmealvo对象来接受这些参数
        SetmealVO setmealVO = SetmealVO.builder()
                .id(setmeal.getId())
                .categoryId(setmeal.getCategoryId())
                .name(setmeal.getName())
                .price(setmeal.getPrice())
                .status(setmeal.getStatus())
                .description(setmeal.getDescription())
                .image(setmeal.getImage())
                .updateTime(setmeal.getUpdateTime())
                .setmealDishes(setmealDishes)
                .build();

        return setmealVO;
    }


    /**
     * 批量删除套餐
     * @param ids
     */
    @Override
    public void deleteBatch(List<Long> ids) {
        //起售中的套餐不能删除，所以需要先遍历一遍套餐
        ids.forEach(id -> {
            //通过mapper来获取id所对应的草滩
            Setmeal setmeal = setmealMapper.getById(id);
            if (setmeal.getStatus() == StatusConstant.ENABLE){
                //起售中的套餐不能删除
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        });
//        //删除套餐表和套菜商品表中的相关内容 这里可以不用遍历，从而简化sql语句条数
//        ids.forEach(id -> {
//            setmealMapper.deleteById(id);
//            setmealDishMapper.deleteBySetmealId(id);
//        });
        setmealMapper.deleteByIds(ids);
        setmealDishMapper.deleteBySetmealIds(ids);

    }

    /**
     * 修改套餐
     * @param setmealDTO
     */
    @Override
    public void updateWithDish(SetmealDTO setmealDTO) {
        //从DTO中拿到基础信息
        Setmeal setmeal = Setmeal.builder()
                .id(setmealDTO.getId())
                .categoryId(setmealDTO.getCategoryId())
                .name(setmealDTO.getName())
                .price(setmealDTO.getPrice())
                .description(setmealDTO.getDescription())
                .image(setmealDTO.getImage())
                .status(setmealDTO.getStatus())
                .build();
        setmealMapper.update(setmeal);

        //处理setmealDishes
        //获取setmealId
        Long setmealId = setmealDTO.getId();
        //先删除关系再生成关系
        setmealDishMapper.deleteBySetmealId(setmealId);
        //获取新的setmealdishes的list
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        setmealDishes.forEach(dish -> dish.setSetmealId(setmealId));
        //将新的关系list表插入到setmeal_dish表中
        setmealDishMapper.insertBatch(setmealDishes);

    }

    /**
     * 起售停售
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        //起售套餐时，要先判断套餐中有没有停售商品，有的话应该抛出异常
        //这里获得的应该是套餐的id
        if(status == StatusConstant.ENABLE){
            List<Dish> setmealDishList = setmealDishMapper.getListBySetmealId(id);
            //遍历套餐中的商品，判断是否有停售商品
            for (Dish dish : setmealDishList) {
                if(dish.getStatus() == StatusConstant.DISABLE){
                    //如果有停售商品，抛出异常
                    throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                }
            }

            }
        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);
    }

    /**
     * 动态条件查询套餐
     * @param setmeal
     * @return
     */
    @Override
    public List<Setmeal> list(Setmeal setmeal) {
        //根据传入的条件查询套餐
        List<Setmeal> list = setmealMapper.list(setmeal);
        return list;
    }

    /**
     * 根据套餐id查询包含的商品
     * @param id
     * @return
     */
    @Override
    public List<DishItemVO> getDishItemById(Long id) {
        List<DishItemVO> dishItemVOList = setmealMapper.getBySetmealId(id);
        return dishItemVOList;
    }

}
