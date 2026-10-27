package com.deng.recommend.service;

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
     * 离线评测，输出 Precision / Recall / F1 / Coverage。
     *
     * @param topN 每个用户的推荐数量
     * @return 评测结果
     */
    RecommendEvaluator.EvaluationResult evaluate(int topN);
}
