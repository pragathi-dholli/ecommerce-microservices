package com.ecommerce.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String sku) {
        super("Insufficient stock for product SKU: " + sku);
    }
}
