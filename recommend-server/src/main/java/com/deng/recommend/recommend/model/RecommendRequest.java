package com.deng.recommend.recommend.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 推荐请求上下文。
 */
@Data
@Builder
public class RecommendRequest {

    /** 目标用户 ID，首页个性化推荐必填 */
    private Long userId;

    /** 基准商品 ID，ItemCF 相似推荐必填 */
    private Long itemId;

    /** 需要返回的结果数量，默认 10 */
    @Builder.Default
    private int topN = 10;

    /** UserCF 邻居数量 K，默认 20 */
    @Builder.Default
    private int neighborK = 20;

    /** 需要排除的商品 ID（如已购、已下架），避免重复推荐 */
    @Builder.Default
    private List<Long> excludeItemIds = new ArrayList<>();
}
