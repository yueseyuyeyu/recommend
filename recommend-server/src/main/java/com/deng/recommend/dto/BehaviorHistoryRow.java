package com.deng.recommend.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行为聚合行，供个人中心「浏览过 / 买过」列表查询使用。
 *
 * <p>按 productId 分组聚合，避免同一商品因多次浏览在列表里刷屏。
 */
@Data
public class BehaviorHistoryRow {

    private Long productId;

    /** 该商品最后一次行为时间 */
    private LocalDateTime lastTime;

    /** 累计行为次数 */
    private Integer times;
}
