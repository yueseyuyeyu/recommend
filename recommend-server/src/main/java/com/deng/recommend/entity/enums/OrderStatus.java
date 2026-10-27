package com.deng.recommend.entity.enums;

import lombok.Getter;

/**
 * 订单状态枚举。
 */
@Getter
public enum OrderStatus {

    /** 待支付 */
    PENDING(0, "待支付"),

    /** 已支付 */
    PAID(1, "已支付"),

    /** 已发货 */
    SHIPPED(2, "已发货"),

    /** 已完成 */
    FINISHED(3, "已完成"),

    /** 已取消 */
    CANCELLED(4, "已取消");

    private final int code;
    private final String name;

    OrderStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static OrderStatus of(Integer code) {
        if (code == null) {
            return PENDING;
        }
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的订单状态: " + code);
    }

    /** 该状态下用户是否可以取消订单 */
    public boolean cancellable() {
        return this == PENDING || this == PAID;
    }
}
