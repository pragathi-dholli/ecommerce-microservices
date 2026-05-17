package com.ecommerce.product.config;

/**
 * This interface lives in the ORDER SERVICE — shown here as documentation
 * of how Order Service will call Product Service via OpenFeign.
 *
 * In order-service, add this as:
 *   src/main/java/com/ecommerce/order/client/ProductServiceClient.java
 */

/*
import com.ecommerce.order.dto.InventoryCheckRequest;
import com.ecommerce.order.dto.InventoryCheckResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = "product-service",
    url = "${services.product.url}",          // e.g. http://product-service:8081
    fallback = ProductServiceClientFallback.class
)
public interface ProductServiceClient {

    @PostMapping("/api/v1/products/inventory/check")
    @CircuitBreaker(name = "productService", fallbackMethod = "inventoryCheckFallback")
    InventoryCheckResponse checkInventory(@RequestBody InventoryCheckRequest request);

    @PostMapping("/api/v1/products/sku/{sku}/deduct-stock")
    void deductStock(@PathVariable String sku, @RequestParam int quantity);
}

// ── FALLBACK ──────────────────────────────────────────────────────────────

@Component
class ProductServiceClientFallback implements ProductServiceClient {

    @Override
    public InventoryCheckResponse checkInventory(InventoryCheckRequest request) {
        // Circuit breaker open — return safe default: product unavailable
        return InventoryCheckResponse.builder()
            .sku(request.getSku())
            .available(false)
            .availableQuantity(0)
            .build();
    }

    @Override
    public void deductStock(String sku, int quantity) {
        throw new RuntimeException("Product Service unavailable — cannot deduct stock for SKU: " + sku);
    }
}
*/

// This file is documentation only. See comment above for usage in Order Service.
public class ProductServiceClientExample {}
