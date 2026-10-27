package com.deng.recommend.controller;

import com.deng.recommend.common.Result;
import com.deng.recommend.config.SecurityUtils;
import com.deng.recommend.recommend.evaluator.RecommendEvaluator;
import com.deng.recommend.service.RecommendService;
import com.deng.recommend.vo.RecommendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 推荐接口。
 *
 * <p>Controller 只做参数透传，算法选择完全由
 * {@link RecommendService} 内部完成，保证算法可替换。
 */
@Validated
@RestController
@RequestMapping("/api/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    /**
     * 首页个性化推荐，基于 UserCF，不足时以热门商品补足。
     *
     * @param topN 推荐数量，默认 10
     * @return 推荐商品列表
     */
    @GetMapping("/home")
    public Result<List<RecommendVO>> home(@RequestParam(defaultValue = "10") int topN) {
        return Result.success(recommendService.recommendForUser(SecurityUtils.currentUserId(), topN));
    }

    /**
     * 商品详情页相似推荐，基于 ItemCF，公开访问。
     *
     * @param productId 当前商品 ID
     * @param topN     推荐数量，默认 10
     * @return 相似商品列表
     */
    @GetMapping("/similar/{productId}")
    public Result<List<RecommendVO>> similar(@PathVariable Long productId,
                                             @RequestParam(defaultValue = "10") int topN) {
        return Result.success(recommendService.recommendSimilar(productId, topN));
    }

    /**
     * 热门推荐，用于游客与冷启动场景，公开访问。
     *
     * @param topN 推荐数量，默认 10
     * @return 热门商品列表
     */
    @GetMapping("/hot")
    public Result<List<RecommendVO>> hot(@RequestParam(defaultValue = "10") int topN) {
        return Result.success(recommendService.recommendHot(topN));
    }

    /**
     * 混合推荐（UserCF + ItemCF）。
     *
     * <p>{@code itemId} 可为空：携带时以当前商品为锚点做相似扩散，
     * 不携带时退化为纯个性化推荐。
     *
     * @param itemId 锚点商品 ID，可为空
     * @param topN  推荐数量，默认 10
     * @return 推荐商品列表
     */
    @GetMapping("/hybrid")
    public Result<List<RecommendVO>> hybrid(@RequestParam(required = false) Long itemId,
                                            @RequestParam(defaultValue = "10") int topN) {
        Long userId = SecurityUtils.currentUserId();
        return Result.success(recommendService.recommendHybrid(userId, itemId, topN));
    }

    /**
     * 离线评测推荐效果，输出 Precision / Recall / F1 / Coverage。
     *
     * @param topN 每个用户的推荐数量，默认 10
     * @return 评测结果
     */
    @GetMapping("/evaluate")
    public Result<RecommendEvaluator.EvaluationResult> evaluate(@RequestParam(defaultValue = "10") int topN) {
        return Result.success(recommendService.evaluate(topN));
    }
}
