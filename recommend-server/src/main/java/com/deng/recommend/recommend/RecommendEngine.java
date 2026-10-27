package com.deng.recommend.recommend;

import com.deng.recommend.config.RecommendProperties;
import com.deng.recommend.recommend.model.RecommendRequest;
import com.deng.recommend.recommend.model.ScoredItem;
import com.deng.recommend.recommend.model.UserItemMatrix;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 推荐引擎门面：统一入口，负责策略路由、结果补全与异常降级。
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li>上层（Service）只与本类交互，不直接依赖具体策略，符合依赖倒置原则。</li>
 *   <li>协同过滤结果不足 N 个时，自动用热门商品补足，保证前端始终拿到满额结果。</li>
 *   <li>任何算法异常都被捕获并降级为热门推荐——推荐是增强功能，
 *       绝不允许把页面搞挂（参考项目 <code>e.printStackTrace()</code> 直接吞异常的做法不可取）。</li>
 * </ul>
 */
@Slf4j
@Component
public class RecommendEngine {

    private final UserCFStrategy userCFStrategy;
    private final ItemCFStrategy itemCFStrategy;
    private final HotFallbackStrategy hotFallbackStrategy;
    private final HybridStrategy hybridStrategy;
    private final RecommendProperties properties;

    public RecommendEngine(UserCFStrategy userCFStrategy,
                           ItemCFStrategy itemCFStrategy,
                           HotFallbackStrategy hotFallbackStrategy,
                           HybridStrategy hybridStrategy,
                           RecommendProperties properties) {
        this.userCFStrategy = userCFStrategy;
        this.itemCFStrategy = itemCFStrategy;
        this.hotFallbackStrategy = hotFallbackStrategy;
        this.hybridStrategy = hybridStrategy;
        this.properties = properties;
    }

    /**
     * 首页个性化推荐：优先 UserCF，不足或异常时用热门补足。
     */
    public List<ScoredItem> recommendForUser(UserItemMatrix matrix, Long userId, int topN, List<Long> excludeItemIds) {
        RecommendRequest request = RecommendRequest.builder()
                .userId(userId)
                .topN(topN)
                .excludeItemIds(excludeItemIds)
                .build();
        return executeWithFallback(matrix, request, "UserCF 首页推荐",
                () -> userCFStrategy.recommend(matrix, request));
    }

    /**
     * 商品详情页相似推荐：优先 ItemCF，不足或异常时用热门补足。
     */
    public List<ScoredItem> recommendSimilar(UserItemMatrix matrix, Long itemId, int topN, List<Long> excludeItemIds) {
        RecommendRequest request = RecommendRequest.builder()
                .itemId(itemId)
                .topN(topN)
                .excludeItemIds(excludeItemIds)
                .build();
        return executeWithFallback(matrix, request, "ItemCF 相似推荐",
                () -> itemCFStrategy.recommend(matrix, request));
    }

    /**
     * 冷启动/游客场景：直接返回热门商品。
     */
    public List<ScoredItem> recommendHot(UserItemMatrix matrix, int topN, List<Long> excludeItemIds) {
        RecommendRequest request = RecommendRequest.builder()
                .topN(topN)
                .excludeItemIds(excludeItemIds)
                .build();
        return executeWithFallback(matrix, request, "热门推荐",
                () -> hotFallbackStrategy.recommend(matrix, request), false);
    }

    /**
     * 猜你喜欢：UserCF 与 ItemCF 混合推荐。
     */
    public List<ScoredItem> recommendHybrid(UserItemMatrix matrix, Long userId, Long itemId,
                                            int topN, List<Long> excludeItemIds) {
        RecommendRequest request = RecommendRequest.builder()
                .userId(userId)
                .itemId(itemId)
                .topN(topN)
                .excludeItemIds(excludeItemIds)
                .build();
        return executeWithFallback(matrix, request, "混合推荐",
                () -> hybridStrategy.recommend(matrix, request));
    }

    /**
     * 执行推荐并在必要时降级、补全。
     *
     * @param allowSupplement 是否允许用热门商品补足（热门策略自身不需要）
     */
    private List<ScoredItem> executeWithFallback(UserItemMatrix matrix,
                                                 RecommendRequest request,
                                                 String scenario,
                                                 Supplier<List<ScoredItem>> supplier) {
        return executeWithFallback(matrix, request, scenario, supplier, true);
    }

    private List<ScoredItem> executeWithFallback(UserItemMatrix matrix,
                                                 RecommendRequest request,
                                                 String scenario,
                                                 Supplier<List<ScoredItem>> supplier,
                                                 boolean allowSupplement) {
        List<ScoredItem> result;
        try {
            result = supplier.get();
        } catch (Exception e) {
            // 算法异常不得影响页面可用性：记录日志后降级为热门推荐
            log.error("[{}] 推荐计算异常，降级为热门推荐", scenario, e);
            result = Collections.emptyList();
        }

        if (result == null) {
            result = Collections.emptyList();
        }

        int topN = request.getTopN() > 0 ? request.getTopN() : properties.getDefaultTopN();
        if (!allowSupplement || result.size() >= topN) {
            return result;
        }

        // 结果不足时用热门商品补足，避免前端出现空推荐位
        List<Long> alreadyRecommended = new ArrayList<>();
        for (ScoredItem item : result) {
            alreadyRecommended.add(item.getItemId());
        }
        if (request.getExcludeItemIds() != null) {
            alreadyRecommended.addAll(request.getExcludeItemIds());
        }

        RecommendRequest fallbackRequest = RecommendRequest.builder()
                .topN(topN - result.size())
                .excludeItemIds(alreadyRecommended)
                .build();

        List<ScoredItem> supplement;
        try {
            supplement = hotFallbackStrategy.recommend(matrix, fallbackRequest);
        } catch (Exception e) {
            log.error("[{}] 热门兜底计算异常", scenario, e);
            supplement = Collections.emptyList();
        }

        if (supplement.isEmpty()) {
            return result;
        }

        Set<ScoredItem> merged = new LinkedHashSet<>(result);
        merged.addAll(supplement);
        return new ArrayList<>(merged);
    }
}
