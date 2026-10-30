package com.deng.recommend.entity.enums;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 配送方（快递公司）。
 *
 * <p>每个配送方带一套单号生成规则：{@code prefix + N 位数字 + suffix}。
 * 生成出来的单号只是格式上像真实单号，并不是在快递公司登记过的运单，
 * 本项目的发货是模拟动作，不会真的产生物流轨迹。
 */
public enum Carrier {

    /** 顺丰速运：SF + 13 位数字 */
    SF("SF", "顺丰速运", "SF", 13, ""),

    /** 中国邮政 EMS：E + 9 位数字 + CN */
    EMS("EMS", "中国邮政", "E", 9, "CN"),

    /** 中通快递：75 开头共 12 位 */
    ZTO("ZTO", "中通快递", "75", 10, ""),

    /** 圆通速递：YT + 13 位数字 */
    YTO("YTO", "圆通速递", "YT", 13, ""),

    /** 韵达快递：43 开头共 12 位 */
    YUNDA("YUNDA", "韵达快递", "43", 10, ""),

    /** 京东物流：JD + 12 位数字 */
    JD("JD", "京东物流", "JD", 12, "");

    /** 存库用的编码 */
    private final String code;

    /** 展示名称 */
    private final String displayName;

    private final String prefix;

    private final int digitCount;

    private final String suffix;

    Carrier(String code, String displayName, String prefix, int digitCount, String suffix) {
        this.code = code;
        this.displayName = displayName;
        this.prefix = prefix;
        this.digitCount = digitCount;
        this.suffix = suffix;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 按配送方规则生成一个物流单号。
     *
     * <p>不做全局唯一性校验：随机 13 位数字在本项目的数据量下碰撞概率极低，
     * 且单号本身没有业务含义，真要接真实渠道时单号由对方下发。
     */
    public String generateTrackingNo() {
        StringBuilder builder = new StringBuilder(prefix);
        for (int i = 0; i < digitCount; i++) {
            builder.append(ThreadLocalRandom.current().nextInt(10));
        }
        return builder.append(suffix).toString();
    }

    /**
     * 按编码解析，未知值抛异常（由全局异常处理转成参数错误）。
     */
    public static Carrier of(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("请选择配送方");
        }
        for (Carrier carrier : values()) {
            if (carrier.code.equalsIgnoreCase(code.trim())) {
                return carrier;
            }
        }
        throw new IllegalArgumentException("未知的配送方: " + code);
    }

    /**
     * 取展示名，未知或空返回 null，便于 VO 组装时不抛异常。
     */
    public static String nameOf(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (Carrier carrier : values()) {
            if (carrier.code.equalsIgnoreCase(code.trim())) {
                return carrier.displayName;
            }
        }
        return null;
    }
}
