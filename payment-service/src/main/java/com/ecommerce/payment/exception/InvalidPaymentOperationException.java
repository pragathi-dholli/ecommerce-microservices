package com.ecommerce.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidPaymentOperationException extends RuntimeException {
    public InvalidPaymentOperationException(String message) {
        super(message);
    }
}
