package com.ecommerce.order.repository;

import com.ecommerce.order.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
@DisplayName("OrderRepository Tests")
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("findByOrderNumber: returns correct order")
    void shouldFindByOrderNumber() {
        Order saved = saveOrder("CUST-A", OrderStatus.CONFIRMED);
        Optional<Order> found = orderRepository.findByOrderNumber(saved.getOrderNumber());
        assertThat(found).isPresent();
        assertThat(found.get().getCustomerId()).isEqualTo("CUST-A");
    }

    @Test
    @DisplayName("findByOrderNumber: returns empty for unknown number")
    void shouldReturnEmptyForUnknownOrderNumber() {
        assertThat(orderRepository.findByOrderNumber("ORD-UNKNOWN")).isEmpty();
    }

    @Test
    @DisplayName("findByCustomerId: returns only that customer's orders")
    void shouldFindByCustomerId() {
        saveOrder("CUST-A", OrderStatus.CONFIRMED);
        saveOrder("CUST-A", OrderStatus.PENDING);
        saveOrder("CUST-B", OrderStatus.CONFIRMED);

        Page<Order> result = orderRepository.findByCustomerId("CUST-A", PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(o -> o.getCustomerId().equals("CUST-A"));
    }

    @Test
    @DisplayName("findByStatus: returns only orders with that status")
    void shouldFindByStatus() {
        saveOrder("CUST-A", OrderStatus.CONFIRMED);
        saveOrder("CUST-B", OrderStatus.PENDING);
        saveOrder("CUST-C", OrderStatus.PENDING);

        Page<Order> result = orderRepository.findByStatus(OrderStatus.PENDING, PageRequest.of(0, 10));
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(o -> o.getStatus() == OrderStatus.PENDING);
    }

    @Test
    @DisplayName("findWithFilters: filters by customerId + status")
    void shouldFilterByCustomerAndStatus() {
        saveOrder("CUST-A", OrderStatus.CONFIRMED);
        saveOrder("CUST-A", OrderStatus.PENDING);
        saveOrder("CUST-B", OrderStatus.CONFIRMED);

        Page<Order> result = orderRepository.findWithFilters(
            "CUST-A", OrderStatus.CONFIRMED, null, null, PageRequest.of(0, 10)
        );
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCustomerId()).isEqualTo("CUST-A");
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("findWithFilters: no filters returns all orders")
    void shouldReturnAllWithNoFilters() {
        saveOrder("CUST-A", OrderStatus.CONFIRMED);
        saveOrder("CUST-B", OrderStatus.PENDING);
        saveOrder("CUST-C", OrderStatus.SHIPPED);

        Page<Order> result = orderRepository.findWithFilters(
            null, null, null, null, PageRequest.of(0, 10)
        );
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("findWithFilters: date range filtering works")
    void shouldFilterByDateRange() {
        saveOrder("CUST-A", OrderStatus.CONFIRMED);
        saveOrder("CUST-B", OrderStatus.CONFIRMED);

        LocalDateTime from = LocalDateTime.now().minusMinutes(5);
        LocalDateTime to   = LocalDateTime.now().plusMinutes(5);

        Page<Order> result = orderRepository.findWithFilters(
            null, null, from, to, PageRequest.of(0, 10)
        );
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findStalePendingOrders: returns only PENDING orders before cutoff")
    void shouldFindStalePendingOrders() {
        // Save two PENDING orders (will appear stale immediately with future cutoff)
        saveOrder("CUST-A", OrderStatus.PENDING);
        saveOrder("CUST-B", OrderStatus.PENDING);
        // Confirmed order — should NOT be returned
        saveOrder("CUST-C", OrderStatus.CONFIRMED);

        LocalDateTime futureCutoff = LocalDateTime.now().plusMinutes(10);
        List<Order> stale = orderRepository.findStalePendingOrders(futureCutoff);

        assertThat(stale).hasSize(2);
        assertThat(stale).allMatch(o -> o.getStatus() == OrderStatus.PENDING);
    }

    @Test
    @DisplayName("findByOrderNumberWithItems: eagerly loads order items")
    void shouldLoadOrderWithItems() {
        Order saved = saveOrderWithItems("CUST-A", OrderStatus.CONFIRMED, 3);

        Optional<Order> found = orderRepository.findByOrderNumberWithItems(saved.getOrderNumber());

        assertThat(found).isPresent();
        assertThat(found.get().getItems()).hasSize(3);
    }

    @Test
    @DisplayName("countByCustomerId: returns correct count")
    void shouldCountOrdersByCustomer() {
        saveOrder("CUST-A", OrderStatus.CONFIRMED);
        saveOrder("CUST-A", OrderStatus.CANCELLED);
        saveOrder("CUST-B", OrderStatus.CONFIRMED);

        assertThat(orderRepository.countByCustomerId("CUST-A")).isEqualTo(2);
        assertThat(orderRepository.countByCustomerId("CUST-B")).isEqualTo(1);
        assertThat(orderRepository.countByCustomerId("CUST-X")).isEqualTo(0);
    }

    // ── HELPERS ────────────────────────────────────────────────────────────

    private Order saveOrder(String customerId, OrderStatus status) {
        return saveOrderWithItems(customerId, status, 1);
    }

    private Order saveOrderWithItems(String customerId, OrderStatus status, int itemCount) {
        Order order = Order.builder()
            .customerId(customerId)
            .customerEmail(customerId.toLowerCase() + "@test.com")
            .subtotal(new BigDecimal("99.99"))
            .shippingCost(new BigDecimal("9.99"))
            .taxAmount(new BigDecimal("10.00"))
            .totalAmount(new BigDecimal("119.98"))
            .status(status)
            .shippingAddress(ShippingAddress.builder()
                .fullName("Test User").addressLine1("1 Test St")
                .city("Austin").state("TX").postalCode("78701").country("US")
                .build())
            .build();

        for (int i = 1; i <= itemCount; i++) {
            OrderItem item = OrderItem.builder()
                .productSku("SKU-00" + i)
                .productName("Product " + i)
                .quantity(1)
                .unitPrice(new BigDecimal("99.99"))
                .lineTotal(new BigDecimal("99.99"))
                .build();
            order.addItem(item);
        }

        return orderRepository.save(order);
    }
}
