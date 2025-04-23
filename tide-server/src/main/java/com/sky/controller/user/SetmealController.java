package com.sky.controller.user;


import com.sky.constant.StatusConstant;
import com.sky.entity.Setmeal;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;



@RestController("userSetmealController")
@RequestMapping("/user/setmeal")
@Slf4j
@Tag(name =  "c端套餐相关接口")
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    public SetmealController(SetmealService setmealService) {
        this.setmealService = setmealService;
    }

    @Cacheable(cacheNames = "setmealCache",key = "#categoryId") //key现在为 setmealCache:1
    @RequestMapping("/list")
    @Operation(summary = "根据分类id查询套餐")
    public Result<List<Setmeal>> list(Long categoryId){
        log.info("根据分类id查询套餐：{}",categoryId);

        //这里将两个条件进行了封装来进行对于这两个条件的查询
        Setmeal setmeal = Setmeal.builder()
                .categoryId(categoryId)
                .status(StatusConstant.ENABLE)
                .build();

        List<Setmeal> list = setmealService.list(setmeal);
        return Result.success(list);
    }

    @RequestMapping("/dish/{id}")
    @Operation(summary = "根据套餐id查询包含的商品")
    public Result<List<DishItemVO>> dishList(@PathVariable Long id){
        log.info("根据套餐id查询包含的商品：{}",id);
        List<DishItemVO> list = setmealService.getDishItemById(id);
        return Result.success(list);
    }
}
