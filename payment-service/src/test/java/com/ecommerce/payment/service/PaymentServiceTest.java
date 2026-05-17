package com.ecommerce.payment.service;

import com.ecommerce.payment.client.OrderServiceClient;
import com.ecommerce.payment.config.PaymentGatewayClient;
import com.ecommerce.payment.dto.PaymentDto;
import com.ecommerce.payment.exception.DuplicatePaymentException;
import com.ecommerce.payment.exception.InvalidPaymentOperationException;
import com.ecommerce.payment.exception.PaymentNotFoundException;
import com.ecommerce.payment.model.Payment;
import com.ecommerce.payment.model.PaymentMethod;
import com.ecommerce.payment.model.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock private PaymentRepository    paymentRepository;
    @Mock private PaymentGatewayClient gatewayClient;
    @Mock private OrderServiceClient   orderServiceClient;

    @InjectMocks private PaymentService paymentService;

    private PaymentDto.PaymentRequest validRequest;
    private Payment savedPayment;

    @BeforeEach
    void setUp() {
        validRequest = PaymentDto.PaymentRequest.builder()
            .orderNumber("ORD-TEST001")
            .amount(new BigDecimal("199.99"))
            .currency("USD")
            .customerId("CUST-001")
            .customerEmail("test@example.com")
            .paymentMethod(PaymentDto.PaymentMethodDto.builder()
                .type("CARD").token("tok_success_001").build())
            .build();

        savedPayment = Payment.builder()
            .id(1L)
            .paymentId("PAY-ABC12345")
            .orderNumber("ORD-TEST001")
            .customerId("CUST-001")
            .customerEmail("test@example.com")
            .amount(new BigDecimal("199.99"))
            .currency("USD")
            .method(PaymentMethod.CARD)
            .paymentToken("tok_success_001")
            .status(PaymentStatus.COMPLETED)
            .gatewayReference("gw_abc123")
            .attemptCount(1)
            .version(0L)
            .build();
    }

    // ── INITIATE PAYMENT ───────────────────────────────────────────────────

    @Nested
    @DisplayName("initiatePayment()")
    class InitiatePayment {

        @Test
        @DisplayName("should return COMPLETED on successful gateway charge")
        void shouldCompleteOnSuccessfulCharge() {
            given(paymentRepository.existsByOrderNumber("ORD-TEST001")).willReturn(false);
            given(paymentRepository.save(any())).willReturn(savedPayment);
            given(gatewayClient.charge(any(), any(), any()))
                .willReturn(PaymentDto.GatewayResponse.builder()
                    .success(true).transactionId("gw_abc123").build());

            PaymentDto.PaymentResponse response = paymentService.initiatePayment(validRequest);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(response.getPaymentId()).isEqualTo("PAY-ABC12345");
            then(orderServiceClient).should().notifyPaymentResult(any());
        }

        @Test
        @DisplayName("should return FAILED when gateway declines")
        void shouldFailOnGatewayDecline() {
            Payment failedPayment = savedPayment.toBuilder()
                .status(PaymentStatus.FAILED)
                .failureReason("Your card has insufficient funds")
                .gatewayReference(null)
                .build();

            given(paymentRepository.existsByOrderNumber("ORD-TEST001")).willReturn(false);
            given(paymentRepository.save(any())).willReturn(failedPayment);
            given(gatewayClient.charge(any(), any(), any()))
                .willReturn(PaymentDto.GatewayResponse.builder()
                    .success(false)
                    .declineCode("insufficient_funds")
                    .message("Your card has insufficient funds")
                    .build());

            PaymentDto.PaymentResponse response = paymentService.initiatePayment(validRequest);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(response.getFailureReason()).contains("insufficient funds");
            then(orderServiceClient).should().notifyPaymentResult(any());
        }

        @Test
        @DisplayName("should return FAILED when gateway throws exception")
        void shouldFailOnGatewayException() {
            Payment failedPayment = savedPayment.toBuilder()
                .status(PaymentStatus.FAILED).build();

            given(paymentRepository.existsByOrderNumber("ORD-TEST001")).willReturn(false);
            given(paymentRepository.save(any())).willReturn(failedPayment);
            given(gatewayClient.charge(any(), any(), any()))
                .willThrow(new RuntimeException("Connection timeout"));

            PaymentDto.PaymentResponse response = paymentService.initiatePayment(validRequest);

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("should throw DuplicatePaymentException on duplicate order number")
        void shouldRejectDuplicatePayment() {
            given(paymentRepository.existsByOrderNumber("ORD-TEST001")).willReturn(true);

            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
                .isInstanceOf(DuplicatePaymentException.class)
                .hasMessageContaining("ORD-TEST001");

            then(gatewayClient).should(never()).charge(any(), any(), any());
            then(paymentRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("should throw InvalidPaymentOperationException for unknown payment method")
        void shouldThrowOnUnknownPaymentMethod() {
            validRequest.getPaymentMethod().setType("CRYPTO");
            given(paymentRepository.existsByOrderNumber("ORD-TEST001")).willReturn(false);
            given(paymentRepository.save(any())).willReturn(savedPayment);

            assertThatThrownBy(() -> paymentService.initiatePayment(validRequest))
                .isInstanceOf(InvalidPaymentOperationException.class)
                .hasMessageContaining("CRYPTO");
        }

        @Test
        @DisplayName("should still save payment if Order Service callback fails")
        void shouldSavePaymentEvenIfCallbackFails() {
            given(paymentRepository.existsByOrderNumber("ORD-TEST001")).willReturn(false);
            given(paymentRepository.save(any())).willReturn(savedPayment);
            given(gatewayClient.charge(any(), any(), any()))
                .willReturn(PaymentDto.GatewayResponse.builder()
                    .success(true).transactionId("gw_abc123").build());
            willThrow(new RuntimeException("Order Service down"))
                .given(orderServiceClient).notifyPaymentResult(any());

            // Should NOT throw — callback failure is non-fatal
            assertThatCode(() -> paymentService.initiatePayment(validRequest))
                .doesNotThrowAnyException();
        }
    }

    // ── REFUND ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("refundPayment()")
    class RefundPayment {

        @Test
        @DisplayName("should process full refund successfully")
        void shouldProcessFullRefund() {
            Payment refundedPayment = savedPayment.toBuilder()
                .status(PaymentStatus.REFUNDED)
                .refundReference("rf_xyz789")
                .refundAmount(new BigDecimal("199.99"))
                .build();

            given(paymentRepository.findByPaymentId("PAY-ABC12345"))
                .willReturn(Optional.of(savedPayment));
            given(gatewayClient.refund(any(), any()))
                .willReturn(PaymentDto.GatewayResponse.builder()
                    .success(true).transactionId("rf_xyz789").build());
            given(paymentRepository.save(any())).willReturn(refundedPayment);

            PaymentDto.PaymentResponse response = paymentService.refundPayment(
                "PAY-ABC12345",
                PaymentDto.RefundRequest.builder().reason("Customer request").build()
            );

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(response.getRefundReference()).isEqualTo("rf_xyz789");
        }

        @Test
        @DisplayName("should process partial refund and set PARTIALLY_REFUNDED")
        void shouldProcessPartialRefund() {
            Payment partialRefundedPayment = savedPayment.toBuilder()
                .status(PaymentStatus.PARTIALLY_REFUNDED)
                .refundAmount(new BigDecimal("50.00"))
                .build();

            given(paymentRepository.findByPaymentId("PAY-ABC12345"))
                .willReturn(Optional.of(savedPayment));
            given(gatewayClient.refund(any(), any()))
                .willReturn(PaymentDto.GatewayResponse.builder()
                    .success(true).transactionId("rf_partial").build());
            given(paymentRepository.save(any())).willReturn(partialRefundedPayment);

            PaymentDto.PaymentResponse response = paymentService.refundPayment(
                "PAY-ABC12345",
                PaymentDto.RefundRequest.builder()
                    .amount(new BigDecimal("50.00")).reason("Partial return").build()
            );

            assertThat(response.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        }

        @Test
        @DisplayName("should throw when refund amount exceeds original payment")
        void shouldThrowOnOverRefund() {
            given(paymentRepository.findByPaymentId("PAY-ABC12345"))
                .willReturn(Optional.of(savedPayment));

            assertThatThrownBy(() -> paymentService.refundPayment(
                "PAY-ABC12345",
                PaymentDto.RefundRequest.builder()
                    .amount(new BigDecimal("999.99")).reason("Test").build()))
                .isInstanceOf(InvalidPaymentOperationException.class)
                .hasMessageContaining("exceeds original");
        }

        @Test
        @DisplayName("should throw when payment is not in COMPLETED status")
        void shouldThrowWhenPaymentNotRefundable() {
            Payment failedPayment = savedPayment.toBuilder()
                .status(PaymentStatus.FAILED).build();

            given(paymentRepository.findByPaymentId("PAY-ABC12345"))
                .willReturn(Optional.of(failedPayment));

            assertThatThrownBy(() -> paymentService.refundPayment(
                "PAY-ABC12345",
                PaymentDto.RefundRequest.builder().reason("Test").build()))
                .isInstanceOf(InvalidPaymentOperationException.class)
                .hasMessageContaining("cannot be refunded");
        }

        @Test
        @DisplayName("should throw when payment already refunded")
        void shouldThrowWhenAlreadyRefunded() {
            Payment alreadyRefunded = savedPayment.toBuilder()
                .status(PaymentStatus.REFUNDED)
                .refundReference("rf_already")
                .build();

            given(paymentRepository.findByPaymentId("PAY-ABC12345"))
                .willReturn(Optional.of(alreadyRefunded));

            assertThatThrownBy(() -> paymentService.refundPayment(
                "PAY-ABC12345",
                PaymentDto.RefundRequest.builder().reason("Test").build()))
                .isInstanceOf(InvalidPaymentOperationException.class);
        }
    }

    // ── READ ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getByPaymentId: should return payment when found")
    void shouldGetByPaymentId() {
        given(paymentRepository.findByPaymentId("PAY-ABC12345"))
            .willReturn(Optional.of(savedPayment));

        PaymentDto.PaymentResponse response = paymentService.getByPaymentId("PAY-ABC12345");

        assertThat(response.getPaymentId()).isEqualTo("PAY-ABC12345");
        assertThat(response.getOrderNumber()).isEqualTo("ORD-TEST001");
    }

    @Test
    @DisplayName("getByPaymentId: should throw PaymentNotFoundException when not found")
    void shouldThrowWhenPaymentNotFound() {
        given(paymentRepository.findByPaymentId("PAY-UNKNOWN"))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getByPaymentId("PAY-UNKNOWN"))
            .isInstanceOf(PaymentNotFoundException.class)
            .hasMessageContaining("PAY-UNKNOWN");
    }

    // ── RECONCILIATION ─────────────────────────────────────────────────────

    @Test
    @DisplayName("reconcileStalePayments: should mark stale PROCESSING payments as FAILED")
    void shouldReconcileStalePayments() {
        Payment stalePayment = savedPayment.toBuilder()
            .status(PaymentStatus.PROCESSING)
            .updatedAt(LocalDateTime.now().minusMinutes(15))
            .build();

        given(paymentRepository.findStaleProcessingPayments(any()))
            .willReturn(List.of(stalePayment));

        int reconciled = paymentService.reconcileStalePayments(10);

        assertThat(reconciled).isEqualTo(1);
        assertThat(stalePayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        then(paymentRepository).should().saveAll(any());
        then(orderServiceClient).should().notifyPaymentResult(any());
    }

    @Test
    @DisplayName("reconcileStalePayments: should return 0 when no stale payments")
    void shouldReturnZeroWhenNoStalePayments() {
        given(paymentRepository.findStaleProcessingPayments(any()))
            .willReturn(List.of());

        int reconciled = paymentService.reconcileStalePayments(10);

        assertThat(reconciled).isEqualTo(0);
        then(paymentRepository).should(never()).saveAll(any());
    }
}
