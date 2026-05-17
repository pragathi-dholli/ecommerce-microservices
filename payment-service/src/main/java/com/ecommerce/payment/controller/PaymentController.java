package com.ecommerce.payment.controller;

import com.ecommerce.payment.dto.PaymentDto;
import com.ecommerce.payment.model.PaymentStatus;
import com.ecommerce.payment.service.PaymentService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // ── INITIATE PAYMENT ──────────────────────────────────────────────────

    /**
     * Called by Order Service to process a payment.
     * Validates, charges the gateway, notifies Order Service, returns result.
     */
    @PostMapping
    public ResponseEntity<PaymentDto.PaymentResponse> initiatePayment(
            @Valid @RequestBody PaymentDto.PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(paymentService.initiatePayment(request));
    }

    // ── READ ──────────────────────────────────────────────────────────────

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDto.PaymentResponse> getByPaymentId(
            @PathVariable String paymentId) {
        return ResponseEntity.ok(paymentService.getByPaymentId(paymentId));
    }

    @GetMapping("/order/{orderNumber}")
    public ResponseEntity<PaymentDto.PaymentResponse> getByOrderNumber(
            @PathVariable String orderNumber) {
        return ResponseEntity.ok(paymentService.getByOrderNumber(orderNumber));
    }

    @GetMapping
    public ResponseEntity<Page<PaymentDto.Summary>> getAllPayments(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(
            paymentService.getAllPayments(customerId, status, from, to, pageable)
        );
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<Page<PaymentDto.Summary>> getByCustomer(
            @PathVariable String customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(paymentService.getPaymentsByCustomer(customerId, pageable));
    }

    // ── REFUND ────────────────────────────────────────────────────────────

    /**
     * Issue a full or partial refund.
     * Pass amount in body for partial refund; omit for full refund.
     */
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentDto.PaymentResponse> refundPayment(
            @PathVariable String paymentId,
            @Valid @RequestBody PaymentDto.RefundRequest request) {
        return ResponseEntity.ok(paymentService.refundPayment(paymentId, request));
    }

    /**
     * Convenience endpoint — refund by order number (used by Order Service cancel flow).
     */
    @PostMapping("/order/{orderNumber}/refund")
    public ResponseEntity<PaymentDto.PaymentResponse> refundByOrderNumber(
            @PathVariable String orderNumber) {
        return ResponseEntity.ok(paymentService.refundByOrderNumber(orderNumber));
    }
}
