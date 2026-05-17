package com.ecommerce.order.model;

public enum OrderStatus {
    PENDING,           // Created, awaiting payment
    CONFIRMED,         // Payment succeeded, stock reserved
    PROCESSING,        // Being packed / fulfilled
    SHIPPED,           // Dispatched to carrier
    DELIVERED,         // Received by customer
    CANCELLED,         // Cancelled before shipment
    REFUNDED           // Refund processed
}
