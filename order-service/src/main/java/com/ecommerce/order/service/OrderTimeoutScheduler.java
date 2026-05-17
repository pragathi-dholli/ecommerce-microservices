package com.ecommerce.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderTimeoutScheduler {

    private final OrderService orderService;

    @Value("${order.pending-timeout-minutes:30}")
    private int pendingTimeoutMinutes;

    /**
     * Every 5 minutes: cancel orders stuck in PENDING beyond the timeout window.
     * Restores stock so items don't stay reserved indefinitely.
     */
    @Scheduled(fixedDelayString = "${order.timeout-check-interval-ms:300000}")
    public void cancelStaleOrders() {
        log.debug("Running stale order cleanup (timeout={}min)", pendingTimeoutMinutes);
        int cancelled = orderService.cancelStaleOrders(pendingTimeoutMinutes);
        if (cancelled > 0) {
            log.info("Stale order cleanup: cancelled {} orders", cancelled);
        }
    }
}
