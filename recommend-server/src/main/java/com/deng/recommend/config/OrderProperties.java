package com.deng.recommend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 订单相关配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "order")
public class OrderProperties {

    /**
     * 待支付订单的存活时长（分钟），超时后由定时任务自动取消。
     *
     * <p>设成 0 或负数表示关闭自动取消，便于个别环境（例如演示时想保留订单）关掉它。
     * 演示环境想快速看到效果，可以在 application.yml 里改成 2。
     */
    private int payTimeoutMinutes = 30;

    /** 自动取消任务的扫描间隔（毫秒），默认 5 分钟扫一次 */
    private long autoCancelIntervalMs = 300_000L;

    /**
     * 发货后多少天自动确认收货。
     *
     * <p>买家一直不点「确认收货」，订单会永远挂在「已发货」，
     * 交易无法完结。超过这个天数由定时任务自动完成，
     * 同时管理员也才被允许手工确认——避免管理员提前替买家确认。
     * 设成 0 或负数表示关闭自动确认，且管理员可随时确认。
     */
    private int autoReceiptDays = 7;
}
