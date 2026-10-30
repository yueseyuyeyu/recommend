package com.deng.recommend.service;

import com.deng.recommend.recommend.evaluator.RecommendEvaluator;
import com.deng.recommend.vo.FeedPageVO;
import com.deng.recommend.vo.RecommendVO;

import java.util.List;

/**
 * 推荐服务，对上层屏蔽算法细节。
 *
 * <p>Controller 只依赖本接口，不直接触碰
 * {@link com.deng.recommend.recommend.RecommendEngine}，
 * 保证算法替换不影响接入层。
 */
public interface RecommendService {

    /**
     * 首页个性化推荐（UserCF，不足时热门补足）。
     */
    List<RecommendVO> recommendForUser(Long userId, int topN);

    /**
     * 商品详情页相似推荐（ItemCF）。
     */
    List<RecommendVO> recommendSimilar(Long itemId, int topN);

    /**
     * 热门商品推荐（冷启动与游客场景）。
     */
    List<RecommendVO> recommendHot(int topN);

    /**
     * 猜你喜欢（UserCF + ItemCF 混合）。
     */
    List<RecommendVO> recommendHybrid(Long userId, Long itemId, int topN);

    /**
     * 首页推荐流，游标分页，供无限滚动使用。
     *
     * <p><b>为什么是游标而不是页码</b>：页码分页只能用
     * {@code topN = pageNum × pageSize} 取候选前缀再切片，页码越深算法算得越多，
     * 耗时随滑动距离线性增长。
     *
     * <p><b>本实现</b>：首次请求把整个排行池（{@code recommend.feed-pool-size}，默认 300）
     * 算好并缓存，之后每次翻页只从缓存切一段，单次耗时与页码无关。
     *
     * <p><b>候选池耗尽</b>：协同过滤只覆盖行为矩阵里出现过的商品，
     * 池子不满时按销量从商品表补足，保证仍然能一直往下翻。
     *
     * @param userId   当前用户，可为 null（游客走热门补位）
     * @param cursor   上一页返回的 {@code nextCursor}；首页传 null
     * @param pageSize 每页数量，最大 50
     * @return 本页数据 + 下一页游标
     */
    FeedPageVO recommendFeed(Long userId, String cursor, int pageSize);

    /**
     * 离线评测，输出 Precision / Recall / F1 / Coverage。
     *
     * @param topN 每个用户的推荐数量
     * @return 评测结果
     */
    RecommendEvaluator.EvaluationResult evaluate(int topN);
}
