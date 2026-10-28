package com.deng.recommend.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 购物车条目视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartVO {

    private Long id;

    private Long productId;

    private String productName;

    private String mainImage;

    private BigDecimal price;

    private Integer quantity;

    /** 是否勾选结算 */
    private Integer checked;

    /** 小计 = price × quantity */
    private BigDecimal subtotal;

    /** 当前可售库存，用于前端提示库存不足 */
    private Integer stock;
}
