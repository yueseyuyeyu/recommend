package com.deng.recommend.vo;

import com.deng.recommend.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收藏夹条目视图对象。
 *
 * <p>把商品信息平铺进来，前端渲染收藏卡片时不需要二次请求商品详情。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteVO {

    /** 收藏记录 ID */
    private Long id;

    private Long productId;

    private String name;

    private String subtitle;

    private String mainImage;

    private BigDecimal price;

    private Integer sales;

    /** 商品当前状态，0 表示已下架，前端可置灰提示 */
    private Integer productStatus;

    /** 收藏时间 */
    private LocalDateTime createTime;

    public static FavoriteVO of(Long favoriteId, LocalDateTime createTime, Product product) {
        return FavoriteVO.builder()
                .id(favoriteId)
                .productId(product.getId())
                .name(product.getName())
                .subtitle(product.getSubtitle())
                .mainImage(product.getMainImage())
                .price(product.getPrice())
                .sales(product.getSales())
                .productStatus(product.getStatus())
                .createTime(createTime)
                .build();
    }
}
