package com.deng.recommend.common;

import lombok.Getter;

/**
 * 统一错误码枚举。
 *
 * <p>参考项目用字符串 {@code "true"}/{@code "false"} 表示成败，
 * 调用方无法区分失败原因。本项目使用结构化错误码，前端可据此精确处理。
 */
@Getter
public enum ResultCode {

    /* ===== 通用 ===== */
    SUCCESS(0, "success"),
    PARAM_ERROR(1001, "参数校验失败"),
    NOT_FOUND(1004, "资源不存在"),
    FORBIDDEN(1003, "无权限访问"),
    UNAUTHORIZED(1002, "未登录或登录已过期"),

    /* ===== 用户与认证 2000-2999 ===== */
    USERNAME_EXISTS(2001, "用户名已被注册"),
    USERNAME_OR_PASSWORD_ERROR(2002, "用户名或密码错误"),
    USER_DISABLED(2003, "账号已被禁用"),
    PASSWORD_MISMATCH(2004, "两次输入的密码不一致"),
    OLD_PASSWORD_ERROR(2005, "原密码错误"),

    /* ===== 商品与类目 3000-3999 ===== */
    PRODUCT_NOT_FOUND(3001, "商品不存在"),
    PRODUCT_OFF_SHELF(3002, "商品已下架"),
    CATEGORY_NOT_FOUND(3003, "类目不存在"),

    /* ===== 交易 4000-4999 ===== */
    STOCK_NOT_ENOUGH(4001, "库存不足"),
    CART_EMPTY(4002, "购物车为空"),
    ORDER_NOT_FOUND(4003, "订单不存在"),
    ORDER_NOT_CANCELLABLE(4004, "当前状态不允许取消订单"),
    CREATE_ORDER_FAILED(4005, "下单失败，请重试"),

    /* ===== 推荐 5000-5999 ===== */
    RECOMMEND_DEGRADED(5001, "推荐服务已降级为热门推荐"),

    /* ===== 系统 9000-9999 ===== */
    SYSTEM_ERROR(9999, "系统内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
