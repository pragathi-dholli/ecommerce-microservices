package com.ecommerce.payment.repository;

import com.ecommerce.payment.model.Payment;
import com.ecommerce.payment.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentId(String paymentId);

    Optional<Payment> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    Page<Payment> findByCustomerId(String customerId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    @Query("""
        SELECT p FROM Payment p
        WHERE (:customerId IS NULL OR p.customerId = :customerId)
          AND (:status IS NULL OR p.status = :status)
          AND (:from IS NULL OR p.createdAt >= :from)
          AND (:to IS NULL OR p.createdAt <= :to)
        ORDER BY p.createdAt DESC
        """)
    Page<Payment> findWithFilters(
        @Param("customerId") String customerId,
        @Param("status") PaymentStatus status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );

    // Payments stuck in PROCESSING — need reconciliation
    @Query("""
        SELECT p FROM Payment p
        WHERE p.status = 'PROCESSING'
          AND p.updatedAt < :cutoff
        """)
    List<Payment> findStaleProcessingPayments(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.customerId = :customerId AND p.status = 'COMPLETED'")
    long countCompletedByCustomerId(@Param("customerId") String customerId);
}
