package com.example.gymflow.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when the authenticated user may not perform the operation (403).
 */
public class ForbiddenException extends ApiException {
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
