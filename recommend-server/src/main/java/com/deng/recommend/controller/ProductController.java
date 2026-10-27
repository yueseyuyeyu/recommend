package com.deng.recommend.controller;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.Result;
import com.deng.recommend.dto.ProductQuery;
import com.deng.recommend.service.ProductService;
import com.deng.recommend.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口：前台商品浏览。
 */
@Validated
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * 分页查询在售商品，支持关键字、类目与价格区间筛选。
     *
     * @param query    查询条件，由 Spring 自动绑定同名请求参数
     * @param pageNum  页码，默认 1
     * @param pageSize 每页条数，默认 20
     * @return 分页商品列表
     */
    @GetMapping
    public Result<PageResult<ProductVO>> page(ProductQuery query,
                                              @RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(productService.page(query, pageNum, pageSize));
    }

    /**
     * 查询商品详情。
     *
     * @param id 商品 ID
     * @return 商品详情
     */
    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.success(productService.detail(id));
    }
}
