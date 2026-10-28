package com.deng.recommend.recommend;

import com.deng.recommend.config.RecommendProperties;
import com.deng.recommend.recommend.model.RecommendRequest;
import com.deng.recommend.recommend.model.ScoredItem;
import com.deng.recommend.recommend.model.UserItemMatrix;
import com.deng.recommend.recommend.similarity.CosineSimilarity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于用户的协同过滤（UserCF）。
 *
 * <p><b>算法步骤</b>：
 * <ol>
 *   <li>以「用户 → 物品 → 加权分」向量表示每个用户的兴趣</li>
 *   <li>计算目标用户与其余用户的余弦相似度</li>
 *   <li>取相似度最高的 K 个用户作为邻居</li>
 *   <li>汇总邻居交互过、而目标用户未交互过的物品，
 *       按 {@code 相似度 × 邻居对该物品的加权分} 累加打分</li>
 *   <li>按分数降序取 Top-N</li>
 * </ol>
 *
 * <p><b>适用</b>：首页「猜你喜欢」等需要挖掘跨类目兴趣的场景。
 * <b>不适用</b>：用户数远大于物品数且行为稀疏时，可考虑改用 ItemCF。
 */
@Component
public class UserCFStrategy implements RecommendStrategy {

    private final RecommendProperties properties;

    public UserCFStrategy(RecommendProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getName() {
        return "UserCF";
    }

    @Override
    public List<ScoredItem> recommend(UserItemMatrix matrix, RecommendRequest request) {
        Long userId = request.getUserId();
        if (userId == null || matrix == null || matrix.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Double> targetItems = matrix.getUserItems(userId);
        if (targetItems.isEmpty()) {
            // 目标用户无历史行为，交由上层触发冷启动兜底
            return Collections.emptyList();
        }

        int neighborK = request.getNeighborK() > 0 ? request.getNeighborK() : properties.getNeighborK();
        int topN = request.getTopN() > 0 ? request.getTopN() : properties.getDefaultTopN();
        Set<Long> excluded = buildExcludeSet(request);

        // 1. 计算目标用户与所有其他用户的相似度
        List<ScoredItem> neighbors = new ArrayList<>();
        for (Long otherUserId : matrix.allUserIds()) {
            if (Objects.equals(otherUserId, userId)) {
                continue;
            }
            double similarity = CosineSimilarity.compute(targetItems, matrix.getUserItems(otherUserId));
            if (similarity > 0.0) {
                neighbors.add(new ScoredItem(otherUserId, similarity));
            }
        }
        if (neighbors.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 取相似度最高的 K 个邻居（ScoredItem 自然序为分数降序）
        neighbors.sort(null);
        int actualNeighborCount = Math.min(neighborK, neighbors.size());

        // 3. 汇总邻居的候选物品并加权打分
        Map<Long, Double> itemScores = new HashMap<>();
        for (int i = 0; i < actualNeighborCount; i++) {
            ScoredItem neighbor = neighbors.get(i);
            Map<Long, Double> neighborItems = matrix.getUserItems(neighbor.getItemId());
            for (Map.Entry<Long, Double> entry : neighborItems.entrySet()) {
                Long itemId = entry.getKey();
                // 目标用户已交互过的物品不再推荐
                if (targetItems.containsKey(itemId)) {
                    continue;
                }
                itemScores.merge(itemId, neighbor.getScore() * entry.getValue(), Double::sum);
            }
        }

        // 4. 排序并取 Top-N
        String reason = "与您兴趣相似的 " + actualNeighborCount + " 位用户也在关注";
        return itemScores.entrySet().stream()
                .filter(entry -> !excluded.contains(entry.getKey()))
                .map(entry -> new ScoredItem(entry.getKey(), entry.getValue(), reason))
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
