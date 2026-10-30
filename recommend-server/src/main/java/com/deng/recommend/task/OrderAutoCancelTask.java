package com.deng.recommend.task;

import com.deng.recommend.config.OrderProperties;
import com.deng.recommend.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时未支付订单的自动取消任务。
 *
 * <p><b>为什么要它</b>：下单即扣库存，如果用户下单后一直不付款，
 * 商品会被一直占着卖不出去。定时把这些订单取消并回滚库存，
 * 是电商系统里最基本的一个兜底。
 *
 * <p><b>为什么不严格按间隔跑</b>：用 {@code fixedDelay}（上一轮结束后再等）
 * 而不是 {@code fixedRate}，避免某轮处理慢时任务堆叠。
 *
 * <p><b>为什么首次延迟</b>：留出应用预热时间，也避免本地启动时
 * 立刻把种子数据里的历史待支付订单一次性全取消，方便观察。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoCancelTask {

    private final OrderService orderService;
    private final OrderProperties orderProperties;

    @Scheduled(
            initialDelayString = "${order.auto-cancel-initial-delay-ms:120000}",
            fixedDelayString = "${order.auto-cancel-interval-ms:300000}"
    )
    public void autoCancelExpiredOrders() {
        int timeoutMinutes = orderProperties.getPayTimeoutMinutes();
        if (timeoutMinutes <= 0) {
            // 配置为 0 或负数表示显式关闭，直接跳过
            return;
        }

        try {
            int cancelled = orderService.cancelExpiredOrders(timeoutMinutes);
            if (cancelled > 0) {
                log.info("自动取消了 {} 笔超时未支付订单（阈值 {} 分钟）", cancelled, timeoutMinutes);
            }
        } catch (Exception e) {
            // 任务本身不能抛出去，否则后续调度会被取消
            log.error("超时未支付订单自动取消任务执行失败", e);
        }
    }
}
