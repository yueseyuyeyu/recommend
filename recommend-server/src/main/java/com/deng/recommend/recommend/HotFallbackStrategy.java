package com.deng.recommend.recommend;

import com.deng.recommend.config.RecommendProperties;
import com.deng.recommend.recommend.model.RecommendRequest;
import com.deng.recommend.recommend.model.ScoredItem;
import com.deng.recommend.recommend.model.UserItemMatrix;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 热门兜底策略（冷启动解决方案）。
 *
 * <p>按全站加权行为总量排序取 Top-N，用于以下场景：
 * <ul>
 *   <li>新用户无任何历史行为，UserCF 无法计算相似度</li>
 *   <li>新商品无共现数据，ItemCF 无法计算相似度</li>
 *   <li>协同过滤候选结果不足，需要补足至 N 个</li>
 *   <li>推荐引擎异常，降级保证页面可用</li>
 * </ul>
 *
 * <p>时间窗口由上层控制：传入用近 N 天行为构建的矩阵即为「近 N 天热门」。
 */
@Component
public class HotFallbackStrategy implements RecommendStrategy {

    private final RecommendProperties properties;

    public HotFallbackStrategy(RecommendProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getName() {
        return "HotFallback";
    }

    @Override
    public List<ScoredItem> recommend(UserItemMatrix matrix, RecommendRequest request) {
        if (matrix == null || matrix.isEmpty()) {
            return Collections.emptyList();
        }

        int topN = request.getTopN() > 0 ? request.getTopN() : properties.getDefaultTopN();
        Set<Long> excluded = request.getExcludeItemIds() == null || request.getExcludeItemIds().isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(request.getExcludeItemIds());

        return matrix.allItemIds().stream()
                .filter(itemId -> !excluded.contains(itemId))
                .map(itemId -> new ScoredItem(itemId, matrix.getPopularity(itemId), "近期热门商品"))
                .filter(item -> item.getScore() > 0.0)
                .sorted()
                .limit(topN)
                .collect(Collectors.toList());
    }
}
