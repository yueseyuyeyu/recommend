package com.deng.recommend.controller;

import com.deng.recommend.common.Result;
import com.deng.recommend.service.CategoryService;
import com.deng.recommend.vo.CategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品类目接口。
 */
@Validated
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 查询两级类目树，供前台导航菜单使用。
     *
     * @return 一级类目列表，含二级子列表
     */
    @GetMapping
    public Result<List<CategoryVO>> tree() {
        return Result.success(categoryService.tree());
    }
}
