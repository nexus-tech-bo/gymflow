---
name: spring-layered-crud
description: 'Structure a new resource in a Spring Boot REST API as Entity -> Repository -> DTOs -> MapStruct Mapper -> Service (interface + impl) -> Controller, with Lombok, Bean Validation, custom exceptions and a global exception handler. Use when the user asks to add a new entity, model, table, resource, CRUD, endpoint, repository, service or controller, or asks how a layer should be written.'
---

# Spring Layered CRUD (Entity / Repository / Service / Controller)

Your goal is to add or modify a resource so every one in the codebase looks the same. Every resource is a vertical slice through the same layers:

```
HTTP ──> Controller ──> Service (interface) ──> ServiceImpl ──> Repository ──> DB
           │  DTO in/out        │                     │  Entity
           └─ @Valid Request    └─ returns Response   └─ Mapper: Entity <-> DTO
```

Rules of the flow:
- Controllers only speak DTOs. They never touch entities or repositories.
- Services receive Request DTOs / ids and return Response DTOs (or `Page<Response>`, `List<Response>`, `void`).
- Entities never leave the service layer.
- Errors are thrown as custom exceptions and translated to JSON by the global `@RestControllerAdvice`.

Before writing code, inspect the target project and adapt: base package, Spring Boot version, Lombok/MapStruct presence, existing exception classes, roles, migration tool, and naming of the closest existing resource. When the project already has an established style, follow it over this file. Examples use a placeholder `Product` resource with a `Category` relation, an `ADMIN` role and the `com.example.app` package; rename to the project's own.

## Requirements and assumptions

| Item               | Assumed                                                                                                                 | If different                                                                 |
|--------------------|-------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| Java / Spring Boot | Java 17+ (records), Spring Boot 3+ (`jakarta.*`)                                                                        | Spring Boot 2: `javax.*` imports; Java < 16: DTOs as Lombok `@Value` classes |
| Lombok             | Present                                                                                                                 | Write constructors/getters by hand; keep constructor injection               |
| MapStruct          | Present (`mapstruct` + `mapstruct-processor`, plus `lombok-mapstruct-binding` when both are used)                       | Hand-written `@Component` mapper with the same method names                  |
| Exceptions         | `ResourceNotFoundException`, `BadRequestException`, `BusinessException`, `ForbiddenException` + `@RestControllerAdvice` | Reuse the project's own; create these only if none exist (see section 7)     |
| Security           | Spring Security with method security (`@PreAuthorize`)                                                                  | Drop the annotations if there is no auth                                     |
| OpenAPI            | springdoc (`@Tag`, `@Operation`)                                                                                        | Drop the annotations if springdoc is absent                                  |
| Migrations         | Flyway or Liquibase                                                                                                     | Follow whatever the project uses; never `ddl-auto=update` in production      |
| Null-safety        | `@NullMarked` (JSpecify) on overridden repository methods, needed in Spring Boot 4 / Spring Data 4                      | Omit it on Spring Boot 3 or when JSpecify is not on the classpath            |

## Package layout

```
com.example.<app>/
├── entity/          <Name>.java                      JPA entities
├── enums/           <Name>Status.java, Role.java     enums stored as STRING
├── repository/      <Name>Repository.java            Spring Data interfaces
├── dto/<name>/      <Name>Request.java, <Name>Response.java   records
├── mapper/          <Name>Mapper.java                MapStruct interfaces
├── service/         <Name>Service.java               interface
├── service/impl/    <Name>ServiceImpl.java           implementation
├── controller/      <Name>Controller.java            REST endpoints
└── exception/       ResourceNotFoundException, BadRequestException,
                     BusinessException, ForbiddenException, UnauthorizedException,
                     ErrorResponse, GlobalExceptionHandler
```

Naming: singular entity (`Product`), plural table (`products`), plural URL (`/api/products`), DTO sub-package in lowercase (`dto/product`).

## 1. Entity

```java
package com.example.app.entity;

import com.example.app.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(columnNames = {"name"}))
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
```

Conventions:
- Lombok: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @EqualsAndHashCode(of = "id")`. Never `@Data` on entities (breaks lazy loading and `equals`/`hashCode` on collections).
- `Long id` with `GenerationType.IDENTITY`.
- Every relation is `fetch = FetchType.LAZY`; `@ManyToOne(optional = false, ...)` when required. Name FK columns explicitly with `@JoinColumn(name = "..._id")`.
- Collections: `@Builder.Default private List<X> items = new ArrayList<>();`. The inverse side uses `mappedBy`.
- Defaults that the builder must respect need `@Builder.Default`.
- Enums: `@Enumerated(EnumType.STRING)`, never ORDINAL.
- Unique business keys: declare in `@Table(uniqueConstraints = ...)` AND check in the service (`existsBy...`) to return a friendly error.
- Soft delete with an `active` flag when the row is referenced by history (customers, users); hard delete only for leaf data.
- Concurrency-sensitive rows (reservations, stock, balances) get `@Version private Long version;`.
- Audit timestamps via `@PrePersist`, `updatable = false`.
- Schema changes go in a new migration (Flyway: `src/main/resources/db/migration/V<n>__<description>.sql`; Liquibase: a new changeset); never rely on `ddl-auto`. If the project splits migrations by schema or module, put the table in the matching folder.

## 2. Repository

```java
package com.example.app.repository;

import com.example.app.entity.Product;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByName(String name);

    @NullMarked
    @EntityGraph(attributePaths = {"category"})
    Page<Product> findAll(Pageable pageable);

    @NullMarked
    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findById(Long id);

    @Query("SELECT p FROM Product p " +
            "JOIN FETCH p.category c " +
            "WHERE (:categoryId IS NULL OR c.id = :categoryId) AND " +
            "(:active IS NULL OR p.active = :active)")
    List<Product> findAllByFilters(@Param("categoryId") Long categoryId, @Param("active") Boolean active);
}
```

Conventions:
- Extend `JpaRepository<Entity, Long>`. No custom base repository.
- Prefer derived queries (`existsByName`, `findByCategoryIdAndActiveTrue`) for simple lookups.
- Avoid N+1: whatever the mapper reads from a lazy relation must be loaded by the query. Use `@EntityGraph(attributePaths = ...)` when overriding `findAll`/`findById` (mark overrides `@NullMarked` on Spring Boot 4), or `JOIN FETCH` in `@Query`.
- Optional filters in JPQL: `(:param IS NULL OR x.field = :param)`, always with `@Param`.
- Do not `JOIN FETCH` a collection inside a `Page` query (Hibernate paginates in memory). Fetch `@ManyToOne` only, or use a second query.
- Return `Optional<T>` for single results, `boolean` for existence checks (`existsBy...`, or `SELECT COUNT(x) > 0`).
- Repositories hold no business logic.

## 3. DTOs

Java records, one Request and one Response per resource (add more only when the shape really differs, e.g. `RegisterRequest`, `SummaryItem`).

```java
package com.example.app.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductRequest(
        @NotBlank
        String name,
        @NotNull
        Long categoryId
) {}
```

```java
package com.example.app.dto.product;

public record ProductResponse(
        Long id,
        String name,
        String categoryName,
        boolean active
) {}
```

- Bean Validation annotations live on the Request record; the controller triggers them with `@Valid`.
- Requests reference related entities by id (`categoryId`), never by nested object.
- Responses are flat; never expose passwords, internal flags or entire related entities.

## 4. Mapper (MapStruct)

```java
package com.example.app.mapper;

import com.example.app.dto.product.ProductRequest;
import com.example.app.dto.product.ProductResponse;
import com.example.app.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(source = "category.name", target = "categoryName")
    ProductResponse toResponse(Product product);

    List<ProductResponse> toResponseList(List<Product> products);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Product toEntity(ProductRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void toEntityUpdated(ProductRequest request, @MappingTarget Product product);
}
```

- `componentModel = "spring"` so it is injected like any bean.
- Method names: `toResponse`, `toResponseList`, `toEntity`, `toEntityUpdated` (update in place with `@MappingTarget`).
- Explicitly `ignore = true` the id, relations, and server-controlled fields; the service sets relations after loading them.
- Nested paths (`source = "account.email"`) for flattening.
- Gradle needs `mapstruct-processor` and `lombok-mapstruct-binding` as `annotationProcessor`.

## 5. Service

Interface in `service/`, implementation in `service/impl/`.

```java
package com.example.app.service;

import com.example.app.dto.product.ProductRequest;
import com.example.app.dto.product.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {
    Page<ProductResponse> findAllProducts(Pageable pageable);
    ProductResponse findProductById(Long productId);
    ProductResponse createProduct(ProductRequest request);
    ProductResponse updateProductById(Long productId, ProductRequest request);
    void deactivateProductById(Long productId);
}
```

```java
package com.example.app.service.impl;

import com.example.app.dto.product.ProductRequest;
import com.example.app.dto.product.ProductResponse;
import com.example.app.entity.Product;
import com.example.app.exception.BusinessException;
import com.example.app.exception.ResourceNotFoundException;
import com.example.app.mapper.ProductMapper;
import com.example.app.repository.CategoryRepository;
import com.example.app.repository.ProductRepository;
import com.example.app.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductMapper productMapper;

    @Override
    public Page<ProductResponse> findAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable).map(productMapper::toResponse);
    }

    @Override
    public ProductResponse findProductById(Long productId) {
        return productMapper.toResponse(getProductOrThrowById(productId));
    }

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        if (productRepository.existsByName(request.name()))
            throw new BusinessException("This product already exists");
        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));
        var product = productMapper.toEntity(request);
        product.setCategory(category);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse updateProductById(Long productId, ProductRequest request) {
        var product = getProductOrThrowById(productId);
        if (!product.getName().equals(request.name()) && productRepository.existsByName(request.name()))
            throw new BusinessException("This product already exists");
        productMapper.toEntityUpdated(request, product);
        return productMapper.toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public void deactivateProductById(Long productId) {
        var product = getProductOrThrowById(productId);
        product.setActive(false);
        productRepository.save(product);
    }

    private Product getProductOrThrowById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
    }
}
```

Conventions:
- `@Service @RequiredArgsConstructor` with `private final` dependencies (constructor injection, never `@Autowired` fields).
- Class-level `@Transactional(readOnly = true)`; each writing method overrides it with `@Transactional`.
- Method names spell out the resource: `findAll<Names>`, `find<Name>ById`, `create<Name>`, `update<Name>ById`, `deactivate<Name>ById` / `delete<Name>ById`.
- Private `get<Name>OrThrowById` helper for the repeated "load or 404".
- Use `var` for locals.
- All business validation lives here: uniqueness, state transitions, overlaps, ownership. Throw:
  - `ResourceNotFoundException` -> 404 (missing id).
  - `BadRequestException` -> 400 (invalid input not caught by Bean Validation, duplicate email, etc.).
  - `BusinessException` -> 409 (business rule violated: already exists, slot taken, invalid state transition).
  - `ForbiddenException` -> 403 (authenticated but not the owner).
- Ownership checks take the caller id/role as parameters (from the controller), not by reading `SecurityContextHolder` inside the service.
- Map to DTO before returning; the transaction is still open, so lazy relations loaded by the query are safe.

## 6. Controller

```java
package com.example.app.controller;

import com.example.app.dto.product.ProductRequest;
import com.example.app.dto.product.ProductResponse;
import com.example.app.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Products Controller")
public class ProductController {
    private final ProductService productService;

    @GetMapping
    @Operation(summary = "GET /api/products — list all products")
    public ResponseEntity<Page<ProductResponse>> findAllProducts(@PageableDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(productService.findAllProducts(pageable));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "GET /api/products/{productId} — get a product by id")
    public ResponseEntity<ProductResponse> findProductById(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.findProductById(productId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "POST /api/products — create a product (admin only)")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "PUT /api/products/{productId} — update a product by id")
    public ResponseEntity<ProductResponse> updateProductById(@PathVariable Long productId, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProductById(productId, request));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "DELETE /api/products/{productId} — deactivate a product by id")
    public ResponseEntity<Void> deactivateProductById(@PathVariable Long productId) {
        productService.deactivateProductById(productId);
        return ResponseEntity.noContent().build();
    }
}
```

Conventions:
- `@RestController @RequestMapping("/api/<plural>") @RequiredArgsConstructor`, inject only the service interface.
- OpenAPI: `@Tag` on the class, `@Operation(summary = "<VERB> <path> — <what it does>")` on every method.
- One-line bodies: delegate to the service and wrap in `ResponseEntity`.
- Status codes: `200 ok` for reads/updates, `201 CREATED` for creates, `204 noContent` for deletes/deactivations.
- `@Valid @RequestBody` on every request body.
- Lists that can grow are paginated with `Pageable` + `@PageableDefault(sort = "id", direction = Sort.Direction.ASC)` and return `Page<Response>`.
- Authorization with `@PreAuthorize("hasRole('X')")` / `hasAnyRole(...)`; public endpoints have none (and are registered as public in the security config if unauthenticated).
- "Me" endpoints resolve the authenticated user's id and pass it to the service; never trust an id from the body or path for the caller's identity. Resolve it the way the project already does (e.g. `Long.parseLong(auth.getName())` when the JWT subject is the user id, a custom principal, or `@AuthenticationPrincipal`).
- Path variables are named `{<name>Id}`, sub-resources are nested: `/api/categories/{categoryId}/products`.
- No try/catch in controllers; `GlobalExceptionHandler` builds the `ErrorResponse`.

## 7. Exceptions and global handler

If the project has no custom exceptions or `@RestControllerAdvice` yet, set them up with the `spring-error-handling` skill (exception hierarchy, error body, global handler, security 401/403). This skill only assumes those exceptions exist and that services throw them.

## Checklist for a new resource

1. Migration for the table, FKs and unique constraints.
2. `entity/<Name>.java` (+ enum in `enums/` if needed).
3. `repository/<Name>Repository.java` with fetch strategy for what the response needs.
4. `dto/<name>/<Name>Request.java` and `<Name>Response.java` records.
5. `mapper/<Name>Mapper.java` ignoring id/relations/server fields.
6. `service/<Name>Service.java` + `service/impl/<Name>ServiceImpl.java`.
7. `controller/<Name>Controller.java` with `@Tag`, `@Operation`, `@PreAuthorize`, `@Valid`.
8. Compile (`./gradlew compileJava`) so MapStruct reports unmapped target properties; fix every warning.
9. Add a service unit test (Mockito) for each business rule that throws.

## Antipatterns to reject

- Returning entities from controllers, or accepting entities as `@RequestBody`.
- Repository injected into a controller.
- `@Data` or `@ToString` on entities with relations; `FetchType.EAGER`.
- Business checks in the controller or the mapper.
- `try/catch` + manual `ResponseEntity.status(...)` for errors instead of throwing the custom exceptions.
- Unpaginated `findAll()` on tables that grow (orders, users, logs).
- `JOIN FETCH` of collections combined with `Pageable`.
- Field injection with `@Autowired`.
