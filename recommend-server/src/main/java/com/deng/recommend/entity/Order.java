package com.deng.recommend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deng.recommend.entity.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体，对应表 {@code orders}。
 *
 * <p>表名使用 {@code orders} 而非 {@code order}，因为 {@code order} 是 MySQL 保留关键字。
 */
@Data
@TableName("orders")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务订单号，唯一 */
    private String orderNo;

    private Long userId;

    /** 订单总金额 */
    private BigDecimal totalAmount;

    /** 状态码，取值见 {@link OrderStatus} */
    private Integer status;

    /** 支付方式码，取值见 {@link com.deng.recommend.entity.enums.PayType}；未支付前为 null */
    private Integer payType;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddr;

    private LocalDateTime paidTime;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public OrderStatus getStatusEnum() {
        return OrderStatus.of(this.status);
    }
}
