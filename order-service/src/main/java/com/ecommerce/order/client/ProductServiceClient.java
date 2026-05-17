package com.ecommerce.order.client;

import com.ecommerce.order.dto.ExternalServiceDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = "product-service",
    url = "${services.product.url}",
    fallback = ProductServiceClient.ProductServiceFallback.class
)
public interface ProductServiceClient {

    @PostMapping("/api/v1/products/inventory/check")
    ExternalServiceDto.InventoryCheckResponse checkInventory(
        @RequestBody ExternalServiceDto.InventoryCheckRequest request
    );

    @PostMapping("/api/v1/products/sku/{sku}/deduct-stock")
    void deductStock(
        @PathVariable("sku") String sku,
        @RequestParam("quantity") int quantity
    );

    @PostMapping("/api/v1/products/sku/{sku}/deduct-stock")
    void restoreStock(
        @PathVariable("sku") String sku,
        @RequestParam("quantity") int quantity
    );

    // ── Fallback ──────────────────────────────────────────────────────────

    @org.springframework.stereotype.Component
    class ProductServiceFallback implements ProductServiceClient {

        @Override
        public ExternalServiceDto.InventoryCheckResponse checkInventory(
                ExternalServiceDto.InventoryCheckRequest request) {
            // Circuit open — report product as unavailable
            return ExternalServiceDto.InventoryCheckResponse.builder()
                .sku(request.getSku())
                .available(false)
                .availableQuantity(0)
                .build();
        }

        @Override
        public void deductStock(String sku, int quantity) {
            throw new com.ecommerce.order.exception.ServiceUnavailableException(
                "Product Service is unavailable — cannot deduct stock for SKU: " + sku
            );
        }

        @Override
        public void restoreStock(String sku, int quantity) {
            // Best-effort: log and move on — a reconciliation job handles this
            org.slf4j.LoggerFactory.getLogger(ProductServiceFallback.class)
                .error("STOCK RESTORE FAILED (circuit open) — SKU: {}, qty: {}. Manual reconciliation needed.", sku, quantity);
        }
    }
}
