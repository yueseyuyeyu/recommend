package com.deng.recommend.service;

import com.deng.recommend.common.PageResult;
import com.deng.recommend.recommend.evaluator.RecommendEvaluator;
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
     * 分页推荐流，供首页无限滚动使用。
     *
     * <p><b>为什么需要它</b>：{@link #recommendForUser} 只能返回固定 Top-N，
     * 首页想「滚到底继续加载」就必须能翻页，且翻页过程中不能出现重复商品。
     *
     * <p><b>实现思路</b>：算法是纯函数，给定相同输入必然返回相同序列，
     * 因此用 {@code topN = pageNum × pageSize} 取候选前缀再切片，
     * 天然保证第 1 页是第 2 页的前缀，不会重复也不会跳项。
     *
     * <p><b>候选耗尽怎么办</b>：协同过滤的候选池有限（只覆盖行为矩阵里出现过的商品），
     * 翻到后面会不够。此时按销量从商品表补足，保证无限滚动始终有内容。
     *
     * @param userId   当前用户，可为 null（游客走热门）
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页数量，最大 50
     * @return 分页推荐结果，{@code total} 为在售商品总数
     */
    PageResult<RecommendVO> recommendFeed(Long userId, int pageNum, int pageSize);

    /**
     * 离线评测，输出 Precision / Recall / F1 / Coverage。
     *
     * @param topN 每个用户的推荐数量
     * @return 评测结果
     */
    RecommendEvaluator.EvaluationResult evaluate(int topN);
}
