package com.ecommerce.payment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_order_number", columnList = "order_number", unique = true),
    @Index(name = "idx_payment_status",       columnList = "status"),
    @Index(name = "idx_payment_customer",     columnList = "customer_id"),
    @Index(name = "idx_payment_reference",    columnList = "gateway_reference")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_seq")
    @SequenceGenerator(name = "payment_seq", sequenceName = "payment_sequence", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 50)
    private String paymentId;           // Internal ID e.g. PAY-A1B2C3D4

    @Column(nullable = false, unique = true, length = 50)
    private String orderNumber;         // Links back to Order Service

    @Column(nullable = false, length = 255)
    private String customerId;

    @Column(nullable = false, length = 255)
    private String customerEmail;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;       // CARD, BANK_TRANSFER, WALLET

    // Tokenised payment reference (never store raw card numbers)
    @Column(length = 100)
    private String paymentToken;

    // Gateway's own transaction reference (Stripe charge ID, etc.)
    @Column(length = 100)
    private String gatewayReference;

    // Human-readable failure reason when status = FAILED
    @Column(columnDefinition = "TEXT")
    private String failureReason;

    // Populated when a refund is issued
    @Column(length = 100)
    private String refundReference;

    @Column(precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column
    private LocalDateTime refundedAt;

    // Number of processing attempts (for retry tracking)
    @Column(nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public boolean isRefundable() {
        return status == PaymentStatus.COMPLETED && refundReference == null;
    }

    public void incrementAttempt() {
        this.attemptCount++;
    }
}
