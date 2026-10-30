package com.deng.recommend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态变更日志，对应表 {@code order_status_log}。
 *
 * <p>只追加不修改。每次状态流转写一行，用来回答
 * 「这个订单是什么时候、被谁、从什么状态改成了什么」。
 */
@Data
@TableName("order_status_log")
public class OrderStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    /** 变更前状态；订单创建事件为 null */
    private Integer fromStatus;

    /** 变更后状态 */
    private Integer toStatus;

    /** 操作者类型，取值见 {@link com.deng.recommend.entity.enums.OperatorType} */
    private String operatorType;

    /** 操作者 ID，系统操作为 null */
    private Long operatorId;

    /** 操作者名称快照，避免用户改名后历史日志对不上 */
    private String operatorName;

    /** 备注，如物流单号、取消原因 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
