package com.deng.recommend.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 个人中心顶部的待办数量，对应订单各状态与购物车。
 *
 * <p>字段命名对齐淘宝个人中心那一排数字：
 * 购物车 / 待收货 / 待发货 / 待付款 / 待评价。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsVO {

    /** 购物车条目数 */
    private long cartCount;

    /** 待付款：订单状态 0 */
    private long pendingPayment;

    /** 待发货：订单状态 1（已支付未发货） */
    private long pendingShipment;

    /** 待收货：订单状态 2（已发货未确认） */
    private long pendingReceipt;

    /** 待评价：已完成订单中的商品数减去已评价数，不为负 */
    private long pendingReview;
}
