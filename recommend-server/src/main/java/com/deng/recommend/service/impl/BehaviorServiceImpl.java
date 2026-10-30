package com.deng.recommend.service.impl;

import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.ResultCode;
import com.deng.recommend.config.RecommendProperties;
import com.deng.recommend.dto.BehaviorReportRequest;
import com.deng.recommend.entity.Behavior;
import com.deng.recommend.entity.Product;
import com.deng.recommend.entity.enums.BehaviorType;
import com.deng.recommend.mapper.BehaviorMapper;
import com.deng.recommend.mapper.ProductMapper;
import com.deng.recommend.recommend.model.BehaviorRecord;
import com.deng.recommend.recommend.model.UserItemMatrix;
import com.deng.recommend.service.BehaviorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户行为服务实现。
 *
 * <p>参考项目 newbee-mall 只从订单表反推"用户-商品"关系，
 * 且用 {@code Math.random()} 伪造行为强度，导致推荐结果不可复现。
 * 本项目建立真实的行为采集体系，按行为类型赋予确定权重。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorServiceImpl implements BehaviorService {

    private static final String CACHE_KEY_FULL = "full";

    private static final String CACHE_KEY_HOT = "hot";

    /** 矩阵缓存，key 见上面的常量 */
    private final java.util.Map<String, CachedMatrix> matrixCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 缓存条目：矩阵 + 过期时间戳 */
    private record CachedMatrix(UserItemMatrix matrix, long expireAt) {
    }

    private final BehaviorMapper behaviorMapper;
    private final ProductMapper productMapper;
    private final RecommendProperties recommendProperties;

    @Override
    public void report(Long userId, BehaviorReportRequest request) {
        BehaviorType behaviorType;
        try {
            behaviorType = BehaviorType.of(request.getBehaviorType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "未知的行为类型: " + request.getBehaviorType());
        }

        // 校验商品存在，避免写入脏数据污染推荐矩阵
        Product product = productMapper.selectById(request.getProductId());
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        // 评分行为必须携带 1-5 的评分值
        Integer rating = null;
        if (behaviorType == BehaviorType.RATE) {
            if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "评分行为必须携带 1-5 的评分值");
            }
            rating = request.getRating();
        }

        Behavior behavior = new Behavior();
        behavior.setUserId(userId);
        behavior.setProductId(request.getProductId());
        behavior.applyType(behaviorType);
        behavior.setRating(rating);
        behavior.setCreatedAt(LocalDateTime.now());
        behaviorMapper.insert(behavior);
    }

    @Override
    public UserItemMatrix buildMatrix(LocalDateTime since) {
        // 全量矩阵与热门窗口矩阵分别缓存：热门窗口随时间滑动，
        // 但 TTL 只有几分钟，边界上多算/漏算几条不影响结果
        String key = since == null ? CACHE_KEY_FULL : CACHE_KEY_HOT;
        long now = System.currentTimeMillis();

        CachedMatrix cached = matrixCache.get(key);
        if (cached != null && cached.expireAt() > now) {
            return cached.matrix();
        }

        long start = System.currentTimeMillis();
        int limit = recommendProperties.getMaxBehaviorRows();
        List<BehaviorRecord> records = behaviorMapper.selectBehaviorRecords(since, limit);
        UserItemMatrix matrix = UserItemMatrix.build(records);
        long cost = System.currentTimeMillis() - start;

        // 只在明显变慢时打日志，避免正常情况刷屏
        if (cost > 300) {
            log.info("行为矩阵重建耗时 {} ms（{} 条记录，{} 用户，{} 物品）",
                    cost, records.size(), matrix.userCount(), matrix.itemCount());
        }

        long ttlSeconds = recommendProperties.getMatrixCacheTtlSeconds();
        if (ttlSeconds > 0) {
            matrixCache.put(key, new CachedMatrix(matrix, now + ttlSeconds * 1000L));
        }
        return matrix;
    }

    @Override
    public void evictMatrixCache() {
        matrixCache.clear();
        log.info("行为矩阵缓存已手动清除");
    }
}
