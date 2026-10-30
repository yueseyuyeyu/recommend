package com.deng.recommend.entity.enums;

import lombok.Getter;

import java.util.Set;

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

    /**
     * 允许流转到的下一个状态集合。
     *
     * <p>用集合而不是 if 判断，是为了让「合法路径」集中在一处声明：
     * <pre>
     * 待支付 → 已支付 / 已取消
     * 已支付 → 已发货 / 已取消
     * 已发货 → 已完成
     * 已完成、已取消 → 终态，不可再变更
     * </pre>
     *
     * <p>注意「待支付 → 已支付」只能走支付接口（会写支付方式与支付时间），
     * 后台直接改状态绕过了这一步，因此这里不把它列为可变更路径。
     */
    public Set<OrderStatus> nextStates() {
        return switch (this) {
            case PENDING -> Set.of(CANCELLED);
            case PAID -> Set.of(SHIPPED, CANCELLED);
            case SHIPPED -> Set.of(FINISHED);
            case FINISHED, CANCELLED -> Set.of();
        };
    }

    /**
     * 判断能否从当前状态流转到目标状态。
     */
    public boolean canTransitionTo(OrderStatus target) {
        return target != null && nextStates().contains(target);
    }
}
