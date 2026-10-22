---
name: java-mvc-project-structure
description: Layered MVC structure for a Spring Boot REST backend - package layout, responsibilities of controller/service/repository/dto/mapper/exception layers, naming conventions, request/response records, MapStruct mappers, validation, transactions and a global error format. Use when creating a new feature/module end to end, adding an endpoint, scaffolding a new Spring Boot project, or deciding where a class belongs.
---

# Java MVC Project Structure (Spring Boot REST)

A layered, package-by-layer structure for REST APIs built with Spring Boot, Spring Data JPA, Lombok, MapStruct and Jakarta Validation.
Examples use a fictional **library** domain (`Book`, `Author`, `Member`, `Loan`).

## Package layout

```
com.example.<app>
├── <App>Application.java
├── config/          # @Configuration classes: security, CORS, OpenAPI, WebSocket, beans (Clock, PasswordEncoder)
├── controller/      # @RestController — HTTP only
├── dto/
│   ├── book/        # one sub-package per feature
│   │   ├── BookRequest.java
│   │   ├── BookResponse.java
│   │   └── BookSummaryItem.java
│   └── loan/
├── entity/          # @Entity classes
├── enums/           # domain enums (LoanStatus, Role)
├── exception/       # ApiException hierarchy, ErrorResponse, GlobalExceptionHandler
├── mapper/          # MapStruct interfaces
├── middleware/      # servlet filters (JwtAuthFilter)
├── repository/      # Spring Data interfaces
├── security/        # JwtUtil, SecurityUtils, user checks
├── service/         # service interfaces
│   └── impl/        # service implementations
├── util/            # stateless static helpers
├── validator/       # custom constraint annotations, one sub-package each
└── websocket/       # STOMP events, publishers, interceptors
src/main/resources
├── application.yaml
├── application-dev.yaml
└── db/migration/    # Flyway V{n}__description.sql
```

## Request flow

```
HTTP → Controller → Service (interface) → ServiceImpl → Repository → DB
         │  @Valid DTO          │ business rules, @Transactional
         ▼                      ▼
   ResponseEntity<DTO>   Mapper (entity ↔ DTO)
```

| Layer      | Does                                                                                           | Never does                                           |
|------------|------------------------------------------------------------------------------------------------|------------------------------------------------------|
| Controller | Routing, `@Valid`, auth annotations, status codes, OpenAPI docs                                | Business logic, repository calls, returning entities |
| Service    | Business rules, uniqueness checks, transactions, orchestrating repositories, publishing events | Touch `HttpServletRequest`, build `ResponseEntity`   |
| Repository | Queries                                                                                        | Business logic                                       |
| Mapper     | Entity ↔ DTO conversion                                                                        | Lookups, validation                                  |
| DTO        | Shape of the API contract + validation constraints                                             | Behavior                                             |

## Naming conventions

| Thing           | Pattern                                      | Example                                                                |
|-----------------|----------------------------------------------|------------------------------------------------------------------------|
| Controller      | `<Entity>Controller`                         | `BookController`                                                       |
| Service         | `<Entity>Service` + `<Entity>ServiceImpl`    | `BookService`, `BookServiceImpl`                                       |
| Repository      | `<Entity>Repository`                         | `BookRepository`                                                       |
| Mapper          | `<Entity>Mapper`                             | `BookMapper`                                                           |
| Input DTO       | `<Entity>Request`, `<Action><Entity>Request` | `BookRequest`, `ReturnLoanRequest`                                     |
| Output DTO      | `<Entity>Response`, `<Entity>SummaryItem`    | `BookResponse`                                                         |
| Service methods | verb + entity + qualifier                    | `findAllBooks`, `findBookById`, `updateBookById`, `deactivateBookById` |
| Private lookups | `get<Entity>OrThrowById`                     | `getBookOrThrowById`                                                   |
| Base path       | `/api/<plural-kebab>`                        | `/api/books`, `/api/loan-items`                                        |

## DTOs — Java records

```java
public record BookRequest(
        @NotBlank @Size(max = 200)
        String title,
        @NotBlank
        String isbn,
        @NotNull
        Long authorId,
        @NotNull
        Long publisherId,
        Set<Long> genreIds
) {}

public record BookResponse(
        Long id,
        String title,
        String isbn,
        AuthorResponse author,
        String publisherName,
        boolean active
) {}
```

- Records are immutable, concise, and work with Jackson and MapStruct out of the box.
- Requests reference related entities **by id**; responses embed small nested DTOs or flattened fields.
- Cross-field rules use a class-level custom constraint (see *Custom validators*).

## Controller

```java
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@Tag(name = "Books", description = "Books Controller")
public class BookController {
    private final BookService bookService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "GET /api/books?search={text}&isActive={bool} — list books")
    public ResponseEntity<Page<BookResponse>> findAllBooks(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(bookService.findAllBooks(search, isActive, pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "POST /api/books — create a book")
    public ResponseEntity<BookResponse> createBook(@Valid @RequestBody BookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookService.createBook(request));
    }

    @GetMapping("/{bookId}")
    @Operation(summary = "GET /api/books/{bookId} — get a book by id")
    public ResponseEntity<BookResponse> findBookById(@PathVariable Long bookId) {
        return ResponseEntity.ok(bookService.findBookById(bookId));
    }

    @PutMapping("/{bookId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookResponse> updateBookById(@PathVariable Long bookId, @Valid @RequestBody BookRequest request) {
        return ResponseEntity.ok(bookService.updateBookById(bookId, request));
    }

    @DeleteMapping("/{bookId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateBookById(@PathVariable Long bookId) {
        bookService.deactivateBookById(bookId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{bookId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookResponse> activateBookById(@PathVariable Long bookId) {
        return ResponseEntity.ok(bookService.activateBookById(bookId));
    }
}
```

Status codes: `200` read/update, `201` create, `204` delete/no body, errors via exceptions.
Get the current user with an `Authentication auth` parameter (`auth.getName()` is the JWT subject).
Sub-resources go under the parent: `GET /api/authors/{authorId}/books`.

## Service

```java
public interface BookService {
    Page<BookResponse> findAllBooks(String search, Boolean isActive, Pageable pageable);
    BookResponse createBook(BookRequest request);
    BookResponse findBookById(Long bookId);
    BookResponse updateBookById(Long bookId, BookRequest request);
    void deactivateBookById(Long bookId);
    BookResponse activateBookById(Long bookId);
}

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)          // default for every method
public class BookServiceImpl implements BookService {
    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final BookMapper bookMapper;

    @Override
    @Transactional                        // writes override readOnly
    public BookResponse createBook(BookRequest request) {
        if (bookRepository.existsByIsbn(request.isbn()))
            throw new BusinessException("Book with ISBN " + request.isbn() + " already exists");
        var author = authorRepository.findById(request.authorId())
                .orElseThrow(() -> new ResourceNotFoundException("Author not found with id: " + request.authorId()));
        var book = Book.builder()
                .title(request.title())
                .isbn(request.isbn())
                .author(author)
                .build();
        return bookMapper.toResponse(bookRepository.save(book));
    }

    @Override
    @Transactional
    public BookResponse updateBookById(Long bookId, BookRequest request) {
        var book = getBookOrThrowById(bookId);
        if (!book.getIsbn().equals(request.isbn()) && bookRepository.existsByIsbn(request.isbn()))
            throw new BusinessException("Book with ISBN " + request.isbn() + " already exists");
        bookMapper.updateEntity(request, book);
        return bookMapper.toResponse(bookRepository.save(book));
    }

    @Override
    @Transactional
    public void deactivateBookById(Long bookId) {
        getBookOrThrowById(bookId).setActive(false);
    }

    private Book getBookOrThrowById(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + bookId));
    }
}
```

- Constructor injection via `@RequiredArgsConstructor` + `private final` fields. No `@Autowired` on fields.
- Class-level `@Transactional(readOnly = true)`, method-level `@Transactional` for writes.
- Validate uniqueness with `existsBy...` before saving, and still rely on DB unique constraints (the handler maps `DataIntegrityViolationException` to 409).
- For check-then-act races on a parent row (e.g. "no overlapping loans for this book"), lock it with a `@Lock(PESSIMISTIC_WRITE)` repository method first.
- Inject `java.time.Clock` instead of calling `LocalDateTime.now()` directly when logic depends on "now" — it makes tests deterministic.
- Side effects that must happen only after commit (WebSocket push, emails) are published as events: `ApplicationEventPublisher.publishEvent(...)` + `@TransactionalEventListener(phase = AFTER_COMMIT)`.

## Mapper (MapStruct)

```java
@Mapper(componentModel = "spring", uses = AuthorMapper.class)
public interface BookMapper {
    @Mapping(target = "publisherName", source = "publisher.name")
    BookResponse toResponse(Book book);

    List<BookResponse> toResponseList(List<Book> books);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "publisher", ignore = true)
    @Mapping(target = "genres", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(BookRequest request, @MappingTarget Book book);
}
```

- `componentModel = "spring"` so mappers are injectable beans.
- Ignore ids, relations, audit and version fields explicitly — set relations in the service after loading them.
- Use `Page.map(mapper::toResponse)` for paged results.
- Gradle needs `lombok-mapstruct-binding` in `annotationProcessor` so MapStruct sees Lombok getters/builders.

## Exceptions & error format

```java
@Getter
public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;
    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String message) { super(HttpStatus.NOT_FOUND, message); }
}
// BadRequestException (400), UnauthorizedException (401), ForbiddenException (403), BusinessException (409)
```

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int status, String error, String message, String path,
                            LocalDateTime timestamp, List<FieldErrorItem> errors) {
    public record FieldErrorItem(String field, String message) {}

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return of(status, message, path, null);
    }
    public static ErrorResponse of(HttpStatus status, String message, String path, List<FieldErrorItem> errors) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message, path, LocalDateTime.now(), errors);
    }
}
```

A single `@RestControllerAdvice GlobalExceptionHandler` converts everything into `ErrorResponse`
(full handler in the `java-common-config` skill). Services throw `ApiException` subclasses; they never return error codes.

## Custom validators

One sub-package per constraint under `validator/`, annotation and validator in the same file:

```java
@Documented
@Constraint(validatedBy = DateRange.DateRangeValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DateRange {
    String message() default "End date must be after start date";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    interface Request {               // records implement it; accessors satisfy it
        LocalDate startDate();
        LocalDate endDate();
    }

    class DateRangeValidator implements ConstraintValidator<DateRange, Request> {
        @Override
        public boolean isValid(Request value, ConstraintValidatorContext ctx) {
            if (value == null || value.startDate() == null || value.endDate() == null) return true;
            if (value.endDate().isAfter(value.startDate())) return true;
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("endDate").addConstraintViolation();
            return false;
        }
    }
}

@DateRange
public record LoanRequest(@NotNull Long bookId, @NotNull LocalDate startDate, @NotNull LocalDate endDate)
        implements DateRange.Request {}
```

## Configuration

- `application.yaml` holds defaults with `${ENV_VAR}` placeholders; secrets come from env vars or a git-ignored `.env` (`spring.config.import: optional:file:.env[.properties]`).
- `application-dev.yaml` for local-only settings (`show-sql`, SQL bind logging).
- `spring.jpa.open-in-view: false`, `spring.jpa.hibernate.ddl-auto: none`, Flyway owns the schema.

## New feature checklist

1. Migration `V{n}__create_<table>.sql`
2. `entity/<Entity>.java` (see `java-entity-modeling`)
3. `repository/<Entity>Repository.java` (see `java-jpa-query-strategies`)
4. `dto/<feature>/<Entity>Request.java`, `<Entity>Response.java`
5. `mapper/<Entity>Mapper.java`
6. `service/<Entity>Service.java` + `service/impl/<Entity>ServiceImpl.java`
7. `controller/<Entity>Controller.java` with `@PreAuthorize` + `@Operation`
8. Tests for service rules and controller contract
