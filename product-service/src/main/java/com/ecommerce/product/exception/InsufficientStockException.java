package com.ecommerce.product.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String sku, int requested, int available) {
        super(String.format(
            "Insufficient stock for SKU '%s'. Requested: %d, Available: %d",
            sku, requested, available
        ));
    }
}
