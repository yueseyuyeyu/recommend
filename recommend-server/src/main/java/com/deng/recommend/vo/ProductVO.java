package com.deng.recommend.vo;

import com.deng.recommend.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVO {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String name;

    private String subtitle;

    private String mainImage;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private Integer stock;

    private Integer sales;

    private Integer status;

    private String description;

    /** 由实体转换，避免把 Entity 直接暴露给前端 */
    public static ProductVO from(Product product) {
        return ProductVO.builder()
                .id(product.getId())
                .categoryId(product.getCategoryId())
                .name(product.getName())
                .subtitle(product.getSubtitle())
                .mainImage(product.getMainImage())
                .price(product.getPrice())
                .originalPrice(product.getOriginalPrice())
                .stock(product.getStock())
                .sales(product.getSales())
                .status(product.getStatus())
                .description(product.getDescription())
                .build();
    }
}
