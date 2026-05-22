package com.ecommerce.payment.controller;

import com.ecommerce.payment.client.OrderServiceClient;
import com.ecommerce.payment.config.PaymentGatewayClient;
import com.ecommerce.payment.dto.PaymentDto;
import com.ecommerce.payment.model.Payment;
import com.ecommerce.payment.model.PaymentMethod;
import com.ecommerce.payment.model.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
@DisplayName("PaymentController Integration Tests")
class PaymentControllerIntegrationTest {

    @Autowired private MockMvc          mockMvc;
    @Autowired private ObjectMapper     objectMapper;
    @Autowired private PaymentRepository paymentRepository;

    @MockBean private PaymentGatewayClient gatewayClient;
    @MockBean private OrderServiceClient   orderServiceClient;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();

        // Default: gateway succeeds
        given(gatewayClient.charge(any(), any(), any()))
            .willReturn(PaymentDto.GatewayResponse.builder()
                .success(true).transactionId("gw_inttest_001").build());
    }

    // ── INITIATE PAYMENT ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/payments — 201 COMPLETED on successful charge")
    void shouldInitiatePaymentSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(buildRequest("ORD-001"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.paymentId").value(startsWith("PAY-")))
            .andExpect(jsonPath("$.orderNumber").value("ORD-001"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.gatewayReference").value("gw_inttest_001"))
            .andExpect(jsonPath("$.amount").value(199.99))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.method").value("CARD"))
            .andExpect(jsonPath("$.attemptCount").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/payments — 201 FAILED when gateway declines")
    void shouldReturnFailedOnGatewayDecline() throws Exception {
        given(gatewayClient.charge(any(), any(), any()))
            .willReturn(PaymentDto.GatewayResponse.builder()
                .success(false)
                .declineCode("insufficient_funds")
                .message("Your card has insufficient funds")
                .build());

        mockMvc.perform(post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(buildRequest("ORD-002"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.failureReason").value(containsString("insufficient funds")));
    }

    @Test
    @DisplayName("POST /api/v1/payments — 409 Conflict on duplicate order number")
    void shouldReturn409OnDuplicateOrderNumber() throws Exception {
        // Create first payment
        mockMvc.perform(post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(buildRequest("ORD-DUP"))))
            .andExpect(status().isCreated());

        // Attempt duplicate
        mockMvc.perform(post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(buildRequest("ORD-DUP"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(containsString("ORD-DUP")));
    }

    @Test
    @DisplayName("POST /api/v1/payments — 400 on missing required fields")
    void shouldReturn400OnInvalidRequest() throws Exception {
        PaymentDto.PaymentRequest invalid = PaymentDto.PaymentRequest.builder()
            .orderNumber("") // blank
            .amount(new BigDecimal("-1.00")) // negative
            .build();

        mockMvc.perform(post("/api/v1/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors").exists());
    }

    // ── READ ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/payments/{paymentId} — 200 OK")
    void shouldGetPaymentById() throws Exception {
        Payment saved = savePayment("ORD-GET-001", PaymentStatus.COMPLETED);

        mockMvc.perform(get("/api/v1/payments/{paymentId}", saved.getPaymentId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentId").value(saved.getPaymentId()))
            .andExpect(jsonPath("$.orderNumber").value("ORD-GET-001"))
            .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{paymentId} — 404 Not Found")
    void shouldReturn404WhenPaymentNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/payments/PAY-UNKNOWN"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET /api/v1/payments/order/{orderNumber} — 200 OK")
    void shouldGetByOrderNumber() throws Exception {
        savePayment("ORD-BYNUM-001", PaymentStatus.COMPLETED);

        mockMvc.perform(get("/api/v1/payments/order/ORD-BYNUM-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNumber").value("ORD-BYNUM-001"));
    }

    @Test
    @DisplayName("GET /api/v1/payments — paginated list")
    void shouldGetAllPaymentsPaginated() throws Exception {
        savePayment("ORD-LIST-001", PaymentStatus.COMPLETED);
        savePayment("ORD-LIST-002", PaymentStatus.FAILED);
        savePayment("ORD-LIST-003", PaymentStatus.PENDING);

        mockMvc.perform(get("/api/v1/payments")
            .param("page", "0").param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(3)))
            .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/payments — filter by status")
    void shouldFilterPaymentsByStatus() throws Exception {
        savePayment("ORD-FILT-001", PaymentStatus.COMPLETED);
        savePayment("ORD-FILT-002", PaymentStatus.FAILED);
        savePayment("ORD-FILT-003", PaymentStatus.COMPLETED);

        mockMvc.perform(get("/api/v1/payments")
            .param("status", "COMPLETED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].status", everyItem(equalTo("COMPLETED"))))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/payments/customer/{customerId} — paginated results")
    void shouldGetPaymentsByCustomer() throws Exception {
        savePayment("ORD-CUST-001", PaymentStatus.COMPLETED);
        savePayment("ORD-CUST-002", PaymentStatus.COMPLETED);

        mockMvc.perform(get("/api/v1/payments/customer/CUST-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(2)));
    }

    // ── REFUND ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/payments/{paymentId}/refund — 200 REFUNDED on full refund")
    void shouldProcessFullRefund() throws Exception {
        Payment saved = savePayment("ORD-REF-001", PaymentStatus.COMPLETED);

        given(gatewayClient.refund(any(), any()))
            .willReturn(PaymentDto.GatewayResponse.builder()
                .success(true).transactionId("rf_test_001").build());

        PaymentDto.RefundRequest request = PaymentDto.RefundRequest.builder()
            .reason("Customer request").build();

        mockMvc.perform(post("/api/v1/payments/{paymentId}/refund", saved.getPaymentId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"))
            .andExpect(jsonPath("$.refundReference").value("rf_test_001"))
            .andExpect(jsonPath("$.refundAmount").value(199.99));
    }

    @Test
    @DisplayName("POST /api/v1/payments/{paymentId}/refund — 200 PARTIALLY_REFUNDED on partial refund")
    void shouldProcessPartialRefund() throws Exception {
        Payment saved = savePayment("ORD-PREF-001", PaymentStatus.COMPLETED);

        given(gatewayClient.refund(any(), any()))
            .willReturn(PaymentDto.GatewayResponse.builder()
                .success(true).transactionId("rf_partial_001").build());

        PaymentDto.RefundRequest request = PaymentDto.RefundRequest.builder()
            .amount(new BigDecimal("50.00")).reason("Partial return").build();

        mockMvc.perform(post("/api/v1/payments/{paymentId}/refund", saved.getPaymentId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"))
            .andExpect(jsonPath("$.refundAmount").value(50.00));
    }

    @Test
    @DisplayName("POST /api/v1/payments/{paymentId}/refund — 409 when payment is FAILED")
    void shouldReturn409WhenRefundingFailedPayment() throws Exception {
        Payment saved = savePayment("ORD-NREF-001", PaymentStatus.FAILED);

        PaymentDto.RefundRequest request = PaymentDto.RefundRequest.builder()
            .reason("Test").build();

        mockMvc.perform(post("/api/v1/payments/{paymentId}/refund", saved.getPaymentId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(containsString("cannot be refunded")));
    }

    @Test
    @DisplayName("POST /api/v1/payments/order/{orderNumber}/refund — 200 OK")
    void shouldRefundByOrderNumber() throws Exception {
        savePayment("ORD-OREF-001", PaymentStatus.COMPLETED);

        given(gatewayClient.refund(any(), any()))
            .willReturn(PaymentDto.GatewayResponse.builder()
                .success(true).transactionId("rf_order_001").build());

        mockMvc.perform(post("/api/v1/payments/order/ORD-OREF-001/refund"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    // ── HELPERS ───────────────────────────────────────────────────────────

    private Payment savePayment(String orderNumber, PaymentStatus status) {
        return paymentRepository.save(Payment.builder()
            .paymentId("PAY-" + orderNumber.substring(4))
            .orderNumber(orderNumber)
            .customerId("CUST-001")
            .customerEmail("test@example.com")
            .amount(new BigDecimal("199.99"))
            .currency("USD")
            .method(PaymentMethod.CARD)
            .paymentToken("tok_test")
            .gatewayReference(status == PaymentStatus.COMPLETED ? "gw_ref_" + orderNumber : null)
            .status(status)
            .attemptCount(1)
            .build());
    }

    private PaymentDto.PaymentRequest buildRequest(String orderNumber) {
        return PaymentDto.PaymentRequest.builder()
            .orderNumber(orderNumber)
            .amount(new BigDecimal("199.99"))
            .currency("USD")
            .customerId("CUST-001")
            .customerEmail("test@example.com")
            .paymentMethod(PaymentDto.PaymentMethodDto.builder()
                .type("CARD").token("tok_success_001").build())
            .build();
    }
}
