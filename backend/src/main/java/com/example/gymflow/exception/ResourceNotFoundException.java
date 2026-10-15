package com.example.gymflow.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an id or key does not exist (404).
 */
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
