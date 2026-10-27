package com.deng.recommend.vo;

import com.deng.recommend.entity.Review;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商品评价视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewVO {

    private Long id;

    private Long userId;

    /** 评价人用户名，列表场景不联表查询，默认留空 */
    private String username;

    private Long productId;

    /** 评分 1–5 */
    private Integer rating;

    private String content;

    private LocalDateTime createTime;

    /** 由实体转换，避免把 Entity 直接暴露给前端 */
    public static ReviewVO from(Review review) {
        return ReviewVO.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .productId(review.getProductId())
                .rating(review.getRating())
                .content(review.getContent())
                .createTime(review.getCreateTime())
                .build();
    }
}
