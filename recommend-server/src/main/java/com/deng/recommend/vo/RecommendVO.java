package com.deng.recommend.vo;

import com.deng.recommend.recommend.model.ScoredItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 推荐商品视图对象。
 *
 * <p>除商品基础信息外，额外携带 {@code score} 与 {@code reason}，
 * 让推荐结果具备<b>可解释性</b>——这是参考项目完全缺失的能力。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendVO {

    private Long id;

    private String name;

    private String subtitle;

    private String mainImage;

    private BigDecimal price;

    private Integer sales;

    /** 推荐分，用于前端排序展示 */
    private double score;

    /** 推荐理由，如「与您兴趣相似的 12 位用户也在关注」 */
    private String reason;

    /** 由商品视图与算法输出组装 */
    public static RecommendVO of(ProductVO product, ScoredItem scoredItem) {
        return RecommendVO.builder()
                .id(product.getId())
                .name(product.getName())
                .subtitle(product.getSubtitle())
                .mainImage(product.getMainImage())
                .price(product.getPrice())
                .sales(product.getSales())
                .score(scoredItem.getScore())
                .reason(scoredItem.getReason())
                .build();
    }
}
