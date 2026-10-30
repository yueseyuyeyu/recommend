package com.deng.recommend.entity.enums;

/**
 * 站内通知类型。
 *
 * <p>用字符串入库而不是 TINYINT，是因为通知类型会随业务不断新增，
 * 字符串在前端直接做图标映射更直观，也不会因为码值冲突而返工。
 */
public enum NotificationType {

    /** 订单已发货 */
    ORDER_SHIPPED,

    /** 订单已支付 */
    ORDER_PAID,

    /** 订单已取消 */
    ORDER_CANCELLED
}
