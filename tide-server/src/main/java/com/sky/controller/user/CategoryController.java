package com.sky.controller.user;


import com.sky.entity.Category;
import com.sky.result.Result;
import com.sky.service.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

//注意这里要和admin的controller不同名，不然就会出现注入冲突
@RestController("userCategoryController")
@RequestMapping("/user/category")
@Slf4j
@Tag(name = "c端分类相关接口")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping("/list")
    @Operation(summary = "获取分类列表")
    public Result<List<Category>> getList(Integer type){
        log.info("获取分类列表:{}", (type != null && type == 1) ? "商品" : "套餐" );
        List<Category> list = categoryService.list(type);
        return Result.success(list);
    }
}
