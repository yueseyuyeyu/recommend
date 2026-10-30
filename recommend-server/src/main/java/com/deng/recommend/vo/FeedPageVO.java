package com.deng.recommend.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页推荐流的一页，采用<b>游标分页</b>而非页码分页。
 *
 * <p><b>为什么不用 pageNum</b>：页码分页要求服务端能算出「第 N 页的起点」，
 * 而推荐结果是算法实时排序出来的，传统做法只能把 topN 开到 {@code pageNum × pageSize}
 * 再切片——页码越深，算法要做的工作越多，耗时线性增长。
 *
 * <p><b>游标怎么解决</b>：服务端首次请求把整个排行池（默认 300 条）算好并缓存，
 * 之后每次翻页只从缓存里切一段，返回 {@code nextCursor} 指向下一段起点。
 * 单次翻页耗时与已翻多少页无关。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedPageVO {

    /** 本页商品 */
    @Builder.Default
    private List<RecommendVO> records = new ArrayList<>();

    /** 下一页游标，为 null 表示已到末尾 */
    private String nextCursor;

    /** 是否还有下一页 */
    private boolean hasMore;

    /** 在售商品总数，仅用于前端展示进度，不参与分页计算 */
    private long total;
}
