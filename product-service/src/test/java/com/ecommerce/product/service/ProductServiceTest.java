package com.ecommerce.product.service;

import com.ecommerce.product.dto.ProductDto;
import com.ecommerce.product.exception.DuplicateSkuException;
import com.ecommerce.product.exception.InsufficientStockException;
import com.ecommerce.product.exception.InvalidProductOperationException;
import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.ProductStatus;
import com.ecommerce.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Unit Tests")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct;
    private ProductDto.CreateRequest createRequest;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
            .id(1L)
            .name("Test Headphones")
            .description("Great headphones")
            .sku("ELEC-001")
            .price(new BigDecimal("99.99"))
            .stockQuantity(50)
            .category("Electronics")
            .brand("TestBrand")
            .status(ProductStatus.ACTIVE)
            .version(0L)
            .build();

        createRequest = ProductDto.CreateRequest.builder()
            .name("Test Headphones")
            .description("Great headphones")
            .sku("ELEC-001")
            .price(new BigDecimal("99.99"))
            .stockQuantity(50)
            .category("Electronics")
            .brand("TestBrand")
            .build();
    }

    // ── CREATE ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createProduct()")
    class CreateProduct {

        @Test
        @DisplayName("should create product successfully when SKU is unique")
        void shouldCreateProductSuccessfully() {
            given(productRepository.existsBySku("ELEC-001")).willReturn(false);
            given(productRepository.save(any(Product.class))).willReturn(sampleProduct);

            ProductDto.Response response = productService.createProduct(createRequest);

            assertThat(response).isNotNull();
            assertThat(response.getSku()).isEqualTo("ELEC-001");
            assertThat(response.getName()).isEqualTo("Test Headphones");
            assertThat(response.getPrice()).isEqualByComparingTo("99.99");
            assertThat(response.getStatus()).isEqualTo(ProductStatus.ACTIVE);

            then(productRepository).should().existsBySku("ELEC-001");
            then(productRepository).should().save(any(Product.class));
        }

        @Test
        @DisplayName("should throw DuplicateSkuException when SKU already exists")
        void shouldThrowWhenSkuExists() {
            given(productRepository.existsBySku("ELEC-001")).willReturn(true);

            assertThatThrownBy(() -> productService.createProduct(createRequest))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("ELEC-001");

            then(productRepository).should(never()).save(any());
        }
    }

    // ── GET BY ID ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProductById()")
    class GetProductById {

        @Test
        @DisplayName("should return product when found")
        void shouldReturnProductWhenFound() {
            given(productRepository.findById(1L)).willReturn(Optional.of(sampleProduct));

            ProductDto.Response response = productService.getProductById(1L);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getSku()).isEqualTo("ELEC-001");
        }

        @Test
        @DisplayName("should throw ProductNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            given(productRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");
        }
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateProduct()")
    class UpdateProduct {

        @Test
        @DisplayName("should update only provided fields (partial update)")
        void shouldUpdateOnlyProvidedFields() {
            ProductDto.UpdateRequest updateRequest = ProductDto.UpdateRequest.builder()
                .price(new BigDecimal("149.99"))
                .build();

            given(productRepository.findById(1L)).willReturn(Optional.of(sampleProduct));
            given(productRepository.save(any(Product.class))).willReturn(sampleProduct);

            productService.updateProduct(1L, updateRequest);

            // Price should have been updated on the entity
            assertThat(sampleProduct.getPrice()).isEqualByComparingTo("149.99");
            // Name unchanged
            assertThat(sampleProduct.getName()).isEqualTo("Test Headphones");
        }

        @Test
        @DisplayName("should throw when product not found")
        void shouldThrowWhenProductNotFound() {
            given(productRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.updateProduct(99L, ProductDto.UpdateRequest.builder().build()))
                .isInstanceOf(ProductNotFoundException.class);
        }
    }

    // ── STOCK MANAGEMENT ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Stock Management")
    class StockManagement {

        @Test
        @DisplayName("checkAndReserveStock: should return available=true when stock is sufficient")
        void shouldReturnAvailableWhenStockSufficient() {
            given(productRepository.findBySku("ELEC-001")).willReturn(Optional.of(sampleProduct));

            ProductDto.InventoryCheckResponse response = productService.checkAndReserveStock(
                ProductDto.InventoryCheckRequest.builder()
                    .sku("ELEC-001")
                    .requestedQuantity(10)
                    .build()
            );

            assertThat(response.isAvailable()).isTrue();
            assertThat(response.getAvailableQuantity()).isEqualTo(50);
            assertThat(response.getUnitPrice()).isEqualByComparingTo("99.99");
        }

        @Test
        @DisplayName("checkAndReserveStock: should return available=false when stock insufficient")
        void shouldReturnUnavailableWhenStockInsufficient() {
            given(productRepository.findBySku("ELEC-001")).willReturn(Optional.of(sampleProduct));

            ProductDto.InventoryCheckResponse response = productService.checkAndReserveStock(
                ProductDto.InventoryCheckRequest.builder()
                    .sku("ELEC-001")
                    .requestedQuantity(100)
                    .build()
            );

            assertThat(response.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("adjustStock: should increase stock on positive quantity")
        void shouldIncreaseStockOnPositiveAdjustment() {
            given(productRepository.findById(1L)).willReturn(Optional.of(sampleProduct));
            given(productRepository.save(any())).willReturn(sampleProduct);

            productService.adjustStock(1L, ProductDto.StockAdjustmentRequest.builder()
                .quantity(20)
                .reason("MANUAL_CORRECTION")
                .build());

            assertThat(sampleProduct.getStockQuantity()).isEqualTo(70);
        }

        @Test
        @DisplayName("adjustStock: should throw InsufficientStockException on overdraft")
        void shouldThrowOnStockOverdraft() {
            given(productRepository.findById(1L)).willReturn(Optional.of(sampleProduct));

            assertThatThrownBy(() -> productService.adjustStock(1L,
                ProductDto.StockAdjustmentRequest.builder()
                    .quantity(-100)
                    .reason("ORDER_PLACED")
                    .build()))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("ELEC-001");
        }

        @Test
        @DisplayName("deductStock: should throw when optimistic lock fails (concurrent update)")
        void shouldThrowWhenDecrementReturnsZero() {
            given(productRepository.findBySku("ELEC-001")).willReturn(Optional.of(sampleProduct));
            given(productRepository.decrementStock(1L, 10)).willReturn(0); // Simulate race condition

            assertThatThrownBy(() -> productService.deductStock("ELEC-001", 10))
                .isInstanceOf(InsufficientStockException.class);
        }
    }

    // ── DELETE ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteProduct()")
    class DeleteProduct {

        @Test
        @DisplayName("should delete product when stock is zero")
        void shouldDeleteWhenNoStock() {
            sampleProduct.setStockQuantity(0);
            given(productRepository.findById(1L)).willReturn(Optional.of(sampleProduct));

            assertThatCode(() -> productService.deleteProduct(1L))
                .doesNotThrowAnyException();

            then(productRepository).should().delete(sampleProduct);
        }

        @Test
        @DisplayName("should throw InvalidProductOperationException when stock > 0")
        void shouldThrowWhenStockExists() {
            given(productRepository.findById(1L)).willReturn(Optional.of(sampleProduct));

            assertThatThrownBy(() -> productService.deleteProduct(1L))
                .isInstanceOf(InvalidProductOperationException.class)
                .hasMessageContaining("existing stock");

            then(productRepository).should(never()).delete(any());
        }
    }

    // ── DEACTIVATE ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("deactivateProduct: should set status to INACTIVE")
    void shouldDeactivateProduct() {
        given(productRepository.findById(1L)).willReturn(Optional.of(sampleProduct));
        given(productRepository.save(any())).willReturn(sampleProduct);

        productService.deactivateProduct(1L);

        assertThat(sampleProduct.getStatus()).isEqualTo(ProductStatus.INACTIVE);
    }
}
