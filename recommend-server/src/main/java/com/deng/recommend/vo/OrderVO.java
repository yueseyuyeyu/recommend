package com.deng.recommend.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO {

    private String orderNo;

    private BigDecimal totalAmount;

    /** 状态码 */
    private Integer status;

    /** 状态名称，如「待支付」 */
    private String statusName;

    /** 支付方式码：1-支付宝，2-微信支付，3-银行卡 */
    private Integer payType;

    /** 支付方式名称，如「支付宝」 */
    private String payTypeName;

    /** 支付时间，未支付为 null */
    private LocalDateTime paidTime;

    /** 物流单号，未发货为 null */
    private String trackingNo;

    /** 配送方编码 */
    private String carrier;

    /** 配送方名称，如「顺丰速运」 */
    private String carrierName;

    /** 发货时间 */
    private LocalDateTime shipTime;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddr;

    private LocalDateTime createTime;

    private List<OrderItemVO> items;
}
