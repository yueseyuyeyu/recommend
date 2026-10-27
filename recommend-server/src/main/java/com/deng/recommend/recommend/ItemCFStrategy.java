package com.deng.recommend.recommend;

import com.deng.recommend.config.RecommendProperties;
import com.deng.recommend.recommend.model.RecommendRequest;
import com.deng.recommend.recommend.model.ScoredItem;
import com.deng.recommend.recommend.model.UserItemMatrix;
import com.deng.recommend.recommend.similarity.CosineSimilarity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于物品的协同过滤（ItemCF）。
 *
 * <p><b>算法步骤</b>：
 * <ol>
 *   <li>以「物品 → 用户 → 加权分」倒排向量表示每个物品的受众</li>
 *   <li>计算目标物品与其余物品的余弦相似度（可选热门惩罚）</li>
 *   <li>按相似度降序取 Top-N，排除目标物品自身与已下架物品</li>
 * </ol>
 *
 * <p><b>热门惩罚</b>：热门物品与大量用户共现，容易与"万物相似"。
 * 通过 {@link CosineSimilarity#computeWithPenalty} 除以 {@code |受众|^alpha} 抑制该效应，
 * alpha = 0 时退化为标准余弦相似度。
 *
 * <p><b>适用</b>：商品详情页「相似商品」——物品相似度相对稳定，可离线预计算并缓存。
 */
@Component
public class ItemCFStrategy implements RecommendStrategy {

    private final RecommendProperties properties;

    public ItemCFStrategy(RecommendProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getName() {
        return "ItemCF";
    }

    @Override
    public List<ScoredItem> recommend(UserItemMatrix matrix, RecommendRequest request) {
        Long itemId = request.getItemId();
        if (itemId == null || matrix == null || matrix.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Double> targetUsers = matrix.getItemUsers(itemId);
        if (targetUsers.isEmpty()) {
            // 目标商品无共现数据，交由上层降级为同类目热门
            return Collections.emptyList();
        }

        int topN = request.getTopN() > 0 ? request.getTopN() : properties.getDefaultTopN();
        Set<Long> excluded = buildExcludeSet(request);
        double penalty = properties.getPopularityPenalty();

        List<ScoredItem> similarItems = new ArrayList<>();
        for (Long otherItemId : matrix.allItemIds()) {
            if (Objects.equals(otherItemId, itemId)) {
                continue;
            }
            double similarity = CosineSimilarity.computeWithPenalty(
                    targetUsers, matrix.getItemUsers(otherItemId), penalty);
            if (similarity > 0.0) {
                similarItems.add(new ScoredItem(otherItemId, similarity, "关注该商品的用户也关注了此商品"));
            }
        }

        return similarItems.stream()
                .filter(item -> !excluded.contains(item.getItemId()))
                .sorted()
                .limit(topN)
                .collect(Collectors.toList());
    }

    private Set<Long> buildExcludeSet(RecommendRequest request) {
        if (request.getExcludeItemIds() == null || request.getExcludeItemIds().isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(request.getExcludeItemIds());
    }
}
