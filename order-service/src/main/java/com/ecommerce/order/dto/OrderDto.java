package com.ecommerce.order.dto;

import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.model.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDto {

    // ── CREATE REQUEST ──────────────────────────────────────────────────────

    @Data
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "Customer ID is required")
        private String customerId;

        @NotBlank(message = "Customer email is required")
        @Email(message = "Invalid email format")
        private String customerEmail;

        @NotEmpty(message = "Order must have at least one item")
        @Valid
        private List<OrderItemRequest> items;

        @Valid
        @NotNull(message = "Shipping address is required")
        private ShippingAddressDto shippingAddress;

        private String notes;
    }

    // ── ORDER ITEM REQUEST ──────────────────────────────────────────────────

    @Data
    @Builder
    public static class OrderItemRequest {

        @NotBlank(message = "Product SKU is required")
        private String productSku;

        @NotNull
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 999, message = "Quantity cannot exceed 999")
        private Integer quantity;
    }

    // ── SHIPPING ADDRESS ────────────────────────────────────────────────────

    @Data
    @Builder
    public static class ShippingAddressDto {

        @NotBlank private String fullName;
        @NotBlank private String addressLine1;
        private String addressLine2;
        @NotBlank private String city;
        @NotBlank private String state;
        @NotBlank private String postalCode;
        @NotBlank @Size(min = 2, max = 3) private String country;
        private String phone;
    }

    // ── STATUS UPDATE REQUEST ───────────────────────────────────────────────

    @Data
    @Builder
    public static class StatusUpdateRequest {

        @NotNull(message = "Status is required")
        private OrderStatus status;

        private String notes;
    }

    // ── ORDER ITEM RESPONSE ─────────────────────────────────────────────────

    @Data
    @Builder
    public static class OrderItemResponse {
        private Long id;
        private String productSku;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }

    // ── FULL RESPONSE ───────────────────────────────────────────────────────

    @Data
    @Builder
    public static class Response {
        private Long id;
        private String orderNumber;
        private String customerId;
        private String customerEmail;
        private List<OrderItemResponse> items;
        private BigDecimal subtotal;
        private BigDecimal shippingCost;
        private BigDecimal taxAmount;
        private BigDecimal totalAmount;
        private OrderStatus status;
        private ShippingAddressDto shippingAddress;
        private String paymentId;
        private PaymentStatus paymentStatus;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ── SUMMARY (for list views) ────────────────────────────────────────────

    @Data
    @Builder
    public static class Summary {
        private Long id;
        private String orderNumber;
        private String customerId;
        private BigDecimal totalAmount;
        private OrderStatus status;
        private PaymentStatus paymentStatus;
        private int itemCount;
        private LocalDateTime createdAt;
    }

    // ── PAYMENT CALLBACK (from Payment Service) ─────────────────────────────

    @Data
    @Builder
    public static class PaymentCallbackRequest {

        @NotBlank
        private String orderNumber;

        @NotBlank
        private String paymentId;

        @NotNull
        private PaymentStatus paymentStatus;

        private String failureReason;
    }
}
