package com.ecommerce.product.controller;

import com.ecommerce.product.dto.ProductDto;
import com.ecommerce.product.model.ProductStatus;
import com.ecommerce.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ── CREATE ─────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ProductDto.Response> createProduct(
            @Valid @RequestBody ProductDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(productService.createProduct(request));
    }

    // ── READ ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto.Response> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductDto.Response> getProductBySku(@PathVariable String sku) {
        return ResponseEntity.ok(productService.getProductBySku(sku));
    }

    @GetMapping
    public ResponseEntity<Page<ProductDto.Summary>> getAllProducts(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(productService.getAllProducts(pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductDto.Summary>> searchProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
            productService.searchProducts(category, brand, status, minPrice, maxPrice, search, pageable)
        );
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<Page<ProductDto.Summary>> getByCategory(
            @PathVariable String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.getProductsByCategory(category, pageable));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<ProductDto.Summary>> getLowStock(
            @RequestParam(defaultValue = "10") int threshold) {
        return ResponseEntity.ok(productService.getLowStockProducts(threshold));
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────

    @PatchMapping("/{id}")
    public ResponseEntity<ProductDto.Response> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductDto.UpdateRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ProductDto.Response> deactivateProduct(@PathVariable Long id) {
        return ResponseEntity.ok(productService.deactivateProduct(id));
    }

    // ── STOCK / INVENTORY ──────────────────────────────────────────────────

    /**
     * Called by Order Service (via Feign) to check product availability.
     */
    @PostMapping("/inventory/check")
    public ResponseEntity<ProductDto.InventoryCheckResponse> checkInventory(
            @Valid @RequestBody ProductDto.InventoryCheckRequest request) {
        return ResponseEntity.ok(productService.checkAndReserveStock(request));
    }

    /**
     * Called by Order Service (via Feign) to deduct stock upon confirmed order.
     */
    @PostMapping("/sku/{sku}/deduct-stock")
    public ResponseEntity<Void> deductStock(
            @PathVariable String sku,
            @RequestParam int quantity) {
        productService.deductStock(sku, quantity);
        return ResponseEntity.noContent().build();
    }

    /**
     * Manual stock adjustment (admin operations).
     */
    @PostMapping("/{id}/stock-adjustment")
    public ResponseEntity<ProductDto.Response> adjustStock(
            @PathVariable Long id,
            @Valid @RequestBody ProductDto.StockAdjustmentRequest request) {
        return ResponseEntity.ok(productService.adjustStock(id, request));
    }

    // ── DELETE ─────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
