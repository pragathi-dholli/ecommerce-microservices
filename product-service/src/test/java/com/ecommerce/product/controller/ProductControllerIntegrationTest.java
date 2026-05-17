package com.ecommerce.product.controller;

import com.ecommerce.product.dto.ProductDto;
import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.ProductStatus;
import com.ecommerce.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ProductController Integration Tests")
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    private static Long createdProductId;

    @BeforeEach
    void cleanUp() {
        productRepository.deleteAll();
    }

    // ── CREATE ─────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/products — 201 Created")
    void shouldCreateProduct() throws Exception {
        ProductDto.CreateRequest request = ProductDto.CreateRequest.builder()
            .name("Wireless Headphones")
            .sku("ELEC-WH-001")
            .price(new BigDecimal("199.99"))
            .stockQuantity(100)
            .category("Electronics")
            .brand("SoundMax")
            .build();

        ResultActions result = mockMvc.perform(post("/api/v1/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)));

        result.andExpect(status().isCreated())
              .andExpect(jsonPath("$.id").exists())
              .andExpect(jsonPath("$.sku").value("ELEC-WH-001"))
              .andExpect(jsonPath("$.name").value("Wireless Headphones"))
              .andExpect(jsonPath("$.price").value(199.99))
              .andExpect(jsonPath("$.stockQuantity").value(100))
              .andExpect(jsonPath("$.status").value("ACTIVE"))
              .andExpect(jsonPath("$.inStock").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/products — 400 Bad Request on missing required fields")
    void shouldReturn400OnInvalidRequest() throws Exception {
        ProductDto.CreateRequest invalid = ProductDto.CreateRequest.builder()
            .name("") // blank name
            .sku("bad sku!") // invalid pattern
            .price(new BigDecimal("-5.00")) // negative price
            .stockQuantity(-1) // negative stock
            .category("Electronics")
            .build();

        mockMvc.perform(post("/api/v1/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors").exists());
    }

    @Test
    @DisplayName("POST /api/v1/products — 409 Conflict on duplicate SKU")
    void shouldReturn409OnDuplicateSku() throws Exception {
        // Create once
        createSampleProduct("ELEC-DUP-001");

        // Try creating again with same SKU
        ProductDto.CreateRequest request = ProductDto.CreateRequest.builder()
            .name("Another Product")
            .sku("ELEC-DUP-001")
            .price(new BigDecimal("50.00"))
            .stockQuantity(10)
            .category("Electronics")
            .build();

        mockMvc.perform(post("/api/v1/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(containsString("ELEC-DUP-001")));
    }

    // ── READ ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/products/{id} — 200 OK")
    void shouldGetProductById() throws Exception {
        Product saved = createSampleProduct("ELEC-GET-001");

        mockMvc.perform(get("/api/v1/products/{id}", saved.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(saved.getId()))
            .andExpect(jsonPath("$.sku").value("ELEC-GET-001"));
    }

    @Test
    @DisplayName("GET /api/v1/products/{id} — 404 Not Found")
    void shouldReturn404WhenProductNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/products/99999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/v1/products/sku/{sku} — 200 OK")
    void shouldGetProductBySku() throws Exception {
        createSampleProduct("ELEC-SKU-001");

        mockMvc.perform(get("/api/v1/products/sku/ELEC-SKU-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sku").value("ELEC-SKU-001"));
    }

    @Test
    @DisplayName("GET /api/v1/products — paginated list")
    void shouldReturnPaginatedProducts() throws Exception {
        createSampleProduct("ELEC-PAG-001");
        createSampleProduct("ELEC-PAG-002");
        createSampleProduct("ELEC-PAG-003");

        mockMvc.perform(get("/api/v1/products")
            .param("page", "0")
            .param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/products/search — filter by category")
    void shouldSearchByCategory() throws Exception {
        createSampleProduct("ELEC-SRCH-001");
        createSampleProductWithCategory("FURN-SRCH-001", "Furniture");

        mockMvc.perform(get("/api/v1/products/search")
            .param("category", "Electronics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].category", everyItem(equalTo("Electronics"))));
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/v1/products/{id} — 200 OK partial update")
    void shouldUpdateProduct() throws Exception {
        Product saved = createSampleProduct("ELEC-UPD-001");

        ProductDto.UpdateRequest update = ProductDto.UpdateRequest.builder()
            .price(new BigDecimal("249.99"))
            .build();

        mockMvc.perform(patch("/api/v1/products/{id}", saved.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(update)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.price").value(249.99))
            .andExpect(jsonPath("$.name").value(saved.getName())); // Unchanged
    }

    @Test
    @DisplayName("PATCH /api/v1/products/{id}/deactivate — 200 OK")
    void shouldDeactivateProduct() throws Exception {
        Product saved = createSampleProduct("ELEC-DEACT-001");

        mockMvc.perform(patch("/api/v1/products/{id}/deactivate", saved.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    // ── INVENTORY ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/products/inventory/check — available when stock sufficient")
    void shouldReturnAvailableForSufficientStock() throws Exception {
        createSampleProduct("ELEC-INV-001");

        ProductDto.InventoryCheckRequest checkRequest = ProductDto.InventoryCheckRequest.builder()
            .sku("ELEC-INV-001")
            .requestedQuantity(5)
            .build();

        mockMvc.perform(post("/api/v1/products/inventory/check")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(checkRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.availableQuantity").value(50))
            .andExpect(jsonPath("$.unitPrice").value(99.99));
    }

    @Test
    @DisplayName("POST /api/v1/products/inventory/check — unavailable when stock insufficient")
    void shouldReturnUnavailableForInsufficientStock() throws Exception {
        createSampleProduct("ELEC-INV-002");

        ProductDto.InventoryCheckRequest checkRequest = ProductDto.InventoryCheckRequest.builder()
            .sku("ELEC-INV-002")
            .requestedQuantity(999)
            .build();

        mockMvc.perform(post("/api/v1/products/inventory/check")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(checkRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/products/{sku}/deduct-stock — 204 No Content")
    void shouldDeductStock() throws Exception {
        createSampleProduct("ELEC-DED-001");

        mockMvc.perform(post("/api/v1/products/sku/ELEC-DED-001/deduct-stock")
            .param("quantity", "10"))
            .andExpect(status().isNoContent());

        // Verify stock was deducted
        mockMvc.perform(get("/api/v1/products/sku/ELEC-DED-001"))
            .andExpect(jsonPath("$.stockQuantity").value(40));
    }

    @Test
    @DisplayName("POST /api/v1/products/{sku}/deduct-stock — 422 on insufficient stock")
    void shouldReturn422OnInsufficientStock() throws Exception {
        createSampleProduct("ELEC-DED-002");

        mockMvc.perform(post("/api/v1/products/sku/ELEC-DED-002/deduct-stock")
            .param("quantity", "999"))
            .andExpect(status().isUnprocessableEntity());
    }

    // ── DELETE ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/v1/products/{id} — 204 No Content when stock=0")
    void shouldDeleteProductWithNoStock() throws Exception {
        Product saved = createSampleProductWithStock("ELEC-DEL-001", 0);

        mockMvc.perform(delete("/api/v1/products/{id}", saved.getId()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/{id}", saved.getId()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/products/{id} — 400 when product has stock")
    void shouldRejectDeleteWhenStockExists() throws Exception {
        Product saved = createSampleProduct("ELEC-DEL-002");

        mockMvc.perform(delete("/api/v1/products/{id}", saved.getId()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("existing stock")));
    }

    // ── HELPERS ────────────────────────────────────────────────────────────

    private Product createSampleProduct(String sku) {
        return productRepository.save(Product.builder()
            .name("Test Product " + sku)
            .sku(sku)
            .price(new BigDecimal("99.99"))
            .stockQuantity(50)
            .category("Electronics")
            .brand("TestBrand")
            .status(ProductStatus.ACTIVE)
            .build());
    }

    private Product createSampleProductWithCategory(String sku, String category) {
        return productRepository.save(Product.builder()
            .name("Test Product " + sku)
            .sku(sku)
            .price(new BigDecimal("99.99"))
            .stockQuantity(50)
            .category(category)
            .status(ProductStatus.ACTIVE)
            .build());
    }

    private Product createSampleProductWithStock(String sku, int stock) {
        return productRepository.save(Product.builder()
            .name("Test Product " + sku)
            .sku(sku)
            .price(new BigDecimal("99.99"))
            .stockQuantity(stock)
            .category("Electronics")
            .status(ProductStatus.ACTIVE)
            .build());
    }
}
