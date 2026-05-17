package com.ecommerce.payment.config;

import com.ecommerce.payment.dto.PaymentDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Simulated payment gateway.
 *
 * In production, replace this class with calls to your real gateway SDK
 * (e.g. Stripe, Adyen, PayPal). The rest of the service is unchanged.
 *
 * Simulation rules (based on token prefix):
 *   tok_success_*  → COMPLETED
 *   tok_decline_*  → FAILED  (insufficient_funds)
 *   tok_error_*    → FAILED  (gateway_error)
 *   anything else  → COMPLETED  (default happy path)
 */
@Component
@Slf4j
public class PaymentGatewayClient {

    private static final Map<String, String> DECLINE_CODES = Map.of(
        "tok_decline_funds",   "insufficient_funds",
        "tok_decline_expired", "card_expired",
        "tok_decline_stolen",  "card_stolen",
        "tok_decline_cvc",     "incorrect_cvc"
    );

    public PaymentDto.GatewayResponse charge(String token, BigDecimal amount, String currency) {
        log.info("Gateway charge: token={}, amount={} {}", token, amount, currency);

        // Simulate network latency
        simulateLatency();

        if (token.startsWith("tok_error_")) {
            log.warn("Gateway error simulated for token={}", token);
            return PaymentDto.GatewayResponse.builder()
                .success(false)
                .declineCode("gateway_error")
                .message("Payment gateway encountered an internal error")
                .build();
        }

        if (token.startsWith("tok_decline_") || DECLINE_CODES.containsKey(token)) {
            String declineCode = DECLINE_CODES.getOrDefault(token, "card_declined");
            log.warn("Payment declined: token={}, code={}", token, declineCode);
            return PaymentDto.GatewayResponse.builder()
                .success(false)
                .declineCode(declineCode)
                .message(humanReadableDecline(declineCode))
                .build();
        }

        // Success path
        String transactionId = "gw_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("Gateway charge successful: transactionId={}", transactionId);
        return PaymentDto.GatewayResponse.builder()
            .success(true)
            .transactionId(transactionId)
            .message("Payment processed successfully")
            .build();
    }

    public PaymentDto.GatewayResponse refund(String gatewayReference, BigDecimal amount) {
        log.info("Gateway refund: reference={}, amount={}", gatewayReference, amount);
        simulateLatency();

        if (gatewayReference == null || gatewayReference.startsWith("gw_error")) {
            return PaymentDto.GatewayResponse.builder()
                .success(false)
                .declineCode("refund_failed")
                .message("Refund could not be processed by gateway")
                .build();
        }

        String refundId = "rf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("Gateway refund successful: refundId={}", refundId);
        return PaymentDto.GatewayResponse.builder()
            .success(true)
            .transactionId(refundId)
            .message("Refund processed successfully")
            .build();
    }

    private void simulateLatency() {
        try {
            Thread.sleep(100); // Simulate ~100ms gateway round trip
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String humanReadableDecline(String code) {
        return switch (code) {
            case "insufficient_funds" -> "Your card has insufficient funds";
            case "card_expired"       -> "Your card has expired";
            case "card_stolen"        -> "This card has been reported lost or stolen";
            case "incorrect_cvc"      -> "The CVC code is incorrect";
            default                   -> "Your payment was declined";
        };
    }
}
