package com.ecommerce.product.repository;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ProductRepository Tests")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();

        productRepository.saveAll(List.of(
            buildProduct("ELEC-001", "Sony Headphones",     "Electronics", "Sony",  199.99, 100, ProductStatus.ACTIVE),
            buildProduct("ELEC-002", "Samsung Monitor",     "Electronics", "Samsung", 499.99, 5, ProductStatus.ACTIVE),
            buildProduct("FURN-001", "Ergonomic Chair",     "Furniture",   "IKEA",  399.99, 0, ProductStatus.ACTIVE),
            buildProduct("SPRT-001", "Running Shoes",       "Sports",      "Nike",   89.99, 200, ProductStatus.ACTIVE),
            buildProduct("ELEC-003", "Old TV",              "Electronics", "LG",    299.99, 3, ProductStatus.DISCONTINUED)
        ));
    }

    @Test
    @DisplayName("findBySku: should find existing product")
    void shouldFindBySku() {
        Optional<Product> result = productRepository.findBySku("ELEC-001");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Sony Headphones");
    }

    @Test
    @DisplayName("findBySku: should return empty for unknown SKU")
    void shouldReturnEmptyForUnknownSku() {
        assertThat(productRepository.findBySku("UNKNOWN-999")).isEmpty();
    }

    @Test
    @DisplayName("existsBySku: should return true for existing SKU")
    void shouldReturnTrueForExistingSku() {
        assertThat(productRepository.existsBySku("ELEC-001")).isTrue();
        assertThat(productRepository.existsBySku("DOES-NOT-EXIST")).isFalse();
    }

    @Test
    @DisplayName("findByCategory: should return only matching products")
    void shouldFindByCategory() {
        Page<Product> result = productRepository.findByCategory("Electronics", PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).allMatch(p -> p.getCategory().equals("Electronics"));
    }

    @Test
    @DisplayName("findWithFilters: should filter by category and status")
    void shouldFilterByCategoryAndStatus() {
        Page<Product> result = productRepository.findWithFilters(
            "Electronics", null, ProductStatus.ACTIVE,
            null, null, null,
            PageRequest.of(0, 10)
        );
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(p -> p.getStatus() == ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("findWithFilters: should filter by price range")
    void shouldFilterByPriceRange() {
        Page<Product> result = productRepository.findWithFilters(
            null, null, null,
            new BigDecimal("100.00"), new BigDecimal("400.00"),
            null,
            PageRequest.of(0, 10)
        );
        assertThat(result.getContent())
            .allMatch(p -> p.getPrice().compareTo(new BigDecimal("100.00")) >= 0
                        && p.getPrice().compareTo(new BigDecimal("400.00")) <= 0);
    }

    @Test
    @DisplayName("findWithFilters: should search by name keyword")
    void shouldSearchByNameKeyword() {
        Page<Product> result = productRepository.findWithFilters(
            null, null, null, null, null, "headphones",
            PageRequest.of(0, 10)
        );
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getSku()).isEqualTo("ELEC-001");
    }

    @Test
    @DisplayName("findLowStockProducts: should return products below threshold")
    void shouldFindLowStockProducts() {
        List<Product> lowStock = productRepository.findLowStockProducts(10);
        // ELEC-002 (qty=5), FURN-001 (qty=0) and ELEC-003 (qty=3, DISCONTINUED not ACTIVE)
        // Only ACTIVE ones: ELEC-002 (5), FURN-001 (0)
        assertThat(lowStock).hasSize(2);
        assertThat(lowStock).allMatch(p -> p.getStockQuantity() <= 10);
        assertThat(lowStock).allMatch(p -> p.getStatus() == ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("decrementStock: should reduce quantity when sufficient stock")
    void shouldDecrementStock() {
        Product product = productRepository.findBySku("ELEC-001").get();
        int updated = productRepository.decrementStock(product.getId(), 20);

        assertThat(updated).isEqualTo(1);
        Product refreshed = productRepository.findById(product.getId()).get();
        assertThat(refreshed.getStockQuantity()).isEqualTo(80);
    }

    @Test
    @DisplayName("decrementStock: should return 0 rows when insufficient stock (safe)")
    void shouldNotDecrementWhenInsufficientStock() {
        Product product = productRepository.findBySku("ELEC-001").get();
        int updated = productRepository.decrementStock(product.getId(), 9999);

        assertThat(updated).isEqualTo(0); // No rows updated — safe
        Product refreshed = productRepository.findById(product.getId()).get();
        assertThat(refreshed.getStockQuantity()).isEqualTo(100); // Unchanged
    }

    @Test
    @DisplayName("incrementStock: should increase stock quantity")
    void shouldIncrementStock() {
        Product product = productRepository.findBySku("ELEC-001").get();
        productRepository.incrementStock(product.getId(), 50);

        Product refreshed = productRepository.findById(product.getId()).get();
        assertThat(refreshed.getStockQuantity()).isEqualTo(150);
    }

    // ── HELPER ────────────────────────────────────────────────────────────

    private Product buildProduct(String sku, String name, String category, String brand,
                                  double price, int stock, ProductStatus status) {
        return Product.builder()
            .sku(sku).name(name).category(category).brand(brand)
            .price(BigDecimal.valueOf(price))
            .stockQuantity(stock)
            .status(status)
            .build();
    }
}
