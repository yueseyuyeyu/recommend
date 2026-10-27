package com.deng.recommend.recommend.similarity;

import java.util.Map;
import java.util.Set;

/**
 * 相似度计算工具。
 *
 * <p>采用余弦相似度衡量两个评分向量的夹角：
 *
 * <pre>
 *              Σ(i∈A∩B) wA(i) · wB(i)
 * sim(A,B) = ─────────────────────────────
 *             √Σ(i∈A)wA(i)² · √Σ(i∈B)wB(i)²
 * </pre>
 *
 * <p>取值区间 [0,1]，0 表示完全无关，1 表示完全一致。
 */
public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    /**
     * 计算两个稀疏向量的余弦相似度。
     *
     * @param a 向量 A（key → 权重）
     * @param b 向量 B（key → 权重）
     * @return 相似度，取值 [0,1]；任一向量为空或两个向量无交集时返回 0
     */
    public static double compute(Map<Long, Double> a, Map<Long, Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }

        // 遍历较短的向量以减少比较次数
        Map<Long, Double> small = a.size() <= b.size() ? a : b;
        Map<Long, Double> large = a.size() <= b.size() ? b : a;

        double dotProduct = 0.0;
        Set<Long> keys = small.keySet();
        for (Long key : keys) {
            Double valueInLarge = large.get(key);
            if (valueInLarge != null) {
                dotProduct += small.get(key) * valueInLarge;
            }
        }

        if (dotProduct == 0.0) {
            return 0.0;
        }

        double normA = norm(a);
        double normB = norm(b);
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        double similarity = dotProduct / (normA * normB);
        // 浮点误差可能导致结果略微越界，做一次收敛
        return Math.max(0.0, Math.min(1.0, similarity));
    }

    /**
     * 计算向量的 L2 范数。
     *
     * @param vector 稀疏向量
     * @return 范数值
     */
    public static double norm(Map<Long, Double> vector) {
        if (vector == null || vector.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Double value : vector.values()) {
            if (value != null) {
                sum += value * value;
            }
        }
        return Math.sqrt(sum);
    }

    /**
     * 计算带热门惩罚的余弦相似度。
     *
     * <p>热门物品与大量用户产生共现，容易与"万物相似"，
     * 通过惩罚因子抑制其权重：{@code sim / |B|^alpha}。
     * alpha = 0 时退化为标准余弦相似度。
     *
     * @param a        向量 A
     * @param b        向量 B
     * @param popularityPenalty 热门惩罚因子，取值 [0,1]
     * @return 惩罚后的相似度
     */
    public static double computeWithPenalty(Map<Long, Double> a, Map<Long, Double> b, double popularityPenalty) {
        double similarity = compute(a, b);
        if (similarity == 0.0 || popularityPenalty <= 0.0) {
            return similarity;
        }
        int size = b.size();
        if (size <= 1) {
            return similarity;
        }
        return similarity / Math.pow(size, popularityPenalty);
    }
}
