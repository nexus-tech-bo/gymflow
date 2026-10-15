package com.example.gymflow.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown for invalid input that Bean Validation cannot express (400).
 */
public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
