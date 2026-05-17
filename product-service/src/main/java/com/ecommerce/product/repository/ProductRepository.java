package com.ecommerce.product.repository;

import com.ecommerce.product.model.Product;
import com.ecommerce.product.model.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    Page<Product> findByCategory(String category, Pageable pageable);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    @Query("""
        SELECT p FROM Product p
        WHERE (:category IS NULL OR p.category = :category)
          AND (:brand IS NULL OR p.brand = :brand)
          AND (:status IS NULL OR p.status = :status)
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
          AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Page<Product> findWithFilters(
        @Param("category") String category,
        @Param("brand") String brand,
        @Param("status") ProductStatus status,
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        @Param("search") String search,
        Pageable pageable
    );

    @Query("SELECT p FROM Product p WHERE p.sku IN :skus")
    List<Product> findBySkuIn(@Param("skus") List<String> skus);

    @Query("SELECT p FROM Product p WHERE p.stockQuantity <= :threshold AND p.status = 'ACTIVE'")
    List<Product> findLowStockProducts(@Param("threshold") int threshold);

    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity WHERE p.id = :id AND p.stockQuantity >= :quantity")
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :quantity WHERE p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    List<Product> findTop10ByCategoryAndStatusOrderByCreatedAtDesc(String category, ProductStatus status);
}
