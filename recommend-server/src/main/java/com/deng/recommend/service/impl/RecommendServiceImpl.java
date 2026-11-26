package com.deng.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deng.recommend.common.BusinessException;
import com.deng.recommend.common.ResultCode;
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
import com.deng.recommend.service.CategoryService;
import com.deng.recommend.service.RecommendService;
import com.deng.recommend.vo.FeedPageVO;
import com.deng.recommend.vo.ProductVO;
import com.deng.recommend.vo.RecommendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private final CategoryService categoryService;
    private final RecommendProperties properties;

    /** 游客排行池的缓存键 */
    private static final String GUEST_CACHE_KEY = "guest";

    /** 缓存条目上限，超过则整体清空，防止内存无界增长 */
    private static final int MAX_CACHE_ENTRIES = 5000;

    /** 排行池缓存：key = userId 或 guest，value = 有序候选列表 + 过期时间 */
    private final Map<String, CachedPool> poolCache = new ConcurrentHashMap<>();

    /** 缓存条目，用 record 保证不可变 */
    private record CachedPool(List<ScoredItem> items, long total, long expireAt) {
    }

    /** 下架商品清单缓存：下架是低频管理操作，不必每次推荐请求都查一遍 */
    private volatile List<Long> offShelfCache;

    private volatile long offShelfCacheAt;

    /** 下架清单缓存时长：与类目名缓存同策略，变更后最多 60 秒生效 */
    private static final long OFF_SHELF_CACHE_MS = 60_000L;

    @Override
    public List<RecommendVO> recommendForUser(Long userId, int topN) {
        int size = normalizeTopN(topN);
        UserItemMatrix matrix = behaviorService.buildMatrix(null);

        // 冷启动：几乎没有行为的用户跑协同过滤，找出来的邻居不可信。
        // 此时跳过算法，直接用「全站热门 + 类目打散」，理由文案也换成冷启动专属的。
        if (isColdUser(matrix, userId)) {
            int candidateSize = Math.min(size * 3, 60);
            List<Long> exclude = userId == null
                    ? new ArrayList<>()
                    : buildExcludeIds(userId);
            List<ScoredItem> hot = recommendEngine.recommendHot(matrix, candidateSize, exclude);
            List<RecommendVO> result = toRecommendVOs(hot, size, true);
            for (RecommendVO item : result) {
                item.setReason("全站热门好物，逛得越多推荐越准");
            }
            return result;
        }

        int candidateSize = Math.min(size * 3, 60);
        List<ScoredItem> scoredItems = recommendEngine.recommendForUser(
                matrix, userId, candidateSize, buildExcludeIds(userId));
        return toRecommendVOs(scoredItems, size, true);
    }

    /**
     * 判断是否为冷启动用户。
     *
     * <p>游客与行为数不足阈值的新注册用户都算；没有行为记录的也算，
     * 统一走热门路径，避免为 0 条行为单独写一条分支。
     */
    private boolean isColdUser(UserItemMatrix matrix, Long userId) {
        if (userId == null) {
            return true;
        }
        return matrix.getUserItems(userId).size() < properties.getColdStartMinInteractions();
    }

    @Override
    public List<RecommendVO> recommendSimilar(Long itemId, int topN) {
        int size = normalizeTopN(topN);
        UserItemMatrix matrix = behaviorService.buildMatrix(null);
        List<Long> exclude = new ArrayList<>(findOffShelfProductIds());
        exclude.add(itemId);
        List<ScoredItem> scoredItems = new ArrayList<>(
                recommendEngine.recommendSimilarRaw(matrix, itemId, size, exclude));
        // 新商品没有交互记录时 ItemCF 无从下手，用同品类热门商品补位，
        // 比全站热门更贴合「相似」这个语义
        scoredItems = supplementWithSameCategory(scoredItems, itemId, size);
        // 品类补位仍不足时，最后才用全站热门兜底，保证页面没有空位
        if (scoredItems.size() < size) {
            java.util.Set<Long> picked = new java.util.HashSet<>();
            for (ScoredItem item : scoredItems) {
                picked.add(item.getItemId());
            }
            for (ScoredItem hot : recommendEngine.recommendHot(matrix, size * 2, exclude)) {
                if (scoredItems.size() >= size) {
                    break;
                }
                if (!picked.contains(hot.getItemId())) {
                    picked.add(hot.getItemId());
                    scoredItems.add(hot);
                }
            }
        }
        // 注意：相似推荐不能做类目打散——同一个类目恰恰是"相似"的来源
        return toRecommendVOs(scoredItems, size, false);
    }

    /**
     * 相似推荐结果不足时，用同品类热门商品补齐。
     *
     * @param items  ItemCF 的结果（可能为空）
     * @param itemId 源商品 ID
     * @param limit  目标条数
     */
    private List<ScoredItem> supplementWithSameCategory(List<ScoredItem> items, Long itemId, int limit) {
        if (items.size() >= limit) {
            return items;
        }

        Product source = productMapper.selectById(itemId);
        if (source == null) {
            return items;
        }

        List<ScoredItem> result = new ArrayList<>(items);
        java.util.Set<Long> alreadyPicked = new java.util.HashSet<>();
        for (ScoredItem item : items) {
            alreadyPicked.add(item.getItemId());
        }
        alreadyPicked.add(itemId);

        List<Product> sameCategory = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getCategoryId, source.getCategoryId())
                        .eq(Product::getStatus, 1)
                        .notIn(Product::getId, alreadyPicked)
                        .orderByDesc(Product::getSales)
                        .last("LIMIT " + (limit - items.size())));

        for (Product product : sameCategory) {
            result.add(new ScoredItem(product.getId(), 0.0, "同品类热门商品"));
        }
        return result;
    }

    @Override
    public List<RecommendVO> recommendHot(int topN) {
        int size = normalizeTopN(topN);
        UserItemMatrix matrix = behaviorService.buildMatrix(hotWindowStart());
        int candidateSize = Math.min(size * 3, 60);
        List<ScoredItem> scoredItems = recommendEngine.recommendHot(
                matrix, candidateSize, findOffShelfProductIds());
        return toRecommendVOs(scoredItems, size, true);
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
    public FeedPageVO recommendFeed(Long userId, String cursor, int pageSize) {
        int size = Math.min(Math.max(1, pageSize), 50);
        int offset = decodeCursor(cursor);

        CachedPool ranked = getRankedPool(userId);
        List<ScoredItem> pool = ranked.items();
        long total = ranked.total();

        if (offset >= pool.size()) {
            return FeedPageVO.builder()
                    .records(new ArrayList<>())
                    .nextCursor(null)
                    .hasMore(false)
                    .total(total)
                    .build();
        }

        int to = Math.min(pool.size(), offset + size);
        List<ScoredItem> slice = new ArrayList<>(pool.subList(offset, to));
        boolean hasMore = to < pool.size();

        return FeedPageVO.builder()
                .records(toRecommendVOs(slice))
                .nextCursor(hasMore ? encodeCursor(to) : null)
                .hasMore(hasMore)
                .total(total)
                .build();
    }

    /**
     * 取当前用户的排行池（连同在售总数），命中缓存时直接返回。
     *
     * <p>这是游标分页能做到「翻到多深都只花一次切片时间」的关键：
     * 昂贵的算法只跑一次，后续翻页退化为内存 subList。
     * 在售总数跟池一起缓存——它随池过期一起刷新，
     * 翻页请求不再每次都跑一条 COUNT。
     */
    private CachedPool getRankedPool(Long userId) {
        String key = userId == null ? GUEST_CACHE_KEY : String.valueOf(userId);
        long now = System.currentTimeMillis();

        CachedPool cached = poolCache.get(key);
        if (properties.isEnableCache() && cached != null && cached.expireAt() > now) {
            return cached;
        }

        List<ScoredItem> pool = buildRankedPool(userId);
        long total = countOnSaleProducts();

        if (properties.isEnableCache()) {
            // 简单防膨胀：条目过多时整体清空，比逐个清理更省事
            if (poolCache.size() > MAX_CACHE_ENTRIES) {
                poolCache.clear();
            }
            cached = new CachedPool(pool, total, now + properties.getCacheTtlSeconds() * 1000L);
            poolCache.put(key, cached);
        } else {
            cached = new CachedPool(pool, total, 0);
        }
        return cached;
    }

    /**
     * 计算完整排行池：算法结果在前，销量补位在后。
     */
    private List<ScoredItem> buildRankedPool(Long userId) {
        int poolSize = Math.max(properties.getFeedPoolSize(), properties.getDefaultTopN());

        UserItemMatrix matrix = behaviorService.buildMatrix(null);
        List<Long> exclude = buildExcludeIds(userId);
        List<ScoredItem> pool = new ArrayList<>(
                recommendEngine.recommendForUser(matrix, userId, poolSize, exclude));

        if (pool.size() < poolSize) {
            Set<Long> alreadyHave = new HashSet<>(exclude);
            for (ScoredItem item : pool) {
                alreadyHave.add(item.getItemId());
            }
            appendSalesFallback(pool, alreadyHave, poolSize - pool.size());
        }
        return pool;
    }

    /**
     * 游标编码为 URL 安全的 Base64，避免前端看到裸的数字偏移量后自行拼接。
     */
    private String encodeCursor(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.valueOf(offset).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解析游标。空游标表示第一页。
     *
     * <p>非法游标<b>直接报参数错误</b>，不再静默回退到第一页：
     * 静默回退会让用户"翻页翻不动、内容反复是第一屏"却看不出原因，
     * 前端也无法判断该重新加载还是提示错误。
     */
    private int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return Math.max(0, Integer.parseInt(raw));
        } catch (IllegalArgumentException e) {
            log.warn("推荐流游标非法: {}", cursor);
            throw new BusinessException(ResultCode.PARAM_ERROR, "分页游标无效，请重新加载推荐流");
        }
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

        List<Long> offShelf = findOffShelfProductIds();

        // 关键口径修正：排除「该用户在训练集中已交互过的商品」。
        // 不做这一步的话，热门兜底会把用户自己看过的商品推回来，
        // 而测试集里同一个商品很可能再次出现，于是被算成命中——
        // 指标虚高的同时，推荐的其实是「已经知道的答案」。
        RecommendEvaluator.EvaluationResult result = RecommendEvaluator.evaluateWithBaselines(
                train, test,
                (userId, n) -> {
                    List<Long> exclude = new ArrayList<>(trainMatrix.getUserItems(userId).keySet());
                    exclude.addAll(offShelf);
                    return recommendEngine.recommendForUser(trainMatrix, userId, n, exclude);
                },
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
        long now = System.currentTimeMillis();
        List<Long> cached = offShelfCache;
        if (cached != null && now - offShelfCacheAt < OFF_SHELF_CACHE_MS) {
            return cached;
        }
        List<Product> offShelf = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 0)
                .select(Product::getId));
        List<Long> ids = new ArrayList<>(offShelf.size());
        for (Product product : offShelf) {
            ids.add(product.getId());
        }
        offShelfCache = ids;
        offShelfCacheAt = now;
        return ids;
    }

    /**
     * 将算法输出的商品 ID 批量转换为视图对象，并严格保持推荐分降序。
     */
    private List<RecommendVO> toRecommendVOs(List<ScoredItem> scoredItems) {
        return toRecommendVOs(scoredItems, Integer.MAX_VALUE, false);
    }

    /**
     * 把打分结果转成视图。
     *
     * @param scoredItems 打分结果，已按分数降序
     * @param limit       最终返回条数上限
     * @param diversify   是否按一级类目做多样性打散
     */
    private List<RecommendVO> toRecommendVOs(List<ScoredItem> scoredItems, int limit, boolean diversify) {
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

        // 先把下架/缺失的过滤掉再打散，避免已下架商品占掉类目配额
        List<ScoredItem> ordered = new ArrayList<>(scoredItems.size());
        for (ScoredItem item : scoredItems) {
            ProductVO product = productMap.get(item.getItemId());
            if (product != null && Integer.valueOf(1).equals(product.getStatus())) {
                ordered.add(item);
            }
        }

        if (diversify) {
            ordered = diversify(ordered, productMap, limit);
        }

        List<RecommendVO> result = new ArrayList<>(ordered.size());
        for (ScoredItem item : ordered) {
            result.add(RecommendVO.of(productMap.get(item.getItemId()), item));
        }
        return result;
    }

    /**
     * 多样性打散：同一一级类目尽量不超过 {@code diversify-max-per-category} 条。
     *
     * <p>逐个挑选「分数最高且未超配额」的候选；若剩余候选全部超配额，
     * 说明类目覆盖面不足以撑满 limit，此时把配额放宽 1 再挑——
     * 保证最终条数不缩水，配额只是"尽量满足"。
     *
     * <p>第一版的缺陷：把超配额的候选收集起来最后补齐，当候选集中在
     * 少数类目时，补齐环节会把配额击穿（实测母婴玩具回到 9/20）。
     */
    private List<ScoredItem> diversify(List<ScoredItem> items, Map<Long, ProductVO> productMap, int limit) {
        int baseQuota = properties.getDiversifyMaxPerCategory();
        if (baseQuota <= 0 || items.size() <= limit) {
            return items;
        }

        Map<Long, Long> topIndex = categoryService.categoryTopIndex();

        // (候选, 所属一级类目)，类目缺失的归入 -1 桶
        List<Map.Entry<ScoredItem, Long>> remaining = new ArrayList<>(items.size());
        for (ScoredItem item : items) {
            ProductVO product = productMap.get(item.getItemId());
            Long topId = product == null ? null : topIndex.get(product.getCategoryId());
            remaining.add(new java.util.AbstractMap.SimpleEntry<>(
                    item, topId == null ? Long.valueOf(-1) : topId));
        }

        List<ScoredItem> picked = new ArrayList<>(limit);
        Map<Long, Integer> counts = new HashMap<>();
        int quota = baseQuota;

        while (picked.size() < limit && !remaining.isEmpty()) {
            int chosenIndex = -1;
            for (int i = 0; i < remaining.size(); i++) {
                Long bucket = remaining.get(i).getValue();
                if (counts.getOrDefault(bucket, 0) < quota) {
                    chosenIndex = i;
                    break;
                }
            }
            if (chosenIndex < 0) {
                // 剩余候选全部超配额，放宽 1 再挑
                quota++;
                continue;
            }
            Map.Entry<ScoredItem, Long> chosen = remaining.remove(chosenIndex);
            picked.add(chosen.getKey());
            counts.merge(chosen.getValue(), 1, Integer::sum);
        }
        return picked;
    }

    private java.time.LocalDateTime hotWindowStart() {
        return java.time.LocalDateTime.now().minusDays(properties.getHotDays());
    }

    /**
     * 在售商品总数，作为推荐流分页的 total。
     */
    private long countOnSaleProducts() {
        Long count = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1));
        return count == null ? 0L : count;
    }

    /**
     * 用销量排序的商品补足候选池。
     *
     * <p>推荐分统一给 0，保证它们排在算法产出的真推荐之后——
     * 补位商品只是「还有内容可看」，不应该抢占真正的个性化结果。
     */
    private void appendSalesFallback(List<ScoredItem> target, Set<Long> excludeIds, int limit) {
        if (limit <= 0) {
            return;
        }
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .orderByDesc(Product::getSales)
                .orderByAsc(Product::getId);
        if (!excludeIds.isEmpty()) {
            wrapper.notIn(Product::getId, excludeIds);
        }
        // 用分页对象限制条数，避免字符串拼接 LIMIT
        Page<Product> page = productMapper.selectPage(new Page<>(1, limit), wrapper);
        for (Product product : page.getRecords()) {
            target.add(new ScoredItem(product.getId(), 0.0, "热销商品"));
        }
    }
}
