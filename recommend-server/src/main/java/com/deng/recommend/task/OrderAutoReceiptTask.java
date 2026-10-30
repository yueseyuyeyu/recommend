package com.deng.recommend.task;

import com.deng.recommend.config.OrderProperties;
import com.deng.recommend.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 发货超时未确认收货的自动完成任务。
 *
 * <p><b>为什么需要</b>：买家不点「确认收货」，订单会永远停在「已发货」，
 * 交易无法完结、评价入口也开不出来。电商平台普遍用「发货 N 天自动确认」兜底。
 *
 * <p>本任务与 {@link OrderAutoCancelTask} 互补：一个收尾未支付的单，一个收尾未收货的单。
 * 同样用 {@code fixedDelay} 避免慢轮次堆叠，并吞掉异常防止调度被中断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoReceiptTask {

    private final OrderService orderService;
    private final OrderProperties orderProperties;

    @Scheduled(
            initialDelayString = "${order.auto-receipt-initial-delay-ms:120000}",
            fixedDelayString = "${order.auto-receipt-interval-ms:1800000}"
    )
    public void autoConfirmReceipts() {
        int days = orderProperties.getAutoReceiptDays();
        if (days <= 0) {
            return;
        }

        try {
            int confirmed = orderService.confirmExpiredReceipts(days);
            if (confirmed > 0) {
                log.info("自动确认收货 {} 笔（发货满 {} 天）", confirmed, days);
            }
        } catch (Exception e) {
            log.error("自动确认收货任务执行失败", e);
        }
    }
}
