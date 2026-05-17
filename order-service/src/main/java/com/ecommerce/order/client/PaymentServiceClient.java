package com.ecommerce.order.client;

import com.ecommerce.order.dto.ExternalServiceDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = "payment-service",
    url = "${services.payment.url}",
    fallback = PaymentServiceClient.PaymentServiceFallback.class
)
public interface PaymentServiceClient {

    @PostMapping("/api/v1/payments")
    ExternalServiceDto.PaymentResponse initiatePayment(
        @RequestBody ExternalServiceDto.PaymentRequest request
    );

    @PostMapping("/api/v1/payments/{paymentId}/refund")
    ExternalServiceDto.PaymentResponse refundPayment(
        @PathVariable("paymentId") String paymentId
    );

    // ── Fallback ──────────────────────────────────────────────────────────

    @org.springframework.stereotype.Component
    class PaymentServiceFallback implements PaymentServiceClient {

        @Override
        public ExternalServiceDto.PaymentResponse initiatePayment(
                ExternalServiceDto.PaymentRequest request) {
            // Circuit open — report payment as failed so order is not stuck
            return ExternalServiceDto.PaymentResponse.builder()
                .orderNumber(request.getOrderNumber())
                .status("FAILED")
                .failureReason("Payment Service is currently unavailable. Please try again shortly.")
                .amount(request.getAmount())
                .build();
        }

        @Override
        public ExternalServiceDto.PaymentResponse refundPayment(String paymentId) {
            throw new com.ecommerce.order.exception.ServiceUnavailableException(
                "Payment Service is unavailable — cannot process refund for payment: " + paymentId
            );
        }
    }
}
