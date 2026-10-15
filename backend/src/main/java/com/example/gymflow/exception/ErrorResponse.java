package com.example.gymflow.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Error body shared by every endpoint: {@code {status, message, timestamp, errors}}.
 * {@code errors} (field to message) is only present on validation failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String message,
        Instant timestamp,
        Map<String, String> errors
) {
    public static ErrorResponse of(HttpStatus status, String message) {
        return of(status, message, null);
    }

    public static ErrorResponse of(HttpStatus status, String message, Map<String, String> errors) {
        return new ErrorResponse(status.value(), message, Instant.now(), errors);
    }
}
