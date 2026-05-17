package com.ecommerce.payment.service;

import com.ecommerce.payment.client.OrderServiceClient;
import com.ecommerce.payment.config.PaymentGatewayClient;
import com.ecommerce.payment.dto.PaymentDto;
import com.ecommerce.payment.exception.DuplicatePaymentException;
import com.ecommerce.payment.exception.InvalidPaymentOperationException;
import com.ecommerce.payment.exception.PaymentNotFoundException;
import com.ecommerce.payment.model.Payment;
import com.ecommerce.payment.model.PaymentMethod;
import com.ecommerce.payment.model.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository    paymentRepository;
    private final PaymentGatewayClient gatewayClient;
    private final OrderServiceClient   orderServiceClient;

    // ── INITIATE PAYMENT ───────────────────────────────────────────────────
    /**
     * Full payment flow:
     *  1. Idempotency check — reject duplicate payments for same order
     *  2. Persist payment as PENDING
     *  3. Mark as PROCESSING, call gateway
     *  4. Update status to COMPLETED or FAILED
     *  5. Notify Order Service of result (async callback)
     */
    @Transactional
    public PaymentDto.PaymentResponse initiatePayment(PaymentDto.PaymentRequest request) {
        log.info("Initiating payment: orderNumber={}, amount={} {}",
            request.getOrderNumber(), request.getAmount(), request.getCurrency());

        // Step 1: Idempotency guard
        if (paymentRepository.existsByOrderNumber(request.getOrderNumber())) {
            throw new DuplicatePaymentException(request.getOrderNumber());
        }

        // Step 2: Persist as PENDING
        Payment payment = Payment.builder()
            .paymentId(generatePaymentId())
            .orderNumber(request.getOrderNumber())
            .customerId(request.getCustomerId())
            .customerEmail(request.getCustomerEmail())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .method(resolveMethod(request.getPaymentMethod().getType()))
            .paymentToken(request.getPaymentMethod().getToken())
            .status(PaymentStatus.PENDING)
            .build();

        payment = paymentRepository.save(payment);

        // Step 3: Mark PROCESSING and call gateway
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.incrementAttempt();
        payment = paymentRepository.save(payment);

        PaymentDto.GatewayResponse gatewayResponse;
        try {
            gatewayResponse = gatewayClient.charge(
                payment.getPaymentToken(),
                payment.getAmount(),
                payment.getCurrency()
            );
        } catch (Exception ex) {
            log.error("Gateway threw exception for orderNumber={}: {}", request.getOrderNumber(), ex.getMessage());
            gatewayResponse = PaymentDto.GatewayResponse.builder()
                .success(false)
                .declineCode("gateway_error")
                .message("Payment gateway unavailable: " + ex.getMessage())
                .build();
        }

        // Step 4: Update status from gateway result
        if (gatewayResponse.isSuccess()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setGatewayReference(gatewayResponse.getTransactionId());
            log.info("Payment COMPLETED: paymentId={}, ref={}", payment.getPaymentId(), gatewayResponse.getTransactionId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(gatewayResponse.getMessage());
            log.warn("Payment FAILED: paymentId={}, code={}", payment.getPaymentId(), gatewayResponse.getDeclineCode());
        }

        payment = paymentRepository.save(payment);

        // Step 5: Notify Order Service (best-effort — circuit breaker handles failures)
        notifyOrderService(payment);

        return toResponse(payment);
    }

    // ── REFUND ─────────────────────────────────────────────────────────────

    @Transactional
    public PaymentDto.PaymentResponse refundPayment(String paymentId, PaymentDto.RefundRequest request) {
        log.info("Refund requested: paymentId={}", paymentId);
        Payment payment = findByPaymentId(paymentId);

        if (!payment.isRefundable()) {
            throw new InvalidPaymentOperationException(
                "Payment " + paymentId + " cannot be refunded. Status: " + payment.getStatus()
            );
        }

        BigDecimal refundAmount = request.getAmount() != null
            ? request.getAmount()
            : payment.getAmount(); // null = full refund

        if (refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new InvalidPaymentOperationException(
                "Refund amount (" + refundAmount + ") exceeds original payment (" + payment.getAmount() + ")"
            );
        }

        PaymentDto.GatewayResponse gatewayResponse = gatewayClient.refund(
            payment.getGatewayReference(), refundAmount
        );

        if (!gatewayResponse.isSuccess()) {
            throw new InvalidPaymentOperationException(
                "Refund failed at gateway: " + gatewayResponse.getMessage()
            );
        }

        boolean isPartial = refundAmount.compareTo(payment.getAmount()) < 0;
        payment.setStatus(isPartial ? PaymentStatus.PARTIALLY_REFUNDED : PaymentStatus.REFUNDED);
        payment.setRefundReference(gatewayResponse.getTransactionId());
        payment.setRefundAmount(refundAmount);
        payment.setRefundedAt(LocalDateTime.now());

        log.info("Refund successful: paymentId={}, refundRef={}, amount={}",
            paymentId, gatewayResponse.getTransactionId(), refundAmount);

        // Notify Order Service of refund
        notifyOrderService(payment);

        return toResponse(paymentRepository.save(payment));
    }

    // ── FULL REFUND (called by Order Service cancel flow) ──────────────────

    @Transactional
    public PaymentDto.PaymentResponse refundByOrderNumber(String orderNumber) {
        Payment payment = paymentRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new PaymentNotFoundException("order: " + orderNumber));
        return refundPayment(payment.getPaymentId(),
            PaymentDto.RefundRequest.builder().reason("Order cancelled").build());
    }

    // ── READ ───────────────────────────────────────────────────────────────

    public PaymentDto.PaymentResponse getByPaymentId(String paymentId) {
        return toResponse(findByPaymentId(paymentId));
    }

    public PaymentDto.PaymentResponse getByOrderNumber(String orderNumber) {
        return toResponse(paymentRepository.findByOrderNumber(orderNumber)
            .orElseThrow(() -> new PaymentNotFoundException("order: " + orderNumber)));
    }

    public Page<PaymentDto.Summary> getAllPayments(String customerId, PaymentStatus status,
                                                    LocalDateTime from, LocalDateTime to,
                                                    Pageable pageable) {
        return paymentRepository.findWithFilters(customerId, status, from, to, pageable)
            .map(this::toSummary);
    }

    public Page<PaymentDto.Summary> getPaymentsByCustomer(String customerId, Pageable pageable) {
        return paymentRepository.findByCustomerId(customerId, pageable).map(this::toSummary);
    }

    // ── STALE PAYMENT RECONCILIATION (scheduled) ───────────────────────────

    @Transactional
    public int reconcileStalePayments(int staleMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(staleMinutes);
        List<Payment> stale = paymentRepository.findStaleProcessingPayments(cutoff);

        stale.forEach(payment -> {
            log.warn("Reconciling stale payment: paymentId={}, stuck since={}",
                payment.getPaymentId(), payment.getUpdatedAt());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment timed out during processing — auto-reconciled");
            notifyOrderService(payment);
        });

        paymentRepository.saveAll(stale);
        return stale.size();
    }

    // ── PRIVATE HELPERS ────────────────────────────────────────────────────

    private void notifyOrderService(Payment payment) {
        String callbackStatus = switch (payment.getStatus()) {
            case COMPLETED           -> "COMPLETED";
            case REFUNDED,
                 PARTIALLY_REFUNDED  -> "REFUNDED";
            default                  -> "FAILED";
        };

        try {
            orderServiceClient.notifyPaymentResult(PaymentDto.OrderCallbackRequest.builder()
                .orderNumber(payment.getOrderNumber())
                .paymentId(payment.getPaymentId())
                .paymentStatus(callbackStatus)
                .failureReason(payment.getFailureReason())
                .build());
            log.debug("Order Service notified: orderNumber={}, status={}", payment.getOrderNumber(), callbackStatus);
        } catch (Exception ex) {
            // Non-fatal — fallback logs for manual reconciliation
            log.error("Failed to notify Order Service for orderNumber={}: {}", payment.getOrderNumber(), ex.getMessage());
        }
    }

    private Payment findByPaymentId(String paymentId) {
        return paymentRepository.findByPaymentId(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private String generatePaymentId() {
        return "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private PaymentMethod resolveMethod(String type) {
        return switch (type.toUpperCase()) {
            case "CARD"          -> PaymentMethod.CARD;
            case "BANK_TRANSFER" -> PaymentMethod.BANK_TRANSFER;
            case "WALLET"        -> PaymentMethod.WALLET;
            default -> throw new InvalidPaymentOperationException("Unknown payment method type: " + type);
        };
    }

    private PaymentDto.PaymentResponse toResponse(Payment p) {
        return PaymentDto.PaymentResponse.builder()
            .id(p.getId())
            .paymentId(p.getPaymentId())
            .orderNumber(p.getOrderNumber())
            .customerId(p.getCustomerId())
            .customerEmail(p.getCustomerEmail())
            .amount(p.getAmount())
            .currency(p.getCurrency())
            .status(p.getStatus())
            .method(p.getMethod())
            .gatewayReference(p.getGatewayReference())
            .failureReason(p.getFailureReason())
            .refundReference(p.getRefundReference())
            .refundAmount(p.getRefundAmount())
            .refundedAt(p.getRefundedAt())
            .attemptCount(p.getAttemptCount())
            .createdAt(p.getCreatedAt())
            .updatedAt(p.getUpdatedAt())
            .build();
    }

    private PaymentDto.Summary toSummary(Payment p) {
        return PaymentDto.Summary.builder()
            .id(p.getId())
            .paymentId(p.getPaymentId())
            .orderNumber(p.getOrderNumber())
            .customerId(p.getCustomerId())
            .amount(p.getAmount())
            .currency(p.getCurrency())
            .status(p.getStatus())
            .method(p.getMethod())
            .createdAt(p.getCreatedAt())
            .build();
    }
}
