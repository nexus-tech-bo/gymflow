package com.example.gymflow.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when credentials are invalid (401).
 */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
