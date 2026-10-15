package com.example.gymflow.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base of the controlled domain errors; carries the HTTP status the global handler returns.
 */
@Getter
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
