package com.deng.recommend.controller;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.common.Result;
import com.deng.recommend.config.SecurityUtils;
import com.deng.recommend.dto.ReviewRequest;
import com.deng.recommend.service.ReviewService;
import com.deng.recommend.vo.ReviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品评价接口。
 *
 * <p>提交评价需登录（用户 ID 取自登录态），查询评价公开。
 */
@Validated
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 提交商品评价，每用户对每商品限评一次。
     *
     * @param request 评价请求
     * @return 空数据成功响应
     */
    @PostMapping
    public Result<Void> submit(@Valid @RequestBody ReviewRequest request) {
        reviewService.submit(SecurityUtils.currentUserId(), request);
        return Result.success(null);
    }

    /**
     * 分页查询某商品的评价。
     *
     * @param productId 商品 ID
     * @param pageNum   页码，默认 1
     * @param pageSize  每页条数，默认 20
     * @return 分页评价列表
     */
    @GetMapping
    public Result<PageResult<ReviewVO>> pageByProduct(@RequestParam Long productId,
                                                      @RequestParam(defaultValue = "1") long pageNum,
                                                      @RequestParam(defaultValue = "20") long pageSize) {
        return Result.success(reviewService.pageByProduct(productId, pageNum, pageSize));
    }
}
