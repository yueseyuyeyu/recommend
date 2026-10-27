package com.deng.recommend.recommend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 带推荐分的商品，推荐算法的输出单元。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoredItem implements Comparable<ScoredItem> {

    /** 商品 ID */
    private Long itemId;

    /** 推荐分（UserCF 为加权得分，ItemCF 为相似度，热门为热度分） */
    private double score;

    /** 推荐理由，用于前端展示可解释性 */
    private String reason;

    public ScoredItem(Long itemId, double score) {
        this.itemId = itemId;
        this.score = score;
    }

    /** 按推荐分降序排列，分数相同时按商品 ID 升序保证结果稳定 */
    @Override
    public int compareTo(ScoredItem other) {
        int cmp = Double.compare(other.score, this.score);
        return cmp != 0 ? cmp : Long.compare(this.itemId, other.itemId);
    }
}
