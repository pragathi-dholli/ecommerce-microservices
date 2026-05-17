package com.ecommerce.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicatePaymentException extends RuntimeException {
    public DuplicatePaymentException(String orderNumber) {
        super("A payment already exists for order: " + orderNumber);
    }
}
