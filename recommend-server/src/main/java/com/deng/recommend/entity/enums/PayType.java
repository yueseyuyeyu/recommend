package com.deng.recommend.entity.enums;

/**
 * 支付方式。
 *
 * <p>本项目不接入真实支付渠道，「支付」是一次状态流转：
 * 用户选择方式并确认后，订单从 {@code PENDING} 变为 {@code PAID} 并记录支付时间。
 * 接真实渠道时，这里只需补上各渠道的预下单与回调逻辑，枚举本身不用改。
 */
public enum PayType {

    /** 支付宝 */
    ALIPAY(1, "支付宝"),

    /** 微信支付 */
    WECHAT(2, "微信支付"),

    /** 银行卡 */
    BANK_CARD(3, "银行卡");

    private final int code;

    private final String name;

    PayType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public int getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    /**
     * 根据码值解析，未知值抛异常而不是静默返回 null。
     */
    public static PayType of(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("支付方式不能为空");
        }
        for (PayType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的支付方式: " + code);
    }

    /**
     * 根据码值取名称，未知值返回 null，便于 VO 组装时不抛异常。
     */
    public static String nameOf(Integer code) {
        if (code == null) {
            return null;
        }
        for (PayType type : values()) {
            if (type.code == code) {
                return type.name;
            }
        }
        return null;
    }
}
