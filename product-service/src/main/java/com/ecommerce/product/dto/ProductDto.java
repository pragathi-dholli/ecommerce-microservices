package com.ecommerce.product.dto;

import com.ecommerce.product.model.ProductStatus;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductDto {

    // ── CREATE REQUEST ──────────────────────────────────────────────────────

    @Data
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "Product name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        private String name;

        private String description;

        @NotBlank(message = "SKU is required")
        @Pattern(regexp = "^[A-Z0-9\\-]{3,50}$", message = "SKU must be 3-50 uppercase alphanumeric characters or hyphens")
        private String sku;

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "Invalid price format")
        private BigDecimal price;

        @NotNull(message = "Stock quantity is required")
        @Min(value = 0, message = "Stock quantity cannot be negative")
        private Integer stockQuantity;

        @NotBlank(message = "Category is required")
        @Size(max = 100)
        private String category;

        @Size(max = 100)
        private String brand;

        @Size(max = 500)
        private String imageUrl;
    }

    // ── UPDATE REQUEST ──────────────────────────────────────────────────────

    @Data
    @Builder
    public static class UpdateRequest {

        @Size(max = 255)
        private String name;

        private String description;

        @DecimalMin(value = "0.01")
        @Digits(integer = 10, fraction = 2)
        private BigDecimal price;

        @Min(0)
        private Integer stockQuantity;

        @Size(max = 100)
        private String category;

        @Size(max = 100)
        private String brand;

        @Size(max = 500)
        private String imageUrl;

        private ProductStatus status;
    }

    // ── STOCK ADJUSTMENT REQUEST ────────────────────────────────────────────

    @Data
    @Builder
    public static class StockAdjustmentRequest {

        @NotNull
        private Integer quantity; // Positive = restock, Negative = deduct

        @NotBlank
        private String reason; // e.g. "ORDER_PLACED", "RETURN", "MANUAL_CORRECTION"
    }

    // ── RESPONSE ────────────────────────────────────────────────────────────

    @Data
    @Builder
    public static class Response {
        private Long id;
        private String name;
        private String description;
        private String sku;
        private BigDecimal price;
        private Integer stockQuantity;
        private String category;
        private String brand;
        private String imageUrl;
        private ProductStatus status;
        private boolean inStock;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ── SUMMARY (for listings) ──────────────────────────────────────────────

    @Data
    @Builder
    public static class Summary {
        private Long id;
        private String name;
        private String sku;
        private BigDecimal price;
        private Integer stockQuantity;
        private String category;
        private String imageUrl;
        private ProductStatus status;
        private boolean inStock;
    }

    // ── INVENTORY CHECK (used by Order Service via Feign) ──────────────────

    @Data
    @Builder
    public static class InventoryCheckRequest {
        @NotBlank
        private String sku;

        @NotNull
        @Min(1)
        private Integer requestedQuantity;
    }

    @Data
    @Builder
    public static class InventoryCheckResponse {
        private String sku;
        private boolean available;
        private Integer availableQuantity;
        private BigDecimal unitPrice;
    }
}
