package com.ecommerce.order.controller;

import com.ecommerce.order.dto.ExternalServiceDto;
import com.ecommerce.order.dto.OrderDto;
import com.ecommerce.order.model.OrderStatus;
import com.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ── CREATE ─────────────────────────────────────────────────────────────

    /**
     * Full order creation: validates inventory → saves order → deducts stock → initiates payment.
     */
    @PostMapping
    public ResponseEntity<OrderDto.Response> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(orderService.createOrder(request.getOrder(), request.getPaymentMethod()));
    }

    // ── READ ───────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<OrderDto.Response> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderDto.Response> getOrderByNumber(@PathVariable String orderNumber) {
        return ResponseEntity.ok(orderService.getOrderByNumber(orderNumber));
    }

    @GetMapping
    public ResponseEntity<Page<OrderDto.Summary>> getAllOrders(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrders(customerId, status, from, to, pageable));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<Page<OrderDto.Summary>> getOrdersByCustomer(
            @PathVariable String customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(orderService.getOrdersByCustomer(customerId, pageable));
    }

    // ── UPDATE STATUS ──────────────────────────────────────────────────────

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDto.Response> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody OrderDto.StatusUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, request));
    }

    // ── CANCEL ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderDto.Response> cancelOrder(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(orderService.cancelOrder(id, reason));
    }

    // ── PAYMENT CALLBACK (called by Payment Service) ───────────────────────

    @PostMapping("/payment-callback")
    public ResponseEntity<OrderDto.Response> paymentCallback(
            @Valid @RequestBody OrderDto.PaymentCallbackRequest callback) {
        return ResponseEntity.ok(orderService.handlePaymentCallback(callback));
    }

    // ── INNER REQUEST WRAPPER ──────────────────────────────────────────────

    /**
     * Wraps order + payment method in a single request body.
     */
    @Data
    public static class CreateOrderRequest {
        @Valid
        private OrderDto.CreateRequest order;
        @Valid
        private ExternalServiceDto.PaymentMethodDto paymentMethod;
    }
}
