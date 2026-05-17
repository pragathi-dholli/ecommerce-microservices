package com.ecommerce.order.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTOs that mirror the contracts of Product Service and Payment Service.
 * These are used as request/response bodies in the Feign clients.
 */
public class ExternalServiceDto {

    // ── PRODUCT SERVICE ─────────────────────────────────────────────────────

    @Data
    @Builder
    public static class InventoryCheckRequest {
        private String sku;
        private Integer requestedQuantity;
    }

    @Data
    @Builder
    public static class InventoryCheckResponse {
        private String sku;
        private boolean available;
        private Integer availableQuantity;
        private BigDecimal unitPrice;
        private String productName;
    }

    // ── PAYMENT SERVICE ─────────────────────────────────────────────────────

    @Data
    @Builder
    public static class PaymentRequest {
        private String orderNumber;
        private BigDecimal amount;
        private String currency;
        private String customerId;
        private String customerEmail;
        private PaymentMethodDto paymentMethod;
    }

    @Data
    @Builder
    public static class PaymentMethodDto {
        private String type;         // CARD, BANK_TRANSFER, WALLET
        private String token;        // Tokenised card / account ref
    }

    @Data
    @Builder
    public static class PaymentResponse {
        private String paymentId;
        private String orderNumber;
        private String status;       // PENDING, COMPLETED, FAILED
        private String failureReason;
        private BigDecimal amount;
    }
}
