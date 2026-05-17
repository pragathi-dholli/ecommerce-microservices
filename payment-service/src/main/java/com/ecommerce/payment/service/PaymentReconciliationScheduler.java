package com.ecommerce.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliationScheduler {

    private final PaymentService paymentService;

    @Value("${payment.stale-processing-minutes:10}")
    private int staleProcessingMinutes;

    /**
     * Every 5 minutes: find payments stuck in PROCESSING and mark them FAILED.
     * Notifies Order Service so orders don't stay blocked indefinitely.
     */
    @Scheduled(fixedDelayString = "${payment.reconciliation-interval-ms:300000}")
    public void reconcileStalePayments() {
        log.debug("Running payment reconciliation (stale threshold={}min)", staleProcessingMinutes);
        int reconciled = paymentService.reconcileStalePayments(staleProcessingMinutes);
        if (reconciled > 0) {
            log.warn("Reconciliation: marked {} stale payments as FAILED", reconciled);
        }
    }
}
