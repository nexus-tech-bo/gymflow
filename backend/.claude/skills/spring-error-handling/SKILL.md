---
name: spring-error-handling
description: 'Implement controlled (expected) error handling in a Spring Boot REST API: a small custom exception hierarchy, a single JSON error body, and a @RestControllerAdvice that maps domain, validation, request-parsing, persistence and security exceptions to the right HTTP status without leaking internals. Use when the user asks to handle errors, add custom exceptions, a global exception handler, consistent error responses, validation error messages, or fix endpoints returning 500 for client errors.'
---

# Spring Error Handling (Controlled Errors)

Your goal is that every failure leaves the API as a predictable JSON body with the correct status:
- **Expected errors** (missing resource, invalid input, broken business rule, no permission) are thrown as custom exceptions from the service layer and mapped to 4xx.
- **Framework errors caused by the client** (bad JSON, wrong param type, missing param, unknown route, wrong HTTP method) are mapped to 4xx instead of falling into a 500.
- **Unexpected errors** are logged with the stack trace and returned as a generic 500 with no internal details.

Before writing code, inspect the target project and adapt:
- Existing exceptions, `@RestControllerAdvice`, and error DTO: extend them, don't create a parallel set.
- Status already used for business-rule violations (409, 422, 406...). Keep the existing one.
- Whether the API already returns `ProblemDetail` (RFC 9457), or `spring.mvc.problemdetails.enabled=true` is set. If so, use the variant at the end of this file.
- Spring Security presence: 401/403 raised in filters never reach the advice (see section 5).
- Package base. Examples use `com.example.app`.

## Architecture

```
Controller ──> Service ──throws──> ApiException subclass (status + message)
    │                                   │
    │ @Valid fails, bad JSON, bad param │
    ▼                                   ▼
GlobalExceptionHandler (@RestControllerAdvice)
    ├─ ApiException                 -> its own status, its message
    ├─ validation / parsing errors  -> 400 with field details
    ├─ routing errors               -> 404 / 405 / 415
    ├─ persistence conflicts        -> 409 (generic message)
    ├─ AccessDenied (method level)  -> 403
    └─ Exception                    -> 500, logged, fixed message
Security filter chain (outside MVC)
    ├─ AuthenticationEntryPoint     -> 401 same JSON shape
    └─ AccessDeniedHandler          -> 403 same JSON shape
```

## 1. Exception hierarchy

One abstract base that carries the HTTP status, and one small subclass per outcome. The handler needs a single method for all of them.

```java
package com.example.app.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
```

```java
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}

public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}

public class BusinessException extends ApiException {      // broken business rule
    public BusinessException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}

public class ForbiddenException extends ApiException {     // authenticated, but not allowed on this resource
    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
```

Each class goes in its own file in `exception/`. Add a new subclass only for a new HTTP outcome, not for every situation: `ResourceNotFoundException("Product not found with id: " + id)` covers every entity.

When to throw which:

| Situation                                                                                                                      | Exception                                            | Status |
|--------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------|--------|
| Id or key does not exist                                                                                                       | `ResourceNotFoundException`                          | 404    |
| Input is syntactically valid but wrong and Bean Validation can't express it (end before start, duplicate email in the request) | `BadRequestException`                                | 400    |
| Request is valid, but the current state forbids it (already exists, slot taken, cancelling a closed order)                     | `BusinessException`                                  | 409    |
| Authenticated user is not the owner, or lacks rights on this specific row                                                      | `ForbiddenException`                                 | 403    |
| Programming error, broken invariant, external system down                                                                      | let it propagate (or wrap in an unchecked exception) | 500    |

Rules:
- Throw from the **service** layer. Controllers don't catch, mappers don't validate.
- Messages are for the API consumer: clear, in one language, no stack traces, SQL, class names or secrets.
- Load-or-404 lives in one private helper per service: `repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id))`.
- Check uniqueness with `existsBy...` before saving to return a friendly 409. Keep the DB unique constraint as the real guarantee (section 4 covers the race).
- Never catch an exception just to log it and rethrow it, and never swallow one silently (`catch (Exception e) {}`).
- Catch only when you can translate: e.g. wrap a third-party client failure into a meaningful exception, keeping the cause (`new ExternalServiceException("Payment provider unavailable", e)`).

## 2. Error body

```java
package com.example.app.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp,
        List<FieldErrorItem> errors
) {
    public record FieldErrorItem(String field, String message) {}

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message, path, LocalDateTime.now(), null);
    }

    public static ErrorResponse of(HttpStatus status, String message, String path, List<FieldErrorItem> errors) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message, path, LocalDateTime.now(), errors);
    }
}
```

Example responses:

```json
{ "status": 404, "error": "Not Found", "message": "Product not found with id: 42",
  "path": "/api/products/42", "timestamp": "2026-10-08T10:15:30" }
```

```json
{ "status": 400, "error": "Bad Request", "message": "Validation failed",
  "path": "/api/products", "timestamp": "2026-10-08T10:15:30",
  "errors": [ { "field": "name", "message": "must not be blank" },
              { "field": "price", "message": "must be greater than 0" } ] }
```

The frontend can always read `message` and, for 400s, map `errors[].field` to form inputs.

## 3. Global handler

```java
package com.example.app.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // --- Controlled domain errors ---------------------------------------------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest req) {
        log.debug("Controlled error {}: {}", ex.getStatus().value(), ex.getMessage());
        return build(ex.getStatus(), ex.getMessage(), req);
    }

    // --- Validation -----------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)   // @Valid @RequestBody
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorResponse.FieldErrorItem(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Validation failed", req.getRequestURI(), errors));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)  // constraints on @RequestParam / @PathVariable (Spring 6.1+)
    public ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
        var errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new ErrorResponse.FieldErrorItem(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Validation failed", req.getRequestURI(), errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)      // @Validated on a service/bean
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        var errors = ex.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.FieldErrorItem(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST, "Validation failed", req.getRequestURI(), errors));
    }

    // --- Malformed requests ---------------------------------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)   // invalid JSON, wrong enum value, wrong date format
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class) // /products/abc where a Long is expected
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'", req);
    }

    // --- Routing --------------------------------------------------------------

    @ExceptionHandler(NoResourceFoundException.class)          // unknown URL (Spring 6.1+)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Resource not found", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method " + ex.getMethod() + " not supported", req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type", req);
    }

    // --- Persistence ----------------------------------------------------------

    @ExceptionHandler(DataIntegrityViolationException.class)   // unique/FK/not-null violated at DB level
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "The operation conflicts with existing data", req);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class) // @Version mismatch
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "The resource was modified by another request, retry", req);
    }

    // --- Security (method level: @PreAuthorize) -------------------------------

    @ExceptionHandler(AccessDeniedException.class)             // also covers AuthorizationDeniedException (subclass)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "Access denied", req);
    }

    // --- Fallback -------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", req);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, req.getRequestURI()));
    }
}
```

Adapt to the project:
- Without Spring Security, remove the `AccessDeniedException` handler and its import.
- On Spring Boot < 3.2, remove `HandlerMethodValidationException` and `NoResourceFoundException`; unknown routes are then served by `/error`.
- Without JPA/Spring Data, remove the persistence handlers.

Logging rules:
- 4xx controlled errors: `debug` (or nothing). They are normal traffic, not incidents.
- DB conflicts: `warn` with the root cause, never in the response.
- 500: `error` **with the exception object** as the last argument so the stack trace is printed.
- Never log passwords, tokens or full request bodies.

## 4. Persistence races

The `existsBy...` check in the service gives the friendly message, but two concurrent requests can both pass it. The DB unique constraint stops the second one, and `DataIntegrityViolationException` turns it into a 409 instead of a 500. Keep both:

```java
if (productRepository.existsByName(request.name()))
    throw new BusinessException("A product with this name already exists");
return productMapper.toResponse(productRepository.save(product)); // DB constraint is the real guard
```

For rows with `@Version`, an `OptimisticLockingFailureException` means somebody else updated it first. Return 409 and let the client reload; don't retry blindly on writes the user confirmed.

## 5. Security errors outside the controller

Exceptions raised in the security filter chain (missing/invalid token, URL rules in `authorizeHttpRequests`) never reach `@RestControllerAdvice`. Return the same JSON shape from the entry point and access-denied handler:

```java
http.exceptionHandling(ex -> ex
        .authenticationEntryPoint((req, res, e) -> writeError(res, req, HttpStatus.UNAUTHORIZED, "Authentication required"))
        .accessDeniedHandler((req, res, e) -> writeError(res, req, HttpStatus.FORBIDDEN, "Access denied")));
```

```java
private void writeError(HttpServletResponse res, HttpServletRequest req, HttpStatus status, String message) throws IOException {
    res.setStatus(status.value());
    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(res.getOutputStream(), ErrorResponse.of(status, message, req.getRequestURI()));
}
```

`objectMapper` is the injected Spring bean, so `LocalDateTime` is serialized the same way as in the advice. On Spring Boot 4 (Jackson 3), inject `tools.jackson.databind.json.JsonMapper` instead.

## 6. ProblemDetail variant (RFC 9457)

If the project prefers the standard `application/problem+json`, keep the same exception hierarchy and replace the custom body:

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }
}
```

`ResponseEntityExceptionHandler` already maps validation, parsing, routing and media-type errors. Override `handleMethodArgumentNotValid` to add field errors with `problem.setProperty("errors", ...)`. Pick one format per API; never mix `ErrorResponse` and `ProblemDetail`.

## 7. Verifying

One MockMvc slice test per category is enough to lock the contract:

```java
@WebMvcTest(ProductController.class)
class ProductControllerErrorTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductService productService;

    @Test
    void notFoundReturns404Json() throws Exception {
        when(productService.findProductById(42L)).thenThrow(new ResourceNotFoundException("Product not found with id: 42"));
        mvc.perform(get("/api/products/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 42"));
    }

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").exists());
    }
}
```

Adjust for the project: add `@WithMockUser` or exclude security autoconfiguration when endpoints are protected. On Spring Boot < 3.4 use `@MockBean` instead of `@MockitoBean`.

Manual smoke checks: unknown id gives 404, empty body gives 400 with `errors`, `/api/products/abc` gives 400, `DELETE` on a GET-only route gives 405, a duplicate unique value gives 409, no token gives 401 JSON, the wrong role gives 403 JSON.

## Checklist

1. `ApiException` base + one subclass per HTTP outcome; services throw them, controllers don't catch.
2. One `ErrorResponse` shape (or `ProblemDetail`) for every error, including 401/403 from the filter chain.
3. The handler covers domain, validation, parsing, routing, persistence, security and the fallback.
4. The 500 handler logs the stack trace and returns a fixed message.
5. No internal details (SQL, class names, stack traces) in any response.
6. DB unique constraints back every `existsBy...` check.

## Antipatterns to reject

- `try/catch` in controllers that builds `ResponseEntity.status(...)` by hand.
- Returning `null`, `Optional`, or an empty object to signal "not found" from a service to a controller.
- Throwing `RuntimeException("...")` or `ResponseStatusException` scattered across services instead of the domain exceptions.
- `ex.getMessage()` of an unexpected exception sent to the client.
- One exception class per entity (`ProductNotFoundException`, `CategoryNotFoundException`...) when one parameterized message does it.
- 500 for client mistakes (bad JSON, wrong param type, unknown route).
- Catching `Exception` in a service just to log and rethrow, or swallowing it.
- Different error shapes from the advice and from the security filter chain.
