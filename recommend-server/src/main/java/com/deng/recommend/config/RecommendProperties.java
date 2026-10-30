package com.deng.recommend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 推荐引擎可调参数。
 *
 * <p>全部参数外置到 {@code application.yml}，禁止在算法代码中硬编码魔法数字。
 */
@Data
@Component
@ConfigurationProperties(prefix = "recommend")
public class RecommendProperties {

    /** 默认推荐结果数量 */
    private int defaultTopN = 10;

    /** UserCF 邻居数量 K */
    private int neighborK = 20;

    /**
     * ItemCF 热门惩罚因子，取值 [0,1]。
     * 0 表示不惩罚（标准余弦相似度），调大可抑制热门物品与万物相似。
     */
    private double popularityPenalty = 0.0;

    /** 混合推荐中 UserCF 的权重 */
    private double userCfWeight = 0.6;

    /** 混合推荐中 ItemCF 的权重 */
    private double itemCfWeight = 0.4;

    /** 热门统计的时间窗口（天） */
    private int hotDays = 30;

    /** 是否启用推荐结果缓存 */
    private boolean enableCache = true;

    /** 推荐结果缓存有效期（秒），同时用于首页推荐流的排行池 */
    private long cacheTtlSeconds = 1800;

    /**
     * 首页推荐流排行池大小。
     *
     * <p>算法只在首次请求时算这么多条并缓存，后续翻页只做内存切片，
     * 因此单次翻页耗时与页码无关——这正是游标分页要解决的问题。
     */
    private int feedPoolSize = 300;

    /** 单次推荐计算允许加载的最大行为记录数，防止数据量增长后拖垮接口 */
    private int maxBehaviorRows = 200000;
}
