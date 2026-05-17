package com.ecommerce.product.service;

import com.ecommerce.product.dto.ProductDto;
import com.ecommerce.product.exception.DuplicateSkuException;
import com.ecommerce.product.exception.InsufficientStockException;
import com.ecommerce.product.exception.InvalidProductOperationException;
import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.ProductStatus;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    // ── CREATE ─────────────────────────────────────────────────────────────

    @Transactional
    public ProductDto.Response createProduct(ProductDto.CreateRequest request) {
        log.info("Creating product with SKU: {}", request.getSku());

        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }

        Product product = Product.builder()
            .name(request.getName())
            .description(request.getDescription())
            .sku(request.getSku())
            .price(request.getPrice())
            .stockQuantity(request.getStockQuantity())
            .category(request.getCategory())
            .brand(request.getBrand())
            .imageUrl(request.getImageUrl())
            .status(ProductStatus.ACTIVE)
            .build();

        Product saved = productRepository.save(product);
        log.info("Created product id={} sku={}", saved.getId(), saved.getSku());
        return toResponse(saved);
    }

    // ── READ ───────────────────────────────────────────────────────────────

    public ProductDto.Response getProductById(Long id) {
        return toResponse(findById(id));
    }

    public ProductDto.Response getProductBySku(String sku) {
        return toResponse(findBySku(sku));
    }

    public Page<ProductDto.Summary> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(this::toSummary);
    }

    public Page<ProductDto.Summary> searchProducts(
            String category, String brand, ProductStatus status,
            BigDecimal minPrice, BigDecimal maxPrice,
            String search, Pageable pageable) {

        return productRepository
            .findWithFilters(category, brand, status, minPrice, maxPrice, search, pageable)
            .map(this::toSummary);
    }

    public Page<ProductDto.Summary> getProductsByCategory(String category, Pageable pageable) {
        return productRepository.findByCategory(category, pageable).map(this::toSummary);
    }

    public List<ProductDto.Summary> getLowStockProducts(int threshold) {
        return productRepository.findLowStockProducts(threshold)
            .stream().map(this::toSummary).toList();
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────

    @Transactional
    public ProductDto.Response updateProduct(Long id, ProductDto.UpdateRequest request) {
        log.info("Updating product id={}", id);
        Product product = findById(id);

        if (request.getName() != null)          product.setName(request.getName());
        if (request.getDescription() != null)   product.setDescription(request.getDescription());
        if (request.getPrice() != null)         product.setPrice(request.getPrice());
        if (request.getStockQuantity() != null) product.setStockQuantity(request.getStockQuantity());
        if (request.getCategory() != null)      product.setCategory(request.getCategory());
        if (request.getBrand() != null)         product.setBrand(request.getBrand());
        if (request.getImageUrl() != null)      product.setImageUrl(request.getImageUrl());
        if (request.getStatus() != null)        product.setStatus(request.getStatus());

        return toResponse(productRepository.save(product));
    }

    // ── STOCK MANAGEMENT ───────────────────────────────────────────────────

    @Transactional
    public ProductDto.InventoryCheckResponse checkAndReserveStock(ProductDto.InventoryCheckRequest request) {
        Product product = findBySku(request.getSku());

        boolean available = product.isInStock() &&
                            product.getStockQuantity() >= request.getRequestedQuantity();

        return ProductDto.InventoryCheckResponse.builder()
            .sku(request.getSku())
            .available(available)
            .availableQuantity(product.getStockQuantity())
            .unitPrice(product.getPrice())
            .build();
    }

    @Transactional
    public ProductDto.Response adjustStock(Long id, ProductDto.StockAdjustmentRequest request) {
        log.info("Stock adjustment: product id={}, qty={}, reason={}", id, request.getQuantity(), request.getReason());
        Product product = findById(id);

        int qty = request.getQuantity();
        if (qty > 0) {
            product.increaseStock(qty);
        } else if (qty < 0) {
            int deduct = Math.abs(qty);
            if (product.getStockQuantity() < deduct) {
                throw new InsufficientStockException(product.getSku(), deduct, product.getStockQuantity());
            }
            product.decreaseStock(deduct);
        }

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void deductStock(String sku, int quantity) {
        Product product = findBySku(sku);
        if (product.getStockQuantity() < quantity) {
            throw new InsufficientStockException(sku, quantity, product.getStockQuantity());
        }
        int updated = productRepository.decrementStock(product.getId(), quantity);
        if (updated == 0) {
            throw new InsufficientStockException(sku, quantity, product.getStockQuantity());
        }
        log.info("Deducted {} units from SKU {}", quantity, sku);
    }

    // ── DELETE ─────────────────────────────────────────────────────────────

    @Transactional
    public void deleteProduct(Long id) {
        Product product = findById(id);
        if (product.getStockQuantity() > 0) {
            throw new InvalidProductOperationException(
                "Cannot delete product with existing stock. Deactivate it instead."
            );
        }
        productRepository.delete(product);
        log.info("Deleted product id={}", id);
    }

    @Transactional
    public ProductDto.Response deactivateProduct(Long id) {
        Product product = findById(id);
        product.setStatus(ProductStatus.INACTIVE);
        return toResponse(productRepository.save(product));
    }

    // ── PRIVATE HELPERS ────────────────────────────────────────────────────

    private Product findById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }

    private Product findBySku(String sku) {
        return productRepository.findBySku(sku)
            .orElseThrow(() -> new ProductNotFoundException(sku));
    }

    private ProductDto.Response toResponse(Product p) {
        return ProductDto.Response.builder()
            .id(p.getId())
            .name(p.getName())
            .description(p.getDescription())
            .sku(p.getSku())
            .price(p.getPrice())
            .stockQuantity(p.getStockQuantity())
            .category(p.getCategory())
            .brand(p.getBrand())
            .imageUrl(p.getImageUrl())
            .status(p.getStatus())
            .inStock(p.isInStock())
            .createdAt(p.getCreatedAt())
            .updatedAt(p.getUpdatedAt())
            .build();
    }

    private ProductDto.Summary toSummary(Product p) {
        return ProductDto.Summary.builder()
            .id(p.getId())
            .name(p.getName())
            .sku(p.getSku())
            .price(p.getPrice())
            .stockQuantity(p.getStockQuantity())
            .category(p.getCategory())
            .imageUrl(p.getImageUrl())
            .status(p.getStatus())
            .inStock(p.isInStock())
            .build();
    }
}
