package com.ecommerce.product.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidProductOperationException extends RuntimeException {
    public InvalidProductOperationException(String message) {
        super(message);
    }
}
