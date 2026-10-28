package com.deng.recommend.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品查询条件。
 */
@Data
public class ProductQuery {

    /** 关键字，匹配商品名称 */
    private String keyword;

    /** 类目 ID */
    private Long categoryId;

    /** 价格下限 */
    private BigDecimal minPrice;

    /** 价格上限 */
    private BigDecimal maxPrice;

    /** 排序：default-默认，sales-销量，price_asc-价格升序，price_desc-价格降序 */
    private String sort;
}
