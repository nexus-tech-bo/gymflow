---
name: spring-pagination
description: 'Implement paginated, sortable and filterable list endpoints in a Spring Boot REST API with Spring Data Pageable, returning a stable JSON page shape via @EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO) on the main application class. Use when the user asks to paginate, add page/size/sort params, return Page<T>, add filters to a list endpoint, or fix the "Serializing PageImpl instances as-is is not supported" warning.'
---

# Spring Pagination (Pageable + VIA_DTO)

Your goal is to expose list endpoints that:
- Accept `?page=&size=&sort=` automatically through a `Pageable` parameter with sane defaults.
- Accept optional filters as `@RequestParam(required = false)`.
- Run one paginated query in the repository (with fetch joins for what the response needs).
- Map `Page<Entity>` to `Page<Response>` in the service.
- Serialize the result as a stable `PagedModel` JSON (`content` + `page` metadata), not the raw `PageImpl`.

Before writing code, inspect the target project and adapt: main class name, existing `Page<...>` endpoints, mapper names, and whether `spring.data.web.pageable.*` is already set in `application.yaml`. Examples use a placeholder `Product` resource with a `Category` relation and an `ADMIN` role; rename to the project's own resources and roles.

## Requirements and assumptions

| Item        | Assumed                                                                                            | If different                                                                                                                                                             |
|-------------|----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Spring Boot | 3.3+ (Spring Data 3.3+, where `VIA_DTO` exists)                                                    | On older versions skip step 1 and return `Page<T>` as-is, or a small `record PageResponse<T>(List<T> content, int number, int size, long totalElements, int totalPages)` |
| Persistence | Spring Data JPA                                                                                    | Spring Data MongoDB/JDBC: same controller/service code, repository syntax differs                                                                                        |
| Mapping     | A mapper bean with `toResponse(Entity)` (MapStruct or hand-written)                                | Any `Function<Entity, Response>` works with `Page.map`                                                                                                                   |
| Security    | Spring Security with `@PreAuthorize`                                                               | Drop the annotation if the project has no method security                                                                                                                |
| Null-safety | `@NullMarked` (JSpecify) on overridden repository methods, needed in Spring Boot 4 / Spring Data 4 | Omit it on Spring Boot 3 or when JSpecify is not on the classpath                                                                                                        |

## Flow

```
GET /api/<resources>?page=0&size=20&sort=name,asc&filterA=1
  └─ Controller: @RequestParam filters + @PageableDefault Pageable
       └─ Service: validate filter ids -> repository.findAllByFilters(..., pageable).map(mapper::toResponse)
            └─ Repository: @Query + JOIN FETCH + (:p IS NULL OR ...) -> Page<Entity>
  └─ Jackson: Page<Response> -> PagedModel (VIA_DTO) -> { content, page }
```

## 1. Main class configuration (required once)

Add `@EnableSpringDataWebSupport` with `VIA_DTO` next to `@SpringBootApplication`:

```java
package com.example.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class Application {

    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
```

Why:
- Without it, Spring serializes `PageImpl` directly and logs `Serializing PageImpl instances as-is is not supported`. That JSON leaks internal fields (`pageable`, `sort`, `first`, `last`, `numberOfElements`, `empty`...) and is not guaranteed stable across versions.
- With `VIA_DTO`, every `Page<T>` returned from a controller is converted to `PagedModel<T>` automatically. Controllers and services keep returning `Page<T>`; no wrapper class is needed.
- Check first that the annotation is not already on the main class (or on another `@Configuration`); declare it only once.

Resulting JSON:

```json
{
  "content": [
    { "id": 1, "name": "Cardiology" },
    { "id": 2, "name": "Dermatology" }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

Frontend contract: items are in `content`, pagination is in `page.number` (0-based), `page.size`, `page.totalElements`, `page.totalPages`.

Optional global limits in `application.yaml` (only if the defaults are not enough):

```yaml
spring:
  data:
    web:
      pageable:
        default-page-size: 20   # default when ?size is missing
        max-page-size: 100      # caps ?size=100000
```

## 2. Controller

```java
@GetMapping
@PreAuthorize("hasRole('ADMIN')")
@Operation(summary = "GET /api/products — list products, filter by ?categoryId={id}&isActive={bool}")
public ResponseEntity<Page<ProductResponse>> findAllProducts(
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Boolean isActive,
        @PageableDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable
) {
    return ResponseEntity.ok(productService.findAllProducts(categoryId, isActive, pageable));
}
```

Imports: `org.springframework.data.domain.Page`, `org.springframework.data.domain.Pageable`, `org.springframework.data.domain.Sort`, `org.springframework.data.web.PageableDefault`.

Conventions:
- Return type is `ResponseEntity<Page<Response>>`; never `List<Response>` for tables that grow.
- Always `@PageableDefault(sort = "id", direction = Sort.Direction.ASC)` so results are deterministic. Use a domain field when it is the natural order (e.g. `sort = "createdAt", direction = Sort.Direction.DESC` for a feed of orders). Default size is 10 unless `size` is set in the annotation or `spring.data.web.pageable.default-page-size`.
- `sort` must be an entity property path (`startTime`, `account.name`), not a column name or DTO field. A path that doesn't exist on the entity fails at runtime, so verify it against the entity.
- Filters are `@RequestParam(required = false)` with wrapper types (`Long`, `Boolean`, enums) so "absent" is `null`.
- Document filters in the `@Operation` summary: `filter by ?a={a}&b={b}`.
- Scoped listings (an owner only sees their own rows): override the owner filter with the authenticated user's id before calling the service; never trust a query param for the caller's identity. Resolve the id the way the project already does (e.g. `Long.parseLong(auth.getName())` when the JWT subject is the user id, a custom principal, or `@AuthenticationPrincipal`).
- Pageable is the last method parameter before `Authentication`.

Client usage: `GET /api/products?page=1&size=20&sort=name,desc&sort=id,asc&categoryId=3`.

## 3. Service

```java
public interface ProductService {
    Page<ProductResponse> findAllProducts(Long categoryId, Boolean isActive, Pageable pageable);
}
```

```java
@Override
public Page<ProductResponse> findAllProducts(Long categoryId, Boolean isActive, Pageable pageable) {
    if (categoryId != null) getCategoryOrThrowById(categoryId);
    return productRepository.findAllByFilters(categoryId, isActive, pageable)
            .map(productMapper::toResponse);
}
```

Conventions:
- Pass `Pageable` straight through; don't rebuild it unless enforcing a business rule.
- Convert with `Page.map(mapper::toResponse)`. It keeps the metadata (`totalElements`, `totalPages`) intact. Never build a `PageImpl` by hand from a `List`.
- Validate filter ids that reference other entities (`getXOrThrowById`) so an unknown id returns 404 instead of an empty page.
- Runs under the class-level `@Transactional(readOnly = true)`.

## 4. Repository

No filters: override `findAll(Pageable)` with an entity graph for the relations the mapper reads:

```java
@NullMarked
@EntityGraph(attributePaths = {"category"})
Page<Product> findAll(Pageable pageable);
```

With filters: JPQL with optional parameters and `JOIN FETCH` for `@ManyToOne` relations:

```java
@Query(value = "SELECT p FROM Product p " +
        "JOIN FETCH p.category c " +
        "WHERE (:categoryId IS NULL OR c.id = :categoryId) AND " +
        "(:isActive IS NULL OR p.active = :isActive)",
       countQuery = "SELECT COUNT(p) FROM Product p " +
        "WHERE (:categoryId IS NULL OR p.category.id = :categoryId) AND " +
        "(:isActive IS NULL OR p.active = :isActive)")
Page<Product> findAllByFilters(@Param("categoryId") Long categoryId, @Param("isActive") Boolean isActive, Pageable pageable);
```

Conventions:
- `Pageable` is the last parameter; Spring Data applies `LIMIT/OFFSET` and `ORDER BY` from it. Don't hardcode `ORDER BY` in a paginated `@Query`, it conflicts with the client's `sort`.
- Optional filters: `(:param IS NULL OR x.field = :param)`, always with `@Param`.
- Add an explicit `countQuery` without `FETCH` when the main query uses `JOIN FETCH`. Spring Data otherwise derives the count from the main query, and with fetch joins that can fail or come out slow.
- Use `LEFT JOIN FETCH` for optional relations (nullable FK), otherwise rows without the relation disappear from the page.
- Only `JOIN FETCH` single-valued relations (`@ManyToOne`, `@OneToOne`). Fetching a collection in a paginated query makes Hibernate load everything and paginate in memory (`HHH90003004` warning). If the response needs a collection, page the ids first, or load the collection with a second query.
- Use `List<T>` (not `Page<T>`) only for bounded sets: date-range views, dropdowns, "all active items of one category".

## Checklist

1. `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` present once on the main class.
2. Controller returns `ResponseEntity<Page<Response>>` with `@PageableDefault(sort = ..., direction = ...)`, and optional filters are `@RequestParam(required = false)`.
3. The default `sort` property exists on the entity.
4. The service validates filter ids and maps with `.map(mapper::toResponse)`.
5. The repository fetches what the mapper reads, has a `countQuery` when using `JOIN FETCH`, and has no collection fetch and no hardcoded `ORDER BY`.
6. Smoke test: `GET ...?page=0&size=2&sort=id,desc` returns `content` with 2 items and `page.totalElements` equal to the real count.

## Antipatterns to reject

- Returning `List<T>` from `findAll()` on growing tables, or slicing a list in memory with `subList`.
- Custom `PageResponse<T>` wrapper classes when `VIA_DTO` already provides a stable shape.
- `new PageImpl<>(list, pageable, list.size())` after loading everything.
- Returning `Page<Entity>` from a controller.
- `JOIN FETCH` of a collection combined with `Pageable`.
- `@PageableDefault` without `sort` (non-deterministic order across pages).
- Accepting `page`/`size` as manual `@RequestParam int` and building `PageRequest.of(...)` in the controller.
