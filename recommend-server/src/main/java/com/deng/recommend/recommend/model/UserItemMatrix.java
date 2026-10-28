package com.deng.recommend.recommend.model;

import lombok.Getter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户-物品行为矩阵，协同过滤算法的统一数据结构。
 *
 * <p>同时维护正向索引（用户 → 物品 → 加权分）与倒排索引（物品 → 用户 → 加权分），
 * 分别服务 UserCF 与 ItemCF，避免两种算法各自重复扫描全量数据。
 *
 * <p>同一用户对同一物品的多次行为，权重采用<b>累加</b>策略：
 * 多次浏览、先浏览后购买，都意味着更强的兴趣强度。
 */
@Getter
public class UserItemMatrix {

    /** 正向索引：userId → (productId → 加权分) */
    private final Map<Long, Map<Long, Double>> userItems = new HashMap<>();

    /** 倒排索引：productId → (userId → 加权分) */
    private final Map<Long, Map<Long, Double>> itemUsers = new HashMap<>();

    /** 物品热度：productId → 全站加权分之和，用于热门兜底与流行度惩罚 */
    private final Map<Long, Double> itemPopularity = new HashMap<>();

    /**
     * 由行为记录构建矩阵。
     *
     * @param records 行为记录列表，允许为 null
     * @return 构建好的矩阵，空输入返回空矩阵而非 null
     */
    public static UserItemMatrix build(List<BehaviorRecord> records) {
        UserItemMatrix matrix = new UserItemMatrix();
        if (records == null || records.isEmpty()) {
            return matrix;
        }
        for (BehaviorRecord record : records) {
            if (record.getUserId() == null || record.getProductId() == null) {
                continue;
            }
            double weight = record.getWeight() == null ? 0.0 : record.getWeight();
            if (weight <= 0.0) {
                continue;
            }
            matrix.userItems
                    .computeIfAbsent(record.getUserId(), k -> new HashMap<>())
                    .merge(record.getProductId(), weight, Double::sum);
            matrix.itemUsers
                    .computeIfAbsent(record.getProductId(), k -> new HashMap<>())
                    .merge(record.getUserId(), weight, Double::sum);
            matrix.itemPopularity.merge(record.getProductId(), weight, Double::sum);
        }
        return matrix;
    }

    /** 获取某用户的全部物品及加权分，无数据返回空 Map */
    public Map<Long, Double> getUserItems(Long userId) {
        return userItems.getOrDefault(userId, Collections.emptyMap());
    }

    /** 获取与某物品产生过交互的全部用户及加权分，无数据返回空 Map */
    public Map<Long, Double> getItemUsers(Long productId) {
        return itemUsers.getOrDefault(productId, Collections.emptyMap());
    }

    /** 获取物品热度分，无数据返回 0 */
    public double getPopularity(Long productId) {
        return itemPopularity.getOrDefault(productId, 0.0);
    }

    /** 判断用户是否与该物品产生过交互 */
    public boolean hasInteraction(Long userId, Long productId) {
        Map<Long, Double> items = userItems.get(userId);
        return items != null && items.containsKey(productId);
    }

    /** 全部用户 ID */
    public Set<Long> allUserIds() {
        return userItems.keySet();
    }

    /** 全部物品 ID */
    public Set<Long> allItemIds() {
        return itemUsers.keySet();
    }

    public Collection<Map<Long, Double>> allUserVectors() {
        return userItems.values();
    }

    /** 用户数量 */
    public int userCount() {
        return userItems.size();
    }

    /** 物品数量 */
    public int itemCount() {
        return itemUsers.size();
    }

    /** 是否为空矩阵 */
    public boolean isEmpty() {
        return userItems.isEmpty() || itemUsers.isEmpty();
    }
}
