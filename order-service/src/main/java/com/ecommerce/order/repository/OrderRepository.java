package com.ecommerce.order.repository;

import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderStatus;
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
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    Page<Order> findByCustomerId(String customerId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByCustomerIdAndStatus(String customerId, OrderStatus status, Pageable pageable);

    @Query("""
        SELECT o FROM Order o
        WHERE (:customerId IS NULL OR o.customerId = :customerId)
          AND (:status IS NULL OR o.status = :status)
          AND (:from IS NULL OR o.createdAt >= :from)
          AND (:to IS NULL OR o.createdAt <= :to)
        ORDER BY o.createdAt DESC
        """)
    Page<Order> findWithFilters(
        @Param("customerId") String customerId,
        @Param("status") OrderStatus status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );

    // Orders stuck in PENDING for more than N minutes (for timeout job)
    @Query("""
        SELECT o FROM Order o
        WHERE o.status = 'PENDING'
          AND o.createdAt < :cutoff
        """)
    List<Order> findStalePendingOrders(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customerId = :customerId")
    long countByCustomerId(@Param("customerId") String customerId);

    @Query("""
        SELECT o FROM Order o
        JOIN FETCH o.items
        WHERE o.orderNumber = :orderNumber
        """)
    Optional<Order> findByOrderNumberWithItems(@Param("orderNumber") String orderNumber);
}
