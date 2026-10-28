package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deng.recommend.config.RecommendProperties;
import com.deng.recommend.entity.Behavior;
import com.deng.recommend.entity.Product;
import com.deng.recommend.mapper.BehaviorMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.recommend.RecommendEngine;
import com.deng.recommend.recommend.evaluator.RecommendEvaluator;
import com.deng.recommend.recommend.model.BehaviorRecord;
import com.deng.recommend.recommend.model.ScoredItem;
import com.deng.recommend.recommend.model.UserItemMatrix;
import com.deng.recommend.service.BehaviorService;
import com.deng.recommend.service.RecommendService;
import com.deng.recommend.vo.ProductVO;
import com.deng.recommend.vo.RecommendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推荐服务实现：负责「算法输出 → 商品视图」的编排。
 *
 * <p><b>职责边界</b>：算法只产出 {@code (商品ID, 推荐分, 理由)}，
 * 商品详情查询、下架过滤、字段组装由本层完成，
 * 保证算法层保持纯函数、可单测。
 *
 * <p><b>对照参考项目</b>：newbee-mall 的 {@code RecommendServiceImpl}
 * 既查数据又跑算法又截字符串，职责混杂且无法测试；
 * 本项目将其拆分为「算法策略 + 引擎 + 编排服务」三层。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

    private final RecommendEngine recommendEngine;
    private final BehaviorService behaviorService;
    private final BehaviorMapper behaviorMapper;
    private final ProductMapper productMapper;
    private final RecommendProperties properties;

    @Override
    public List<RecommendVO> recommendForUser(Long userId, int topN) {
        int size = normalizeTopN(topN);
        UserItemMatrix matrix = behaviorService.buildMatrix(null);
        List<ScoredItem> scoredItems = recommendEngine.recommendForUser(
                matrix, userId, size, buildExcludeIds(userId));
        return toRecommendVOs(scoredItems);
    }

    @Override
    public List<RecommendVO> recommendSimilar(Long itemId, int topN) {
        int size = normalizeTopN(topN);
        UserItemMatrix matrix = behaviorService.buildMatrix(null);
        List<Long> exclude = new ArrayList<>(findOffShelfProductIds());
        exclude.add(itemId);
        List<ScoredItem> scoredItems = recommendEngine.recommendSimilar(matrix, itemId, size, exclude);
        return toRecommendVOs(scoredItems);
    }

    @Override
    public List<RecommendVO> recommendHot(int topN) {
        int size = normalizeTopN(topN);
        UserItemMatrix matrix = behaviorService.buildMatrix(hotWindowStart());
        List<ScoredItem> scoredItems = recommendEngine.recommendHot(
                matrix, size, findOffShelfProductIds());
        return toRecommendVOs(scoredItems);
    }

    @Override
    public List<RecommendVO> recommendHybrid(Long userId, Long itemId, int topN) {
        int size = normalizeTopN(topN);
        UserItemMatrix matrix = behaviorService.buildMatrix(null);
        List<ScoredItem> scoredItems = recommendEngine.recommendHybrid(
                matrix, userId, itemId, size, buildExcludeIds(userId));
        return toRecommendVOs(scoredItems);
    }

    @Override
    public RecommendEvaluator.EvaluationResult evaluate(int topN) {
        int size = normalizeTopN(topN);
        List<BehaviorRecord> allRecords = behaviorMapper.selectBehaviorRecords(
                null, properties.getMaxBehaviorRows());

        if (allRecords.size() < 10) {
            log.warn("行为数据量不足（{} 条），无法进行有意义的评测", allRecords.size());
            return new RecommendEvaluator.EvaluationResult();
        }

        // 按时间 8:2 划分训练集与测试集
        List<BehaviorRecord>[] split = RecommendEvaluator.splitByTime(allRecords, 0.8);
        List<BehaviorRecord> train = split[0];
        List<BehaviorRecord> test = split[1];
        UserItemMatrix trainMatrix = UserItemMatrix.build(train);

        RecommendEvaluator.EvaluationResult result = RecommendEvaluator.evaluate(
                train, test,
                (userId, n) -> recommendEngine.recommendForUser(trainMatrix, userId, n, null),
                size);

        log.info("推荐算法离线评测结果: {}", result);
        return result;
    }

    private int normalizeTopN(Integer topN) {
        return (topN == null || topN <= 0) ? properties.getDefaultTopN() : topN;
    }

    /**
     * 构造排除列表：已购买商品 + 已下架商品，避免重复推荐与推荐不可售商品。
     */
    private List<Long> buildExcludeIds(Long userId) {
        List<Long> exclude = new ArrayList<>(findOffShelfProductIds());
        if (userId != null) {
            List<Behavior> purchases = behaviorMapper.selectList(new LambdaQueryWrapper<Behavior>()
                    .eq(Behavior::getUserId, userId)
                    .eq(Behavior::getBehaviorType, "PURCHASE"));
            for (Behavior behavior : purchases) {
                exclude.add(behavior.getProductId());
            }
        }
        return exclude;
    }

    private List<Long> findOffShelfProductIds() {
        List<Product> offShelf = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 0)
                .select(Product::getId));
        List<Long> ids = new ArrayList<>(offShelf.size());
        for (Product product : offShelf) {
            ids.add(product.getId());
        }
        return ids;
    }

    /**
     * 将算法输出的商品 ID 批量转换为视图对象，并严格保持推荐分降序。
     */
    private List<RecommendVO> toRecommendVOs(List<ScoredItem> scoredItems) {
        if (scoredItems == null || scoredItems.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> ids = new ArrayList<>(scoredItems.size());
        for (ScoredItem item : scoredItems) {
            ids.add(item.getItemId());
        }

        List<Product> products = productMapper.selectBatchIds(ids);
        Map<Long, ProductVO> productMap = new LinkedHashMap<>();
        for (Product product : products) {
            productMap.put(product.getId(), ProductVO.from(product));
        }

        List<RecommendVO> result = new ArrayList<>(scoredItems.size());
        Map<Long, ScoredItem> scoreMap = new HashMap<>();
        for (ScoredItem item : scoredItems) {
            scoreMap.put(item.getItemId(), item);
        }
        for (ScoredItem item : scoredItems) {
            ProductVO product = productMap.get(item.getItemId());
            // 商品不存在或已下架则跳过
            if (product == null || !Integer.valueOf(1).equals(product.getStatus())) {
                continue;
            }
            result.add(RecommendVO.of(product, item));
        }
        return result;
    }

    private java.time.LocalDateTime hotWindowStart() {
        return java.time.LocalDateTime.now().minusDays(properties.getHotDays());
    }
}
