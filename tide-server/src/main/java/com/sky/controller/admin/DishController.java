package com.sky.controller.admin;


import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Delete;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Key;
import java.util.List;
import java.util.Set;

@RequestMapping("/admin/dish")
@RestController
@Tag(name = "商品相关接口")
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 新增商品
     * @param dishDTO
     * @return
     */
    @PostMapping
    @Operation(summary = "新增商品和口味")
    public Result save(@RequestBody DishDTO dishDTO){
        log.info("新增商品和口味：{}",dishDTO);
        dishService.saveWithFlavor(dishDTO);

        //清理缓存数据
        String key = "dish_" + dishDTO.getCategoryId();
        cleanCache(key);
        return Result.success();
    }


    @GetMapping("/page")
    @Operation(summary = "商品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO){
        log.info("商品分页查询：{}",dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    @DeleteMapping()
    @Operation(summary = "批量删除商品")
    //地址后面的参数是这样的 1,2,3 可以传入String格式然后自己写语句解析，也可以通过mvc提供的@RequestParam注解来自动解析为List
    public Result delete(@RequestParam List<Long> ids){
        log.info("批量删除商品：{}",ids);
        dishService.deleteBatch(ids);
        //将所有的商品缓存数据删除
        //先获取所有以dish_开头的key
        cleanCache("dish_*");
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @Operation(summary = "起售停售商品")
    public Result startOrStop(@PathVariable Integer status,Long id){
        log.info("起售停售商品：{},{}",status,id);
        dishService.startOrStop(status,id);
        //清理缓存数据，也是全部删除
        cleanCache("dish_*");
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据id查询商品")
    //不能直接用dish，因为dish类中没有包含口味对象
    public Result<DishVO> getById(@PathVariable Long id){
        log.info("根据id查询商品：{}",id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }

    @PutMapping
    @Operation(summary = "修改商品")
    public Result update(@RequestBody DishDTO dishDTO){
        log.info("修改商品：{}",dishDTO);
        dishService.updateWithFlavor(dishDTO);
        //清理缓存数据，也是全部删除
        cleanCache("dish_*");
        return Result.success();
    }

    @GetMapping("/list")
    @Operation(summary = "根据分类id查询商品")
    public Result<List<Dish>> list(Long categoryId){
        log.info("根据分类id查询商品：{}",categoryId);
        List<Dish> list = dishService.list(categoryId);
        return Result.success(list);
    }

    /**
     * 清理缓存数据
     * @param pattern
     */
    private void cleanCache(String pattern){
        Set keys = redisTemplate.keys(pattern);
        redisTemplate.delete(keys);
    }

}
