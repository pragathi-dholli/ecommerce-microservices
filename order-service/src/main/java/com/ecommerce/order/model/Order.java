package com.ecommerce.order.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_customer",   columnList = "customer_id"),
    @Index(name = "idx_order_status",     columnList = "status"),
    @Index(name = "idx_order_number",     columnList = "order_number", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_seq")
    @SequenceGenerator(name = "order_seq", sequenceName = "order_sequence", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String orderNumber;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, length = 255)
    private String customerEmail;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    // Shipping address (embedded)
    @Embedded
    private ShippingAddress shippingAddress;

    // Payment reference set after successful payment
    @Column(length = 100)
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private PaymentStatus paymentStatus;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    // ── Helper methods ─────────────────────────────────────────────────────

    @PrePersist
    private void generateOrderNumber() {
        if (this.orderNumber == null) {
            this.orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void recalculateTotals() {
        this.subtotal = items.stream()
            .map(OrderItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalAmount = subtotal.add(shippingCost).add(taxAmount);
    }

    public boolean isCancellable() {
        return status == OrderStatus.PENDING || status == OrderStatus.CONFIRMED;
    }
}
