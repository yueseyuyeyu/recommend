package com.deng.recommend.vo;

import com.deng.recommend.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 足迹条目：用户浏览过或买过的商品。
 *
 * <p>在商品基础信息上追加 {@code times}（共几次）与 {@code lastTime}（最后一次时间），
 * 让个人中心的「浏览过 / 买过」列表能体现频次与新旧。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryVO {

    private Long id;

    private String name;

    private String subtitle;

    private String mainImage;

    private BigDecimal price;

    private Integer sales;

    /** 该商品的累计行为次数 */
    private Integer times;

    /** 最后一次行为时间 */
    private LocalDateTime lastTime;

    public static HistoryVO of(Product product, Integer times, LocalDateTime lastTime) {
        return HistoryVO.builder()
                .id(product.getId())
                .name(product.getName())
                .subtitle(product.getSubtitle())
                .mainImage(product.getMainImage())
                .price(product.getPrice())
                .sales(product.getSales())
                .times(times)
                .lastTime(lastTime)
                .build();
    }
}
