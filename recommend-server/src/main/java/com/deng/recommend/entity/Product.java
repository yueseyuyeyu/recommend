package com.deng.recommend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体，对应表 {@code product}。
 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属类目 ID */
    private Long categoryId;

    /** 商品名称 */
    private String name;

    /** 副标题 */
    private String subtitle;

    /** 主图 URL */
    private String mainImage;

    /** 售价，使用 DECIMAL 避免浮点误差 */
    private BigDecimal price;

    /** 原价 */
    private BigDecimal originalPrice;

    /** 库存，不得为负 */
    private Integer stock;

    /** 累计销量 */
    private Integer sales;

    /** 上架状态：1-上架，0-下架 */
    private Integer status;

    /** 商品详情描述 */
    private String description;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 是否上架，供推荐过滤下架商品 */
    public boolean isOnSale() {
        return Integer.valueOf(1).equals(this.status);
    }
}
