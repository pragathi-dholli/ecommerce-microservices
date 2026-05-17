package com.ecommerce.payment.repository;

import com.ecommerce.payment.model.Payment;
import com.ecommerce.payment.model.PaymentMethod;
import com.ecommerce.payment.model.PaymentStatus;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("PaymentRepository Tests")
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("findByPaymentId: returns correct payment")
    void shouldFindByPaymentId() {
        Payment saved = save("PAY-001", "ORD-001", "CUST-A", PaymentStatus.COMPLETED);
        Optional<Payment> found = paymentRepository.findByPaymentId("PAY-001");
        assertThat(found).isPresent();
        assertThat(found.get().getOrderNumber()).isEqualTo("ORD-001");
    }

    @Test
    @DisplayName("findByPaymentId: returns empty for unknown ID")
    void shouldReturnEmptyForUnknownPaymentId() {
        assertThat(paymentRepository.findByPaymentId("PAY-UNKNOWN")).isEmpty();
    }

    @Test
    @DisplayName("findByOrderNumber: returns correct payment")
    void shouldFindByOrderNumber() {
        save("PAY-002", "ORD-002", "CUST-A", PaymentStatus.COMPLETED);
        Optional<Payment> found = paymentRepository.findByOrderNumber("ORD-002");
        assertThat(found).isPresent();
        assertThat(found.get().getPaymentId()).isEqualTo("PAY-002");
    }

    @Test
    @DisplayName("existsByOrderNumber: returns true when payment exists")
    void shouldReturnTrueWhenOrderExists() {
        save("PAY-003", "ORD-003", "CUST-A", PaymentStatus.PENDING);
        assertThat(paymentRepository.existsByOrderNumber("ORD-003")).isTrue();
        assertThat(paymentRepository.existsByOrderNumber("ORD-NONE")).isFalse();
    }

    @Test
    @DisplayName("findByCustomerId: returns only that customer's payments")
    void shouldFindByCustomerId() {
        save("PAY-004", "ORD-004", "CUST-A", PaymentStatus.COMPLETED);
        save("PAY-005", "ORD-005", "CUST-A", PaymentStatus.FAILED);
        save("PAY-006", "ORD-006", "CUST-B", PaymentStatus.COMPLETED);

        Page<Payment> result = paymentRepository.findByCustomerId("CUST-A", PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(p -> p.getCustomerId().equals("CUST-A"));
    }

    @Test
    @DisplayName("findByStatus: returns only matching status")
    void shouldFindByStatus() {
        save("PAY-007", "ORD-007", "CUST-A", PaymentStatus.COMPLETED);
        save("PAY-008", "ORD-008", "CUST-A", PaymentStatus.FAILED);
        save("PAY-009", "ORD-009", "CUST-A", PaymentStatus.PENDING);

        Page<Payment> result = paymentRepository.findByStatus(PaymentStatus.COMPLETED, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPaymentId()).isEqualTo("PAY-007");
    }

    @Test
    @DisplayName("findWithFilters: filters by customer + status")
    void shouldFilterByCustomerAndStatus() {
        save("PAY-010", "ORD-010", "CUST-A", PaymentStatus.COMPLETED);
        save("PAY-011", "ORD-011", "CUST-A", PaymentStatus.FAILED);
        save("PAY-012", "ORD-012", "CUST-B", PaymentStatus.COMPLETED);

        Page<Payment> result = paymentRepository.findWithFilters(
            "CUST-A", PaymentStatus.COMPLETED, null, null, PageRequest.of(0, 10)
        );
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPaymentId()).isEqualTo("PAY-010");
    }

    @Test
    @DisplayName("findWithFilters: no filters returns all")
    void shouldReturnAllWithNoFilters() {
        save("PAY-013", "ORD-013", "CUST-A", PaymentStatus.COMPLETED);
        save("PAY-014", "ORD-014", "CUST-B", PaymentStatus.FAILED);

        Page<Payment> result = paymentRepository.findWithFilters(
            null, null, null, null, PageRequest.of(0, 10)
        );
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findStaleProcessingPayments: returns only PROCESSING before cutoff")
    void shouldFindStaleProcessingPayments() {
        save("PAY-015", "ORD-015", "CUST-A", PaymentStatus.PROCESSING);
        save("PAY-016", "ORD-016", "CUST-A", PaymentStatus.PROCESSING);
        save("PAY-017", "ORD-017", "CUST-A", PaymentStatus.COMPLETED); // Not PROCESSING

        LocalDateTime futureCutoff = LocalDateTime.now().plusMinutes(10);
        List<Payment> stale = paymentRepository.findStaleProcessingPayments(futureCutoff);

        assertThat(stale).hasSize(2);
        assertThat(stale).allMatch(p -> p.getStatus() == PaymentStatus.PROCESSING);
    }

    @Test
    @DisplayName("countCompletedByCustomerId: counts only COMPLETED payments")
    void shouldCountCompletedPayments() {
        save("PAY-018", "ORD-018", "CUST-A", PaymentStatus.COMPLETED);
        save("PAY-019", "ORD-019", "CUST-A", PaymentStatus.COMPLETED);
        save("PAY-020", "ORD-020", "CUST-A", PaymentStatus.FAILED);
        save("PAY-021", "ORD-021", "CUST-B", PaymentStatus.COMPLETED);

        assertThat(paymentRepository.countCompletedByCustomerId("CUST-A")).isEqualTo(2);
        assertThat(paymentRepository.countCompletedByCustomerId("CUST-B")).isEqualTo(1);
        assertThat(paymentRepository.countCompletedByCustomerId("CUST-X")).isEqualTo(0);
    }

    // ── HELPER ────────────────────────────────────────────────────────────

    private Payment save(String paymentId, String orderNumber, String customerId, PaymentStatus status) {
        return paymentRepository.save(Payment.builder()
            .paymentId(paymentId)
            .orderNumber(orderNumber)
            .customerId(customerId)
            .customerEmail(customerId.toLowerCase() + "@test.com")
            .amount(new BigDecimal("99.99"))
            .currency("USD")
            .method(PaymentMethod.CARD)
            .paymentToken("tok_test")
            .gatewayReference(status == PaymentStatus.COMPLETED ? "gw_" + paymentId : null)
            .status(status)
            .attemptCount(1)
            .build());
    }
}
