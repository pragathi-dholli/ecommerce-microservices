package com.ecommerce.order.controller;

import com.ecommerce.order.client.PaymentServiceClient;
import com.ecommerce.order.client.ProductServiceClient;
import com.ecommerce.order.dto.ExternalServiceDto;
import com.ecommerce.order.dto.OrderDto;
import com.ecommerce.order.model.*;
import com.ecommerce.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("OrderController Integration Tests")
class OrderControllerIntegrationTest {

    @Autowired private MockMvc       mockMvc;
    @Autowired private ObjectMapper  objectMapper;
    @Autowired private OrderRepository orderRepository;

    // Mock downstream services — we test Order Service in isolation
    @MockBean private ProductServiceClient productServiceClient;
    @MockBean private PaymentServiceClient paymentServiceClient;

    private OrderController.CreateOrderRequest validCreateRequest;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        // Default: product available, payment succeeds
        given(productServiceClient.checkInventory(any()))
            .willReturn(ExternalServiceDto.InventoryCheckResponse.builder()
                .sku("ELEC-001").available(true).availableQuantity(50)
                .unitPrice(new BigDecimal("99.99")).productName("Test Headphones")
                .build());

        given(paymentServiceClient.initiatePayment(any()))
            .willReturn(ExternalServiceDto.PaymentResponse.builder()
                .paymentId("PAY-TEST-001").status("COMPLETED")
                .amount(new BigDecimal("219.97")).build());

        validCreateRequest = buildCreateRequest("ELEC-001", 2);
    }

    // ── CREATE ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/orders — 201 Created on happy path")
    void shouldCreateOrderSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validCreateRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderNumber").value(startsWith("ORD-")))
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.customerId").value("CUST-001"))
            .andExpect(jsonPath("$.paymentId").value("PAY-TEST-001"))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].productSku").value("ELEC-001"))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.subtotal").value(199.98))
            .andExpect(jsonPath("$.shippingCost").value(0.0))   // > £100 → free shipping
            .andExpect(jsonPath("$.taxAmount").isNumber())
            .andExpect(jsonPath("$.totalAmount").isNumber());
    }

    @Test
    @DisplayName("POST /api/v1/orders — 422 when product out of stock")
    void shouldReturn422WhenOutOfStock() throws Exception {
        given(productServiceClient.checkInventory(any()))
            .willReturn(ExternalServiceDto.InventoryCheckResponse.builder()
                .sku("ELEC-001").available(false).availableQuantity(0).build());

        mockMvc.perform(post("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validCreateRequest)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value(containsString("ELEC-001")));
    }

    @Test
    @DisplayName("POST /api/v1/orders — 400 on missing required fields")
    void shouldReturn400OnInvalidRequest() throws Exception {
        OrderController.CreateOrderRequest invalid = new OrderController.CreateOrderRequest();
        // order field intentionally null

        mockMvc.perform(post("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/orders — order created as PENDING on async payment")
    void shouldCreatePendingOrderOnAsyncPayment() throws Exception {
        given(paymentServiceClient.initiatePayment(any()))
            .willReturn(ExternalServiceDto.PaymentResponse.builder()
                .paymentId("PAY-ASYNC").status("PENDING").build());

        mockMvc.perform(post("/api/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(validCreateRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.paymentStatus").value("PENDING"));
    }

    // ── READ ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/orders/{id} — 200 with full response")
    void shouldGetOrderById() throws Exception {
        Order saved = saveOrder(OrderStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/orders/{id}", saved.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(saved.getId()))
            .andExpect(jsonPath("$.orderNumber").value(saved.getOrderNumber()))
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("GET /api/v1/orders/{id} — 404 when not found")
    void shouldReturn404WhenOrderNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/orders/99999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/v1/orders/number/{orderNumber} — 200 with items")
    void shouldGetOrderByNumber() throws Exception {
        Order saved = saveOrder(OrderStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/orders/number/{number}", saved.getOrderNumber()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNumber").value(saved.getOrderNumber()))
            .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/v1/orders/customer/{customerId} — paginated results")
    void shouldGetOrdersByCustomer() throws Exception {
        saveOrder(OrderStatus.CONFIRMED);
        saveOrder(OrderStatus.PENDING);

        mockMvc.perform(get("/api/v1/orders/customer/CUST-001")
            .param("page", "0").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/orders — filter by status")
    void shouldFilterOrdersByStatus() throws Exception {
        saveOrder(OrderStatus.CONFIRMED);
        saveOrder(OrderStatus.PENDING);
        saveOrder(OrderStatus.SHIPPED);

        mockMvc.perform(get("/api/v1/orders")
            .param("status", "CONFIRMED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].status", everyItem(equalTo("CONFIRMED"))));
    }

    // ── CANCEL ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/orders/{id}/cancel — 200 on PENDING order")
    void shouldCancelPendingOrder() throws Exception {
        Order saved = saveOrder(OrderStatus.PENDING);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", saved.getId())
            .param("reason", "Changed my mind"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        then(productServiceClient).should().restoreStock(eq("ELEC-001"), eq(2));
    }

    @Test
    @DisplayName("POST /api/v1/orders/{id}/cancel — 409 on SHIPPED order")
    void shouldReturn409WhenCancellingShippedOrder() throws Exception {
        Order saved = saveOrder(OrderStatus.SHIPPED);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", saved.getId()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(containsString("SHIPPED")));
    }

    // ── STATUS UPDATE ──────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/v1/orders/{id}/status — valid transition CONFIRMED → PROCESSING")
    void shouldUpdateOrderStatus() throws Exception {
        Order saved = saveOrder(OrderStatus.CONFIRMED);

        OrderDto.StatusUpdateRequest req = OrderDto.StatusUpdateRequest.builder()
            .status(OrderStatus.PROCESSING).build();

        mockMvc.perform(patch("/api/v1/orders/{id}/status", saved.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("PATCH /api/v1/orders/{id}/status — 409 on invalid transition")
    void shouldReturn409OnInvalidTransition() throws Exception {
        Order saved = saveOrder(OrderStatus.PENDING);

        OrderDto.StatusUpdateRequest req = OrderDto.StatusUpdateRequest.builder()
            .status(OrderStatus.DELIVERED).build();

        mockMvc.perform(patch("/api/v1/orders/{id}/status", saved.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isConflict());
    }

    // ── PAYMENT CALLBACK ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/orders/payment-callback — COMPLETED confirms order")
    void shouldConfirmOrderOnPaymentCallback() throws Exception {
        Order saved = saveOrder(OrderStatus.PENDING);

        OrderDto.PaymentCallbackRequest callback = OrderDto.PaymentCallbackRequest.builder()
            .orderNumber(saved.getOrderNumber())
            .paymentId("PAY-CALLBACK-001")
            .paymentStatus(PaymentStatus.COMPLETED)
            .build();

        mockMvc.perform(post("/api/v1/orders/payment-callback")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(callback)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.paymentId").value("PAY-CALLBACK-001"));
    }

    @Test
    @DisplayName("POST /api/v1/orders/payment-callback — FAILED cancels order and restores stock")
    void shouldCancelOrderOnFailedPaymentCallback() throws Exception {
        Order saved = saveOrder(OrderStatus.PENDING);

        OrderDto.PaymentCallbackRequest callback = OrderDto.PaymentCallbackRequest.builder()
            .orderNumber(saved.getOrderNumber())
            .paymentId("PAY-FAIL-001")
            .paymentStatus(PaymentStatus.FAILED)
            .failureReason("Card declined")
            .build();

        mockMvc.perform(post("/api/v1/orders/payment-callback")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(callback)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.paymentStatus").value("FAILED"));

        then(productServiceClient).should().restoreStock(eq("ELEC-001"), eq(2));
    }

    // ── HELPERS ────────────────────────────────────────────────────────────

    private Order saveOrder(OrderStatus status) {
        Order order = Order.builder()
            .customerId("CUST-001")
            .customerEmail("test@example.com")
            .subtotal(new BigDecimal("199.98"))
            .shippingCost(BigDecimal.ZERO)
            .taxAmount(new BigDecimal("20.00"))
            .totalAmount(new BigDecimal("219.98"))
            .status(status)
            .shippingAddress(ShippingAddress.builder()
                .fullName("Jane Doe").addressLine1("123 Main St")
                .city("Austin").state("TX").postalCode("78701").country("US")
                .build())
            .build();

        OrderItem item = OrderItem.builder()
            .productSku("ELEC-001").productName("Test Headphones")
            .quantity(2).unitPrice(new BigDecimal("99.99"))
            .lineTotal(new BigDecimal("199.98"))
            .build();
        order.addItem(item);
        return orderRepository.save(order);
    }

    private OrderController.CreateOrderRequest buildCreateRequest(String sku, int qty) {
        OrderController.CreateOrderRequest req = new OrderController.CreateOrderRequest();
        req.setOrder(OrderDto.CreateRequest.builder()
            .customerId("CUST-001")
            .customerEmail("test@example.com")
            .items(List.of(OrderDto.OrderItemRequest.builder()
                .productSku(sku).quantity(qty).build()))
            .shippingAddress(OrderDto.ShippingAddressDto.builder()
                .fullName("Jane Doe").addressLine1("123 Main St")
                .city("Austin").state("TX").postalCode("78701").country("US")
                .build())
            .build());
        req.setPaymentMethod(ExternalServiceDto.PaymentMethodDto.builder()
            .type("CARD").token("tok_test_123").build());
        return req;
    }
}
