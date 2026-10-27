package com.deng.recommend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细实体，对应表 {@code order_item}。
 *
 * <p><b>设计说明</b>：冗余存储 {@code productName} 与 {@code price} 快照，
 * 避免商品后续调价或下架导致历史订单数据失真。
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联订单号，逻辑外键 */
    private String orderNo;

    private Long productId;

    /** 下单时商品名快照 */
    private String productName;

    /** 下单时单价快照 */
    private BigDecimal price;

    private Integer quantity;

    /** 小计 = price × quantity */
    private BigDecimal subtotal;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
