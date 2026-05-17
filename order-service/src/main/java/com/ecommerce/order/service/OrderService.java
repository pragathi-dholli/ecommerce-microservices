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
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderService {

    private static final BigDecimal TAX_RATE        = new BigDecimal("0.10"); // 10%
    private static final BigDecimal SHIPPING_COST   = new BigDecimal("9.99");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("100.00");

    private final OrderRepository      orderRepository;
    private final ProductServiceClient productServiceClient;
    private final PaymentServiceClient paymentServiceClient;

    // ── CREATE ORDER ───────────────────────────────────────────────────────
    /**
     * Full order creation flow:
     *  1. Validate stock availability for all items (Product Service)
     *  2. Build order with price snapshot
     *  3. Save order as PENDING
     *  4. Deduct stock atomically (Product Service)
     *  5. Initiate payment (Payment Service)
     *  6. Update order status based on payment result
     *
     * On any failure after stock deduction, a compensating transaction restores stock.
     */
    @Transactional
    @CircuitBreaker(name = "productService", fallbackMethod = "createOrderFallback")
    @Retry(name = "productService")
    public OrderDto.Response createOrder(OrderDto.CreateRequest request,
                                          ExternalServiceDto.PaymentMethodDto paymentMethod) {
        log.info("Creating order for customer={}, items={}", request.getCustomerId(), request.getItems().size());

        // ── Step 1: Validate all items against Product Service inventory ───
        List<EnrichedItem> enrichedItems = validateAndEnrichItems(request.getItems());

        // ── Step 2: Build Order entity ─────────────────────────────────────
        Order order = buildOrder(request, enrichedItems);
        Order savedOrder = orderRepository.save(order);
        log.info("Order created: orderNumber={}, total={}", savedOrder.getOrderNumber(), savedOrder.getTotalAmount());

        // ── Step 3: Deduct stock for each item ─────────────────────────────
        List<String> deductedSkus = new ArrayList<>();
        try {
            for (EnrichedItem item : enrichedItems) {
                productServiceClient.deductStock(item.sku(), item.quantity());
                deductedSkus.add(item.sku());
                log.debug("Stock deducted: sku={}, qty={}", item.sku(), item.quantity());
            }
        } catch (Exception ex) {
            log.error("Stock deduction failed — rolling back {} SKUs", deductedSkus.size());
            rollbackStockDeductions(deductedSkus, enrichedItems);
            savedOrder.setStatus(OrderStatus.CANCELLED);
            savedOrder.setNotes("Stock deduction failed: " + ex.getMessage());
            orderRepository.save(savedOrder);
            throw ex;
        }

        // ── Step 4: Initiate payment ───────────────────────────────────────
        ExternalServiceDto.PaymentResponse paymentResponse = initiatePayment(
            savedOrder, paymentMethod, deductedSkus, enrichedItems
        );

        // ── Step 5: Update order status from payment result ────────────────
        updateOrderFromPayment(savedOrder, paymentResponse);
        return toResponse(orderRepository.save(savedOrder));
    }

    // ── READ ───────────────────────────────────────────────────────────────

    public OrderDto.Response getOrderById(Long id) {
        return toResponse(findById(id));
    }

    public OrderDto.Response getOrderByNumber(String orderNumber) {
        return toResponse(orderRepository.findByOrderNumberWithItems(orderNumber)
            .orElseThrow(() -> new OrderNotFoundException(orderNumber)));
    }

    public Page<OrderDto.Summary> getOrdersByCustomer(String customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable).map(this::toSummary);
    }

    public Page<OrderDto.Summary> getAllOrders(String customerId, OrderStatus status,
                                                LocalDateTime from, LocalDateTime to,
                                                Pageable pageable) {
        return orderRepository.findWithFilters(customerId, status, from, to, pageable)
            .map(this::toSummary);
    }

    // ── STATUS UPDATE ──────────────────────────────────────────────────────

    @Transactional
    public OrderDto.Response updateOrderStatus(Long id, OrderDto.StatusUpdateRequest request) {
        Order order = findById(id);
        validateStatusTransition(order.getStatus(), request.getStatus());

        order.setStatus(request.getStatus());
        if (request.getNotes() != null) order.setNotes(request.getNotes());

        log.info("Order {} status updated: {} → {}", order.getOrderNumber(), order.getStatus(), request.getStatus());
        return toResponse(orderRepository.save(order));
    }

    // ── CANCEL ─────────────────────────────────────────────────────────────

    @Transactional
    @CircuitBreaker(name = "productService")
    public OrderDto.Response cancelOrder(Long id, String reason) {
        Order order = findById(id);

        if (!order.isCancellable()) {
            throw new InvalidOrderStateException(
                "Order " + order.getOrderNumber() + " cannot be cancelled in status: " + order.getStatus()
            );
        }

        // Restore stock for each item
        order.getItems().forEach(item -> {
            try {
                productServiceClient.restoreStock(item.getProductSku(), item.getQuantity());
                log.info("Stock restored: sku={}, qty={}", item.getProductSku(), item.getQuantity());
            } catch (Exception ex) {
                log.error("Stock restore failed for sku={} — manual reconciliation needed", item.getProductSku());
            }
        });

        // Trigger refund if payment was already completed
        if (order.getPaymentId() != null && order.getPaymentStatus() == PaymentStatus.COMPLETED) {
            try {
                paymentServiceClient.refundPayment(order.getPaymentId());
                order.setPaymentStatus(PaymentStatus.REFUNDED);
                log.info("Refund initiated for paymentId={}", order.getPaymentId());
            } catch (Exception ex) {
                log.error("Refund failed for paymentId={} — manual review needed", order.getPaymentId());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setNotes(reason != null ? reason : "Cancelled by customer");
        return toResponse(orderRepository.save(order));
    }

    // ── PAYMENT CALLBACK ───────────────────────────────────────────────────

    @Transactional
    public OrderDto.Response handlePaymentCallback(OrderDto.PaymentCallbackRequest callback) {
        Order order = orderRepository.findByOrderNumber(callback.getOrderNumber())
            .orElseThrow(() -> new OrderNotFoundException(callback.getOrderNumber()));

        order.setPaymentId(callback.getPaymentId());
        order.setPaymentStatus(callback.getPaymentStatus());

        if (callback.getPaymentStatus() == PaymentStatus.COMPLETED) {
            order.setStatus(OrderStatus.CONFIRMED);
            log.info("Payment confirmed for order={}", callback.getOrderNumber());
        } else if (callback.getPaymentStatus() == PaymentStatus.FAILED) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setNotes("Payment failed: " + callback.getFailureReason());
            // Restore stock on payment failure
            order.getItems().forEach(item ->
                productServiceClient.restoreStock(item.getProductSku(), item.getQuantity())
            );
            log.warn("Payment failed for order={}: {}", callback.getOrderNumber(), callback.getFailureReason());
        }

        return toResponse(orderRepository.save(order));
    }

    // ── STALE ORDER CLEANUP (scheduled) ───────────────────────────────────

    @Transactional
    public int cancelStaleOrders(int pendingTimeoutMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(pendingTimeoutMinutes);
        List<Order> stale = orderRepository.findStalePendingOrders(cutoff);

        stale.forEach(order -> {
            log.warn("Cancelling stale order={}, created={}", order.getOrderNumber(), order.getCreatedAt());
            order.setStatus(OrderStatus.CANCELLED);
            order.setNotes("Auto-cancelled: payment not received within " + pendingTimeoutMinutes + " minutes");
            // Best-effort stock restore
            order.getItems().forEach(item -> {
                try {
                    productServiceClient.restoreStock(item.getProductSku(), item.getQuantity());
                } catch (Exception ex) {
                    log.error("Failed to restore stock for stale order={}, sku={}", order.getOrderNumber(), item.getProductSku());
                }
            });
        });

        orderRepository.saveAll(stale);
        return stale.size();
    }

    // ── CIRCUIT BREAKER FALLBACK ───────────────────────────────────────────

    public OrderDto.Response createOrderFallback(OrderDto.CreateRequest request,
                                                   ExternalServiceDto.PaymentMethodDto paymentMethod,
                                                   Throwable ex) {
        log.error("Order creation failed — circuit open: {}", ex.getMessage());
        throw new com.ecommerce.order.exception.ServiceUnavailableException(
            "Order service is temporarily unable to process orders. Please try again in a moment."
        );
    }

    // ── PRIVATE HELPERS ────────────────────────────────────────────────────

    private List<EnrichedItem> validateAndEnrichItems(List<OrderDto.OrderItemRequest> itemRequests) {
        List<EnrichedItem> enriched = new ArrayList<>();

        for (OrderDto.OrderItemRequest req : itemRequests) {
            ExternalServiceDto.InventoryCheckResponse inv = productServiceClient.checkInventory(
                ExternalServiceDto.InventoryCheckRequest.builder()
                    .sku(req.getProductSku())
                    .requestedQuantity(req.getQuantity())
                    .build()
            );

            if (!inv.isAvailable()) {
                throw new InsufficientStockException(req.getProductSku());
            }

            enriched.add(new EnrichedItem(
                req.getProductSku(),
                inv.getProductName() != null ? inv.getProductName() : req.getProductSku(),
                req.getQuantity(),
                inv.getUnitPrice()
            ));
        }
        return enriched;
    }

    private Order buildOrder(OrderDto.CreateRequest request, List<EnrichedItem> enrichedItems) {
        Order order = Order.builder()
            .customerId(request.getCustomerId())
            .customerEmail(request.getCustomerEmail())
            .shippingAddress(mapAddress(request.getShippingAddress()))
            .notes(request.getNotes())
            .subtotal(BigDecimal.ZERO)
            .shippingCost(BigDecimal.ZERO)
            .taxAmount(BigDecimal.ZERO)
            .totalAmount(BigDecimal.ZERO)
            .build();

        enrichedItems.forEach(item -> {
            OrderItem orderItem = OrderItem.builder()
                .productSku(item.sku())
                .productName(item.productName())
                .quantity(item.quantity())
                .unitPrice(item.unitPrice())
                .lineTotal(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .build();
            order.addItem(orderItem);
        });

        order.recalculateTotals();

        // Apply shipping and tax
        BigDecimal shipping = order.getSubtotal().compareTo(FREE_SHIPPING_THRESHOLD) >= 0
            ? BigDecimal.ZERO : SHIPPING_COST;
        BigDecimal tax = order.getSubtotal().multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);

        order.setShippingCost(shipping);
        order.setTaxAmount(tax);
        order.setTotalAmount(order.getSubtotal().add(shipping).add(tax));
        return order;
    }

    private ExternalServiceDto.PaymentResponse initiatePayment(
            Order order,
            ExternalServiceDto.PaymentMethodDto paymentMethod,
            List<String> deductedSkus,
            List<EnrichedItem> enrichedItems) {
        try {
            return paymentServiceClient.initiatePayment(
                ExternalServiceDto.PaymentRequest.builder()
                    .orderNumber(order.getOrderNumber())
                    .amount(order.getTotalAmount())
                    .currency("USD")
                    .customerId(order.getCustomerId())
                    .customerEmail(order.getCustomerEmail())
                    .paymentMethod(paymentMethod)
                    .build()
            );
        } catch (Exception ex) {
            log.error("Payment initiation failed — rolling back stock for order={}", order.getOrderNumber());
            rollbackStockDeductions(deductedSkus, enrichedItems);
            order.setStatus(OrderStatus.CANCELLED);
            order.setNotes("Payment initiation failed: " + ex.getMessage());
            orderRepository.save(order);
            throw ex;
        }
    }

    private void updateOrderFromPayment(Order order, ExternalServiceDto.PaymentResponse paymentResponse) {
        order.setPaymentId(paymentResponse.getPaymentId());

        switch (paymentResponse.getStatus()) {
            case "COMPLETED" -> {
                order.setStatus(OrderStatus.CONFIRMED);
                order.setPaymentStatus(PaymentStatus.COMPLETED);
            }
            case "PENDING" -> {
                // Async payment — stays PENDING until callback
                order.setPaymentStatus(PaymentStatus.PENDING);
            }
            case "FAILED" -> {
                order.setStatus(OrderStatus.CANCELLED);
                order.setPaymentStatus(PaymentStatus.FAILED);
                order.setNotes("Payment failed: " + paymentResponse.getFailureReason());
            }
            default -> log.warn("Unknown payment status: {}", paymentResponse.getStatus());
        }
    }

    private void rollbackStockDeductions(List<String> deductedSkus, List<EnrichedItem> items) {
        deductedSkus.forEach(sku -> {
            items.stream()
                .filter(i -> i.sku().equals(sku))
                .findFirst()
                .ifPresent(item -> {
                    try {
                        productServiceClient.restoreStock(sku, item.quantity());
                        log.info("Compensating transaction: restored stock for sku={}", sku);
                    } catch (Exception e) {
                        log.error("CRITICAL: Failed to restore stock for sku={} — manual intervention needed", sku);
                    }
                });
        });
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            case PENDING    -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED  -> next == OrderStatus.PROCESSING || next == OrderStatus.CANCELLED;
            case PROCESSING -> next == OrderStatus.SHIPPED || next == OrderStatus.CANCELLED;
            case SHIPPED    -> next == OrderStatus.DELIVERED;
            case DELIVERED  -> next == OrderStatus.REFUNDED;
            default         -> false;
        };

        if (!valid) {
            throw new InvalidOrderStateException(
                "Invalid status transition: " + current + " → " + next
            );
        }
    }

    private ShippingAddress mapAddress(OrderDto.ShippingAddressDto dto) {
        return ShippingAddress.builder()
            .fullName(dto.getFullName())
            .addressLine1(dto.getAddressLine1())
            .addressLine2(dto.getAddressLine2())
            .city(dto.getCity())
            .state(dto.getState())
            .postalCode(dto.getPostalCode())
            .country(dto.getCountry())
            .phone(dto.getPhone())
            .build();
    }

    private Order findById(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private OrderDto.Response toResponse(Order o) {
        List<OrderDto.OrderItemResponse> items = o.getItems().stream()
            .map(i -> OrderDto.OrderItemResponse.builder()
                .id(i.getId())
                .productSku(i.getProductSku())
                .productName(i.getProductName())
                .quantity(i.getQuantity())
                .unitPrice(i.getUnitPrice())
                .lineTotal(i.getLineTotal())
                .build())
            .toList();

        return OrderDto.Response.builder()
            .id(o.getId())
            .orderNumber(o.getOrderNumber())
            .customerId(o.getCustomerId())
            .customerEmail(o.getCustomerEmail())
            .items(items)
            .subtotal(o.getSubtotal())
            .shippingCost(o.getShippingCost())
            .taxAmount(o.getTaxAmount())
            .totalAmount(o.getTotalAmount())
            .status(o.getStatus())
            .shippingAddress(mapAddressToDto(o.getShippingAddress()))
            .paymentId(o.getPaymentId())
            .paymentStatus(o.getPaymentStatus())
            .notes(o.getNotes())
            .createdAt(o.getCreatedAt())
            .updatedAt(o.getUpdatedAt())
            .build();
    }

    private OrderDto.Summary toSummary(Order o) {
        return OrderDto.Summary.builder()
            .id(o.getId())
            .orderNumber(o.getOrderNumber())
            .customerId(o.getCustomerId())
            .totalAmount(o.getTotalAmount())
            .status(o.getStatus())
            .paymentStatus(o.getPaymentStatus())
            .itemCount(o.getItems().size())
            .createdAt(o.getCreatedAt())
            .build();
    }

    private OrderDto.ShippingAddressDto mapAddressToDto(ShippingAddress a) {
        if (a == null) return null;
        return OrderDto.ShippingAddressDto.builder()
            .fullName(a.getFullName())
            .addressLine1(a.getAddressLine1())
            .addressLine2(a.getAddressLine2())
            .city(a.getCity())
            .state(a.getState())
            .postalCode(a.getPostalCode())
            .country(a.getCountry())
            .phone(a.getPhone())
            .build();
    }

    // ── Record for enriched item data (replaces passing parallel lists) ────
    private record EnrichedItem(String sku, String productName, int quantity, BigDecimal unitPrice) {}
}
