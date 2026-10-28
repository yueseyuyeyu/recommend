package com.deng.recommend.recommend;

import com.deng.recommend.config.RecommendProperties;
import com.deng.recommend.recommend.model.RecommendRequest;
import com.deng.recommend.recommend.model.ScoredItem;
import com.deng.recommend.recommend.model.UserItemMatrix;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合推荐策略：线性加权融合 UserCF 与 ItemCF 的结果。
 *
 * <p><b>融合方式</b>：两个策略的分数域不同（UserCF 为加权得分、ItemCF 为相似度），
 * 直接相加无意义，因此先各自按最大值归一化到 [0,1]，再按配置权重加权求和：
 *
 * <pre>
 * score(i) = userCfWeight × norm(userCfScore(i)) + itemCfWeight × norm(itemCfScore(i))
 * </pre>
 *
 * <p>权重可在 {@code application.yml} 中通过
 * {@code recommend.user-cf-weight} 与 {@code recommend.item-cf-weight} 调整。
 */
@Component
public class HybridStrategy implements RecommendStrategy {

    private final UserCFStrategy userCFStrategy;
    private final ItemCFStrategy itemCFStrategy;
    private final RecommendProperties properties;

    public HybridStrategy(UserCFStrategy userCFStrategy,
                          ItemCFStrategy itemCFStrategy,
                          RecommendProperties properties) {
        this.userCFStrategy = userCFStrategy;
        this.itemCFStrategy = itemCFStrategy;
        this.properties = properties;
    }

    @Override
    public String getName() {
        return "Hybrid";
    }

    @Override
    public List<ScoredItem> recommend(UserItemMatrix matrix, RecommendRequest request) {
        if (matrix == null || matrix.isEmpty()) {
            return Collections.emptyList();
        }

        int topN = request.getTopN() > 0 ? request.getTopN() : properties.getDefaultTopN();

        List<ScoredItem> userCfResult = request.getUserId() == null
                ? Collections.emptyList()
                : userCFStrategy.recommend(matrix, request);
        List<ScoredItem> itemCfResult = request.getItemId() == null
                ? Collections.emptyList()
                : itemCFStrategy.recommend(matrix, request);

        if (userCfResult.isEmpty() && itemCfResult.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Double> merged = new HashMap<>();
        merge(merged, userCfResult, properties.getUserCfWeight());
        merge(merged, itemCfResult, properties.getItemCfWeight());

        List<ScoredItem> result = new ArrayList<>(merged.size());
        for (Map.Entry<Long, Double> entry : merged.entrySet()) {
            result.add(new ScoredItem(entry.getKey(), entry.getValue(), "综合您的浏览与购买偏好推荐"));
        }
        result.sort(null);
        if (result.size() > topN) {
            return new ArrayList<>(result.subList(0, topN));
        }
        return result;
    }

    /**
     * 将单个策略的结果归一化后并入总分表。
     */
    private void merge(Map<Long, Double> merged, List<ScoredItem> items, double weight) {
        if (items.isEmpty() || weight <= 0.0) {
            return;
        }
        double maxScore = items.stream()
                .mapToDouble(ScoredItem::getScore)
                .max()
                .orElse(0.0);
        if (maxScore <= 0.0) {
            return;
        }
        for (ScoredItem item : items) {
            double normalized = item.getScore() / maxScore;
            merged.merge(item.getItemId(), normalized * weight, Double::sum);
        }
    }
}
