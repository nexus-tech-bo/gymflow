package com.example.gymflow.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a business rule forbids the operation in the current state (409).
 */
public class BusinessException extends ApiException {
    public BusinessException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
