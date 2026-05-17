package com.ecommerce.payment.dto;

import com.ecommerce.payment.model.PaymentMethod;
import com.ecommerce.payment.model.PaymentStatus;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentDto {

    // ── INITIATE PAYMENT REQUEST ────────────────────────────────────────────

    @Data
    @Builder
    public static class PaymentRequest {

        @NotBlank(message = "Order number is required")
        private String orderNumber;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
        @Digits(integer = 10, fraction = 2)
        private BigDecimal amount;

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
        private String currency;

        @NotBlank(message = "Customer ID is required")
        private String customerId;

        @NotBlank
        @Email
        private String customerEmail;

        @NotNull(message = "Payment method is required")
        private PaymentMethodDto paymentMethod;
    }

    // ── PAYMENT METHOD ─────────────────────────────────────────────────────

    @Data
    @Builder
    public static class PaymentMethodDto {

        @NotBlank(message = "Payment type is required")
        private String type;     // CARD, BANK_TRANSFER, WALLET

        @NotBlank(message = "Payment token is required")
        private String token;    // Tokenised reference — never raw card data
    }

    // ── REFUND REQUEST ─────────────────────────────────────────────────────

    @Data
    @Builder
    public static class RefundRequest {

        @DecimalMin(value = "0.01")
        @Digits(integer = 10, fraction = 2)
        private BigDecimal amount;   // null = full refund

        @NotBlank(message = "Refund reason is required")
        @Size(max = 500)
        private String reason;
    }

    // ── PAYMENT RESPONSE ───────────────────────────────────────────────────

    @Data
    @Builder
    public static class PaymentResponse {
        private Long id;
        private String paymentId;
        private String orderNumber;
        private String customerId;
        private String customerEmail;
        private BigDecimal amount;
        private String currency;
        private PaymentStatus status;
        private PaymentMethod method;
        private String gatewayReference;
        private String failureReason;
        private String refundReference;
        private BigDecimal refundAmount;
        private LocalDateTime refundedAt;
        private Integer attemptCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ── SUMMARY (for list views) ────────────────────────────────────────────

    @Data
    @Builder
    public static class Summary {
        private Long id;
        private String paymentId;
        private String orderNumber;
        private String customerId;
        private BigDecimal amount;
        private String currency;
        private PaymentStatus status;
        private PaymentMethod method;
        private LocalDateTime createdAt;
    }

    // ── ORDER SERVICE CALLBACK (sent back to Order Service) ─────────────────

    @Data
    @Builder
    public static class OrderCallbackRequest {
        private String orderNumber;
        private String paymentId;
        private String paymentStatus;   // "COMPLETED" | "FAILED"
        private String failureReason;
    }

    // ── GATEWAY RESPONSE (simulated payment gateway contract) ───────────────

    @Data
    @Builder
    public static class GatewayResponse {
        private boolean success;
        private String transactionId;
        private String declineCode;     // e.g. "insufficient_funds", "card_expired"
        private String message;
    }
}
