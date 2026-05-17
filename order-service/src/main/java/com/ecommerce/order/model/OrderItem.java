package com.ecommerce.order.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_item_seq")
    @SequenceGenerator(name = "order_item_seq", sequenceName = "order_item_sequence", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, length = 50)
    private String productSku;

    @Column(nullable = false, length = 255)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    // Price at time of order (snapshot — protects against product price changes)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @PrePersist
    @PreUpdate
    public void calculateLineTotal() {
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
