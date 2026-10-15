---
name: spring-custom-validator
description: 'Create custom Bean Validation (Jakarta Validation) constraints in a Spring Boot REST API: a single-field annotation (e.g. allowed values, format) or a class-level cross-field annotation (e.g. "field B is required when field A is X"), each with its ConstraintValidator nested in the same file, wired to the global exception handler so violations come back as 400 with {field, message}. Use when the user asks to add a custom validator, custom validation annotation, @Constraint, ConstraintValidator, cross-field / conditional validation, or to validate a DTO rule that @NotNull/@Size/@Pattern cannot express.'
---

# Spring Custom Validator (Jakarta Bean Validation)

Your goal is a reusable annotation that:
- Lives in its own package `validator/<constraintName>/` with the `ConstraintValidator` nested inside the annotation (one file per constraint).
- Is applied on request DTOs (records) together with `@Valid` in the controller.
- Produces violations the existing `@RestControllerAdvice` turns into `400` + `errors: [{field, message}]`.

Before writing code, inspect the target project and adapt:
- Existing validators (`grep -rl "ConstraintValidator" src/main`): copy their package layout, naming and style. Don't create a parallel convention.
- The global exception handler: which exceptions it maps (`MethodArgumentNotValidException`, `HandlerMethodValidationException`, `ConstraintViolationException`) and whether it reads only `getFieldErrors()`. If it does, **class-level violations must be attached to a property node** (section 3), or they are silently dropped from the response.
- Package base. Examples use `com.example.app`.

## 1. First ask: does a built-in cover it?

| Rule                                | Use                                                                       |
|-------------------------------------|---------------------------------------------------------------------------|
| Not null / not blank / size / range | `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Max`, `@Positive`             |
| Format (digits only, code, etc.)    | `@Pattern(regexp = "...")`, `@Email`                                      |
| Date in past / future               | `@Past`, `@Future`, `@FutureOrPresent`                                    |
| Fixed set of values                 | Bind to an `enum` field (Jackson rejects unknown values -> 400)           |
| Needs the DB (exists, unique)       | **Not a validator.** Check in the service and throw the 404/409 exception |
| Needs the current user / role       | **Not a validator.** Service layer                                        |

Only write a custom constraint when the rule is pure (depends only on the DTO's values) and none of the above expresses it.

## 2. Single-field constraint

Template (mirrors an "allowed values" validator):

```java
package com.example.app.validator.allowedStatus;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;
import java.util.Set;

@Documented
@Constraint(validatedBy = AllowedStatus.AllowedStatusValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedStatus {
    String message() default "The status you have entered is not permitted";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class AllowedStatusValidator implements ConstraintValidator<AllowedStatus, Long> {
        private static final Set<Long> ALLOWED = Set.of(1L, 2L);

        @Override
        public boolean isValid(Long value, ConstraintValidatorContext context) {
            if (value == null) return true; // nullness is @NotNull's job
            return ALLOWED.contains(value);
        }
    }
}
```

Rules:
- `message`, `groups`, `payload` are mandatory: without them Hibernate Validator fails at startup.
- `null` is **valid**. Combine with `@NotNull` when the field is required. This keeps each annotation single-purpose.
- The generic type `ConstraintValidator<Annotation, T>` must match the field type (`Long`, `String`, `LocalDate`...). A mismatch fails at runtime with `UnexpectedTypeException`.
- If the allowed values come from an enum, derive them from the enum (`ERole.DOCTOR.getId()`), never hardcode magic numbers.
- Parameterize via annotation attributes when the same rule is reused with different values, and read them in `initialize(...)`.

Usage:

```java
public record ThingRequest(
        @NotNull @AllowedStatus Long statusId
) {}
```

## 3. Class-level (cross-field) constraint

For rules that compare fields ("end after start", "specialty required if role is DOCTOR"). Put it on the record type.

```java
package com.example.app.validator.dateRange;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;
import java.time.temporal.Temporal;

@Documented
@Constraint(validatedBy = DateRange.DateRangeValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(DateRange.List.class)
public @interface DateRange {
    String message() default "The end must be after the start";

    String start();
    String end();

    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    @Target({ ElementType.TYPE })
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        DateRange[] value();
    }

    class DateRangeValidator implements ConstraintValidator<DateRange, Object> {
        private String startField;
        private String endField;
        private String message;

        @Override
        public void initialize(DateRange annotation) {
            this.startField = annotation.start();
            this.endField = annotation.end();
            this.message = annotation.message();
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean isValid(Object value, ConstraintValidatorContext context) {
            if (value == null) return true;
            var start = (Comparable<Temporal>) read(value, startField);
            var end = (Temporal) read(value, endField);
            if (start == null || end == null) return true; // @NotNull on the fields handles it
            if (start.compareTo(end) < 0) return true;

            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(endField)
                    .addConstraintViolation();
            return false;
        }

        // Works for records (accessor "end()") and JavaBeans (getter "getEnd()").
        private static Object read(Object target, String field) {
            var type = target.getClass();
            try {
                try {
                    return type.getMethod(field).invoke(target);
                } catch (NoSuchMethodException ignored) {}
                var getter = "get" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
                return type.getMethod(getter).invoke(target);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Field '" + field + "' not readable on " + type.getSimpleName(), e);
            }
        }
    }
}
```

Rules:
- **Always** `disableDefaultConstraintViolation()` + `addPropertyNode(field)` so the error is a *field* error pointing at the field the user must fix. A class-level violation without a node is a *global* error, which a handler that only reads `getFieldErrors()` drops (400 with an empty `errors` list).
- Different messages per branch are fine (build one violation per failing branch).
- A wrong field name is a programmer bug: throw `IllegalStateException` (fails loudly in tests). Don't `catch (Exception) { return false; }`, which turns a typo into a misleading 400 for every request.
- Field names are strings: keep them next to the record so a rename is caught, and cover them with a test (section 5).
- If the rule is only ever used by one DTO and is short, prefer a typed variant (`ConstraintValidator<DateRange, ThingRequest>`) without reflection.
- `@Repeatable` + `List` lets the same annotation be applied twice on one type.

Usage:

```java
@DateRange(start = "startTime", end = "endTime")
public record BookingRequest(
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime
) {}
```

## 4. Wiring

- Controller: `@Valid @RequestBody ThingRequest request`. Without `@Valid`, nothing runs.
- `@PathVariable` / `@RequestParam` constraints: Spring 6.1+ validates them automatically for `@Validated`-free controllers when the parameter carries a constraint, raising `HandlerMethodValidationException`. Make sure the handler maps it to 400.
- Nested objects / lists inside the DTO need `@Valid` on the field to cascade.
- Validators are created by Spring's `SpringConstraintValidatorFactory`, so constructor injection works, but keep validators pure (section 1). DB-backed checks belong in the service.

## 5. Test

One plain unit test per constraint, no Spring context:

```java
class DateRangeTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void should_reportEndField_when_endNotAfterStart() {
        var now = LocalDateTime.now();
        var violations = validator.validate(new BookingRequest(now, now));

        assertThat(violations).singleElement()
                .satisfies(v -> assertThat(v.getPropertyPath()).hasToString("endTime"));
    }

    @Test
    void should_pass_when_endAfterStart() {
        var now = LocalDateTime.now();
        assertThat(validator.validate(new BookingRequest(now, now.plusMinutes(30)))).isEmpty();
    }
}
```

The `getPropertyPath()` assertion is the one that catches a missing `addPropertyNode`.

## Checklist

- [ ] No built-in or service-layer check covers it.
- [ ] Package `validator/<name>/`, validator nested in the annotation.
- [ ] `message`, `groups`, `payload` present; `null` returns `true`.
- [ ] Class-level: `disableDefaultConstraintViolation()` + `addPropertyNode(...)`; bad field name throws.
- [ ] Applied on the DTO and `@Valid` on the controller parameter.
- [ ] Unit test asserting the property path.
