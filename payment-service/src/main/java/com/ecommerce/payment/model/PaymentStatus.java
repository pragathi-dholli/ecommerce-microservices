package com.ecommerce.payment.model;

public enum PaymentStatus {
    PENDING,       // Created, awaiting processing
    PROCESSING,    // Sent to gateway, awaiting response
    COMPLETED,     // Successfully charged
    FAILED,        // Gateway declined or error
    REFUNDED,      // Full refund issued
    PARTIALLY_REFUNDED // Partial refund issued
}
