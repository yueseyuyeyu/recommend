package com.deng.recommend.vo;

import com.deng.recommend.entity.Carousel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 轮播图视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarouselVO {

    private Long id;

    private String title;

    private String subtitle;

    private String imageUrl;

    /** CSS 渐变色值，imageUrl 为空时前端用它做背景 */
    private String bgColor;

    private String linkUrl;

    public static CarouselVO from(Carousel carousel) {
        return CarouselVO.builder()
                .id(carousel.getId())
                .title(carousel.getTitle())
                .subtitle(carousel.getSubtitle())
                .imageUrl(carousel.getImageUrl())
                .bgColor(carousel.getBgColor())
                .linkUrl(carousel.getLinkUrl())
                .build();
    }
}
