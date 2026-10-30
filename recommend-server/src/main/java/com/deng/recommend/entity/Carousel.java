package com.deng.recommend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页轮播图实体，对应表 {@code carousel}。
 *
 * <p><b>设计说明</b>：{@code bgColor} 存 CSS 渐变色值，
 * 这样在没有图片素材的情况下轮播图依然能正常展示；
 * 后续接入真实图片时填 {@code imageUrl} 即可覆盖渐变，无需改表结构。
 */
@Data
@TableName("carousel")
public class Carousel {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 主标题 */
    private String title;

    /** 副标题 */
    private String subtitle;

    /** 背景图 URL，为空时使用 bgColor 渐变 */
    private String imageUrl;

    /** CSS 渐变色值，如 linear-gradient(135deg,#ff6a00,#ee0979) */
    private String bgColor;

    /** 点击跳转路径，如 /products?categoryId=3 */
    private String linkUrl;

    /** 排序值，越小越靠前 */
    private Integer sortOrder;

    /** 状态：1-启用，0-停用 */
    private Integer status;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
