package com.ecommerce.product.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_sku", columnList = "sku", unique = true),
    @Index(name = "idx_product_category", columnList = "category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "product_sequence", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(length = 100)
    private String brand;

    @Column(length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version; // Optimistic locking

    public boolean isInStock() {
        return stockQuantity > 0 && status == ProductStatus.ACTIVE;
    }

    public void decreaseStock(int quantity) {
        if (stockQuantity < quantity) {
            throw new IllegalStateException(
                "Insufficient stock for product: " + sku + ". Available: " + stockQuantity
            );
        }
        this.stockQuantity -= quantity;
    }

    public void increaseStock(int quantity) {
        this.stockQuantity += quantity;
    }
}
