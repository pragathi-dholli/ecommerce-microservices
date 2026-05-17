package com.ecommerce.order.service;

import com.ecommerce.order.client.PaymentServiceClient;
import com.ecommerce.order.client.ProductServiceClient;
import com.ecommerce.order.dto.ExternalServiceDto;
import com.ecommerce.order.dto.OrderDto;
import com.ecommerce.order.exception.InsufficientStockException;
import com.ecommerce.order.exception.InvalidOrderStateException;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.model.*;
import com.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock private OrderRepository      orderRepository;
    @Mock private ProductServiceClient productServiceClient;
    @Mock private PaymentServiceClient paymentServiceClient;

    @InjectMocks private OrderService orderService;

    private OrderDto.CreateRequest createRequest;
    private ExternalServiceDto.PaymentMethodDto paymentMethod;
    private ExternalServiceDto.InventoryCheckResponse availableInventory;
    private ExternalServiceDto.PaymentResponse successfulPayment;

    @BeforeEach
    void setUp() {
        createRequest = OrderDto.CreateRequest.builder()
            .customerId("CUST-001")
            .customerEmail("test@example.com")
            .items(List.of(
                OrderDto.OrderItemRequest.builder().productSku("ELEC-001").quantity(2).build()
            ))
            .shippingAddress(OrderDto.ShippingAddressDto.builder()
                .fullName("Jane Doe")
                .addressLine1("123 Main St")
                .city("Austin")
                .state("TX")
                .postalCode("78701")
                .country("US")
                .build())
            .build();

        paymentMethod = ExternalServiceDto.PaymentMethodDto.builder()
            .type("CARD")
            .token("tok_test_123")
            .build();

        availableInventory = ExternalServiceDto.InventoryCheckResponse.builder()
            .sku("ELEC-001")
            .available(true)
            .availableQuantity(50)
            .unitPrice(new BigDecimal("99.99"))
            .productName("Test Headphones")
            .build();

        successfulPayment = ExternalServiceDto.PaymentResponse.builder()
            .paymentId("PAY-ABC123")
            .status("COMPLETED")
            .amount(new BigDecimal("209.97"))
            .build();
    }

    // ── CREATE ORDER ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("createOrder()")
    class CreateOrder {

        @Test
        @DisplayName("should create CONFIRMED order on successful inventory + payment")
        void shouldCreateConfirmedOrder() {
            given(productServiceClient.checkInventory(any())).willReturn(availableInventory);
            given(paymentServiceClient.initiatePayment(any())).willReturn(successfulPayment);

            Order savedOrder = buildSavedOrder(OrderStatus.CONFIRMED);
            given(orderRepository.save(any())).willReturn(savedOrder);

            OrderDto.Response response = orderService.createOrder(createRequest, paymentMethod);

            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

            then(productServiceClient).should().checkInventory(any());
            then(productServiceClient).should().deductStock(eq("ELEC-001"), eq(2));
            then(paymentServiceClient).should().initiatePayment(any());
        }

        @Test
        @DisplayName("should throw InsufficientStockException when item unavailable")
        void shouldThrowWhenStockUnavailable() {
            ExternalServiceDto.InventoryCheckResponse unavailable =
                ExternalServiceDto.InventoryCheckResponse.builder()
                    .sku("ELEC-001").available(false).availableQuantity(0).build();

            given(productServiceClient.checkInventory(any())).willReturn(unavailable);

            assertThatThrownBy(() -> orderService.createOrder(createRequest, paymentMethod))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("ELEC-001");

            then(productServiceClient).should(never()).deductStock(any(), anyInt());
            then(paymentServiceClient).should(never()).initiatePayment(any());
        }

        @Test
        @DisplayName("should rollback stock and cancel order when payment fails")
        void shouldRollbackStockOnPaymentFailure() {
            given(productServiceClient.checkInventory(any())).willReturn(availableInventory);
            willThrow(new RuntimeException("Payment timeout")).given(paymentServiceClient).initiatePayment(any());

            Order savedOrder = buildSavedOrder(OrderStatus.PENDING);
            given(orderRepository.save(any())).willReturn(savedOrder);

            assertThatThrownBy(() -> orderService.createOrder(createRequest, paymentMethod))
                .isInstanceOf(RuntimeException.class);

            // Stock should be restored (compensating transaction)
            then(productServiceClient).should().restoreStock(eq("ELEC-001"), eq(2));
        }

        @Test
        @DisplayName("should rollback stock and cancel order when stock deduction fails")
        void shouldRollbackOnStockDeductionFailure() {
            given(productServiceClient.checkInventory(any())).willReturn(availableInventory);
            willThrow(new RuntimeException("Product Service down")).given(productServiceClient).deductStock(any(), anyInt());

            Order savedOrder = buildSavedOrder(OrderStatus.PENDING);
            given(orderRepository.save(any())).willReturn(savedOrder);

            assertThatThrownBy(() -> orderService.createOrder(createRequest, paymentMethod))
                .isInstanceOf(RuntimeException.class);

            // No stock was successfully deducted, so no restore expected
            then(paymentServiceClient).should(never()).initiatePayment(any());
        }

        @Test
        @DisplayName("should create PENDING order when payment is async")
        void shouldCreatePendingOrderOnAsyncPayment() {
            ExternalServiceDto.PaymentResponse pendingPayment =
                ExternalServiceDto.PaymentResponse.builder()
                    .paymentId("PAY-ASYNC-001").status("PENDING").build();

            given(productServiceClient.checkInventory(any())).willReturn(availableInventory);
            given(paymentServiceClient.initiatePayment(any())).willReturn(pendingPayment);

            Order savedOrder = buildSavedOrder(OrderStatus.PENDING);
            given(orderRepository.save(any())).willReturn(savedOrder);

            OrderDto.Response response = orderService.createOrder(createRequest, paymentMethod);

            assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        }
    }

    // ── CANCEL ORDER ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrder {

        @Test
        @DisplayName("should cancel PENDING order and restore stock")
        void shouldCancelPendingOrder() {
            Order order = buildSavedOrder(OrderStatus.PENDING);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willReturn(order);

            orderService.cancelOrder(1L, "Changed my mind");

            then(productServiceClient).should().restoreStock(eq("ELEC-001"), eq(2));
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("should trigger refund when cancelling CONFIRMED paid order")
        void shouldRefundOnCancelConfirmedOrder() {
            Order order = buildSavedOrder(OrderStatus.CONFIRMED);
            order.setPaymentId("PAY-123");
            order.setPaymentStatus(PaymentStatus.COMPLETED);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willReturn(order);

            ExternalServiceDto.PaymentResponse refundResponse =
                ExternalServiceDto.PaymentResponse.builder().status("REFUNDED").build();
            given(paymentServiceClient.refundPayment("PAY-123")).willReturn(refundResponse);

            orderService.cancelOrder(1L, "Cancellation");

            then(paymentServiceClient).should().refundPayment("PAY-123");
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("should throw when cancelling SHIPPED order")
        void shouldThrowWhenCancellingShippedOrder() {
            Order order = buildSavedOrder(OrderStatus.SHIPPED);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L, "Too late"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("SHIPPED");
        }
    }

    // ── STATUS TRANSITIONS ─────────────────────────────────────────────────

    @Nested
    @DisplayName("updateOrderStatus() — state machine")
    class StatusTransitions {

        @Test
        @DisplayName("PENDING → CONFIRMED is valid")
        void pendingToConfirmed() {
            testValidTransition(OrderStatus.PENDING, OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("CONFIRMED → PROCESSING is valid")
        void confirmedToProcessing() {
            testValidTransition(OrderStatus.CONFIRMED, OrderStatus.PROCESSING);
        }

        @Test
        @DisplayName("PROCESSING → SHIPPED is valid")
        void processingToShipped() {
            testValidTransition(OrderStatus.PROCESSING, OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("SHIPPED → DELIVERED is valid")
        void shippedToDelivered() {
            testValidTransition(OrderStatus.SHIPPED, OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("PENDING → SHIPPED is invalid")
        void pendingToShippedIsInvalid() {
            testInvalidTransition(OrderStatus.PENDING, OrderStatus.SHIPPED);
        }

        @Test
        @DisplayName("DELIVERED → PENDING is invalid")
        void deliveredToPendingIsInvalid() {
            testInvalidTransition(OrderStatus.DELIVERED, OrderStatus.PENDING);
        }

        private void testValidTransition(OrderStatus from, OrderStatus to) {
            Order order = buildSavedOrder(from);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willReturn(order);

            assertThatCode(() -> orderService.updateOrderStatus(1L,
                OrderDto.StatusUpdateRequest.builder().status(to).build()))
                .doesNotThrowAnyException();
        }

        private void testInvalidTransition(OrderStatus from, OrderStatus to) {
            Order order = buildSavedOrder(from);
            given(orderRepository.findById(1L)).willReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.updateOrderStatus(1L,
                OrderDto.StatusUpdateRequest.builder().status(to).build()))
                .isInstanceOf(InvalidOrderStateException.class);
        }
    }

    // ── PAYMENT CALLBACK ───────────────────────────────────────────────────

    @Nested
    @DisplayName("handlePaymentCallback()")
    class PaymentCallback {

        @Test
        @DisplayName("COMPLETED payment → order CONFIRMED")
        void completedPaymentConfirmsOrder() {
            Order order = buildSavedOrder(OrderStatus.PENDING);
            given(orderRepository.findByOrderNumber("ORD-TEST001")).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willReturn(order);

            orderService.handlePaymentCallback(OrderDto.PaymentCallbackRequest.builder()
                .orderNumber("ORD-TEST001")
                .paymentId("PAY-XYZ")
                .paymentStatus(PaymentStatus.COMPLETED)
                .build());

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("FAILED payment → order CANCELLED + stock restored")
        void failedPaymentCancelsOrder() {
            Order order = buildSavedOrder(OrderStatus.PENDING);
            given(orderRepository.findByOrderNumber("ORD-TEST002")).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willReturn(order);

            orderService.handlePaymentCallback(OrderDto.PaymentCallbackRequest.builder()
                .orderNumber("ORD-TEST002")
                .paymentId("PAY-FAIL")
                .paymentStatus(PaymentStatus.FAILED)
                .failureReason("Insufficient funds")
                .build());

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(productServiceClient).should().restoreStock(eq("ELEC-001"), eq(2));
        }
    }

    // ── NOT FOUND ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrderById: should throw OrderNotFoundException")
    void shouldThrowWhenOrderNotFound() {
        given(orderRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
            .isInstanceOf(OrderNotFoundException.class)
            .hasMessageContaining("99");
    }

    // ── HELPER ────────────────────────────────────────────────────────────

    private Order buildSavedOrder(OrderStatus status) {
        Order order = Order.builder()
            .id(1L)
            .orderNumber("ORD-TEST001")
            .customerId("CUST-001")
            .customerEmail("test@example.com")
            .subtotal(new BigDecimal("199.98"))
            .shippingCost(new BigDecimal("9.99"))
            .taxAmount(new BigDecimal("20.00"))
            .totalAmount(new BigDecimal("229.97"))
            .status(status)
            .shippingAddress(ShippingAddress.builder()
                .fullName("Jane Doe").addressLine1("123 Main St")
                .city("Austin").state("TX").postalCode("78701").country("US")
                .build())
            .version(0L)
            .build();

        OrderItem item = OrderItem.builder()
            .id(1L).productSku("ELEC-001").productName("Test Headphones")
            .quantity(2).unitPrice(new BigDecimal("99.99"))
            .lineTotal(new BigDecimal("199.98"))
            .build();
        item.setOrder(order);
        order.getItems().add(item);
        return order;
    }
}
