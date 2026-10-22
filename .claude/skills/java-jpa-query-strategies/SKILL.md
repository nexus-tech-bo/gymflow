---
name: java-jpa-query-strategies
description: Decide how to write Spring Data JPA repository queries - when to use built-in JpaRepository methods, derived query methods, @Query (JPQL/native), @EntityGraph, JOIN FETCH, projections, @Modifying and @Lock - and how to avoid N+1 and LazyInitializationException. Use when adding or reviewing a repository method, fixing N+1 or lazy-loading errors, writing filters/pagination/search, or choosing between derived methods, @Query and @EntityGraph.
---

# JPA Query Strategies: built-in vs derived vs @Query vs @EntityGraph

Examples use a fictional **library** domain:
`Book` → `@ManyToOne author`, `@ManyToOne publisher`, `@ManyToMany genres`; `Loan` → `@ManyToOne member`, `@ManyToOne book`.
All associations are `LAZY` (see `java-entity-modeling`).

## Decision guide

```
Need to load/save by primary key, count, delete, list everything?
  └─ YES → built-in JpaRepository method (findById, save, existsById, findAll(Pageable)…)

Simple condition on fields (AND/OR, =, <, between, like, in, true/false, order)?
And the method name stays readable (≈ 3–4 conditions max)?
  └─ YES → derived query method (findByIsbn, existsByEmail, findByMemberIdAndStatus…)

Also need related entities in the result (avoid N+1 / LazyInitializationException)?
  └─ Derived method or findById + @EntityGraph(attributePaths = …)

Optional filters (param may be null), OR across several fields, search, aggregates,
subqueries, COUNT(...) > 0 with complex logic, overlaps, NOT IN, custom ordering?
  └─ @Query (JPQL), with JOIN FETCH if you need relations

Bulk UPDATE/DELETE?
  └─ @Modifying + @Query

DB-specific feature (window functions, full-text, JSONB, CTEs)?
  └─ @Query(nativeQuery = true)

Read-only list that needs only a few columns?
  └─ DTO/interface projection
```

## 1. Built-in methods (`JpaRepository`)

```java
public interface GenreRepository extends JpaRepository<Genre, Long> {}
```

You get for free: `findById`, `findAll`, `findAll(Pageable)`, `findAll(Sort)`, `findAllById`, `save`, `saveAll`, `existsById`, `count`, `deleteById`, `delete`, `getReferenceById`.

Use them when the entity has no lazy relations you need in the response, or for writes.

**`getReferenceById`** returns a proxy without a SELECT — ideal to set an FK when you only have the id and already know it exists:

```java
loan.setBook(bookRepository.getReferenceById(request.bookId()));
```

(If the id may not exist, use `findById(...).orElseThrow(...)` so you return 404 instead of a FK violation.)

## 2. Derived query methods

Spring parses the method name. Best for simple, readable conditions:

```java
public interface BookRepository extends JpaRepository<Book, Long> {
    Optional<Book> findByIsbn(String isbn);
    boolean existsByIsbn(String isbn);
    boolean existsByTitleIgnoreCase(String title);
    List<Book> findByAuthorIdAndActiveTrue(Long authorId);
    Optional<Book> findByIdAndActiveTrue(Long id);
    long countByPublisherId(Long publisherId);
    List<Book> findTop10ByActiveTrueOrderByCreatedAtDesc();
}

public interface LoanRepository extends JpaRepository<Loan, Long> {
    boolean existsByBookIdAndStatus(Long bookId, LoanStatus status);
    List<Loan> findByMemberIdAndDueDateBetweenAndStatusNot(Long memberId, LocalDate from, LocalDate to, LoanStatus status);
    List<Loan> findByMemberIdAndLoanDateGreaterThanEqualOrderByLoanDateAsc(Long memberId, LocalDate from);
}
```

Keywords: `And`, `Or`, `Is/Equals`, `Not`, `Between`, `LessThan`, `GreaterThanEqual`, `After`, `Before`, `IsNull`, `IsNotNull`, `Like`, `Containing`, `StartingWith`, `IgnoreCase`, `In`, `NotIn`, `True`, `False`, `OrderBy…Asc/Desc`, `Top/First{n}`, `Distinct`.
Nested properties traverse relations: `findByAuthorId` → `book.author.id`.

**Use when**: the uniqueness checks (`existsBy…`), lookups by natural key, simple filters.

**Switch to `@Query` when**:
- The name exceeds ~4 conditions or stops being readable.
- A parameter is optional (`null` = "don't filter"). Derived methods would compare to `NULL` and match nothing.
- You need `OR` across fields mixed with `AND`, functions (`LOWER`, `CONCAT`), subqueries, or aggregates.

## 3. `@EntityGraph` — fetch relations without writing JPQL

Adds the listed associations to the SELECT as joins for **that method only**, leaving the entity mapping `LAZY`.

```java
public interface BookRepository extends JpaRepository<Book, Long> {

    // Override the built-in findById so the detail endpoint brings author + publisher in one query
    @EntityGraph(attributePaths = {"author", "publisher"})
    Optional<Book> findById(Long id);

    // Derived method + graph
    @EntityGraph(attributePaths = {"author", "genres"})
    List<Book> findByPublisherIdAndActiveTrue(Long publisherId);

    // Nested paths with dots: loan → book → author
    // (on LoanRepository)
}

public interface LoanRepository extends JpaRepository<Loan, Long> {
    @EntityGraph(attributePaths = {"member", "book.author"})
    List<Loan> findByMemberIdAndStatus(Long memberId, LoanStatus status);
}
```

Separate method names for different graphs of the same lookup — Spring ignores words between `find` and `By`:

```java
@EntityGraph(attributePaths = {"genres"})
Optional<Book> findBookGenresById(Long id);   // same query as findById, different graph

@EntityGraph(attributePaths = {"loans.member"})
Optional<Member> findMemberLoansById(Long id);
```

**Use when**:
- The query itself is simple (built-in or derived), you just need relations loaded.
- You want to keep derived-method readability and avoid N+1.

**Avoid when**:
- You fetch **more than one `List` collection** (`MultipleBagFetchException`) — use `Set`, or load the second collection in another query.
- You combine a **collection** graph with `Pageable` — Hibernate paginates in memory (`HHH90003004` warning). Paginate the root, then load collections by ids in a second query.

With JSpecify/nullness checks enabled, overriding `findById` may require `@NullMarked` on the method to match the parent signature.

## 4. `@Query` (JPQL)

Use for anything the method name can't express cleanly.

### Optional filters + pagination

```java
@Query("SELECT b FROM Book b " +
        "JOIN FETCH b.author a " +
        "LEFT JOIN FETCH b.publisher p " +
        "WHERE (:authorId IS NULL OR a.id = :authorId) AND " +
        "(:publisherId IS NULL OR p.id = :publisherId) AND " +
        "(:isActive IS NULL OR b.active = :isActive)")
Page<Book> findAllByFilters(@Param("authorId") Long authorId,
                            @Param("publisherId") Long publisherId,
                            @Param("isActive") Boolean isActive,
                            Pageable pageable);
```

- `(:param IS NULL OR field = :param)` makes every filter optional with one method.
- `JOIN FETCH` on **to-one** relations is safe with `Pageable`. Spring derives the count query automatically; if it fails (complex fetches), supply `countQuery = "SELECT COUNT(b) FROM Book b WHERE …"`.
- `LEFT JOIN FETCH` when the relation is nullable, `JOIN FETCH` when it is mandatory.

### Free-text search across several fields

```java
/** An empty {@code search} matches every book. */
@Query("SELECT b FROM Book b WHERE (:isActive IS NULL OR b.active = :isActive) AND (" +
        "LOWER(b.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
        "b.isbn LIKE CONCAT('%', :search, '%') OR " +
        "LOWER(b.author.name) LIKE LOWER(CONCAT('%', :search, '%')))")
Page<Book> search(@Param("search") String search, @Param("isActive") Boolean isActive, Pageable pageable);
```

Normalize in the service: `search == null ? "" : search.strip()`.

### Existence / overlap checks

```java
@Query("SELECT COUNT(l) > 0 FROM Loan l " +
        "WHERE l.book.id = :bookId " +
        "AND l.status NOT IN :excludedStatuses " +
        "AND l.loanDate < :endDate " +
        "AND l.dueDate > :startDate")
boolean existsOverlappingLoan(@Param("bookId") Long bookId,
                              @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate,
                              @Param("excludedStatuses") List<LoanStatus> excludedStatuses);
```

### Date range with ordering and fetched relations

```java
@Query("SELECT l FROM Loan l " +
        "JOIN FETCH l.member m " +
        "JOIN FETCH l.book b " +
        "WHERE (:memberId IS NULL OR m.id = :memberId) AND " +
        "l.loanDate >= :from AND l.loanDate < :to " +
        "ORDER BY l.loanDate ASC")
List<Loan> findByFiltersAndDateRange(@Param("memberId") Long memberId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);
```

Use half-open ranges (`>= from AND < to`) for dates/timestamps.

### Enum constants in JPQL

Refer to them by simple name when the enum is in the entity model: `WHERE m.role = Role.LIBRARIAN`, or pass as a parameter.

### Aggregates and DTO projections

```java
public record BooksPerGenre(String genre, long total) {}

@Query("SELECT new com.example.app.dto.report.BooksPerGenre(g.name, COUNT(b)) " +
        "FROM Book b JOIN b.genres g GROUP BY g.name ORDER BY COUNT(b) DESC")
List<BooksPerGenre> countBooksPerGenre();
```

Interface projections work with derived methods too:

```java
public interface BookTitleView { Long getId(); String getTitle(); }
List<BookTitleView> findByAuthorId(Long authorId);
```

Projections select only the needed columns and never trigger lazy loading — best for lists, dropdowns, and reports.

### Bulk updates

```java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE Loan l SET l.status = LoanStatus.OVERDUE WHERE l.status = LoanStatus.ACTIVE AND l.dueDate < :today")
int markOverdue(@Param("today") LocalDate today);
```

Needs a `@Transactional` caller. Bypasses entity callbacks, `@Version` and cascades — use it only for set-based changes.

### Native queries

```java
@Query(value = "SELECT * FROM books WHERE to_tsvector('english', title) @@ plainto_tsquery(:q)", nativeQuery = true)
List<Book> fullTextSearch(@Param("q") String q);
```

Last resort: ties you to one database and skips JPQL validation at startup.

## 5. Pessimistic locking

Serialize check-then-act operations on the same parent row (e.g. two librarians lending the last copy at once):

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT b FROM Book b WHERE b.id = :id")
Optional<Book> findByIdForUpdate(@Param("id") Long id);
```

```java
@Transactional
public LoanResponse createLoan(LoanRequest request) {
    var book = bookRepository.findByIdForUpdate(request.bookId())
            .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + request.bookId()));
    if (loanRepository.existsOverlappingLoan(book.getId(), request.startDate(), request.endDate(), List.of(LoanStatus.RETURNED)))
        throw new BusinessException("Book is already loaned for that period");
    ...
}
```

Use `@Version` (optimistic) for ordinary concurrent edits; pessimistic locks only where a conflict must be prevented, not just detected.

## Comparison

|                           | Built-in | Derived     | `@EntityGraph` | `@Query` JPQL   | Native     |
|---------------------------|----------|-------------|----------------|-----------------|------------|
| Boilerplate               | none     | method name | annotation     | JPQL string     | SQL string |
| Optional (`null`) filters | ✗       | ✗          | ✗ (inherits)  | ✓              | ✓         |
| Load relations            | ✗       | ✗          | ✓             | ✓ `JOIN FETCH` | manual     |
| Aggregates / subqueries   | ✗       | ✗          | ✗             | ✓              | ✓         |
| Validated at startup      | ✓       | ✓          | ✓             | ✓              | ✗         |
| Portable                  | ✓       | ✓          | ✓             | ✓              | ✗         |

## Avoiding N+1 and LazyInitializationException

Symptom: listing 50 books fires 1 query for books + 50 for authors; or `LazyInitializationException` when mapping outside a transaction.

Fix, in order of preference:
1. `@EntityGraph` on the repository method used by that endpoint.
2. `JOIN FETCH` inside a `@Query`.
3. A DTO projection if you only need a few columns.
4. `spring.jpa.properties.hibernate.default_batch_fetch_size: 50` as a safety net for collections.

Never fix it with `FetchType.EAGER` or `open-in-view: true`.
Verify with `spring.jpa.show-sql: true` (dev profile) and count the statements.

## Checklist

- [ ] Uniqueness checks use `existsBy…`, not `findBy…().isPresent()`
- [ ] Every endpoint that maps a relation loads it via graph / fetch join / projection
- [ ] Optional filters use `@Query` with `(:p IS NULL OR …)`
- [ ] No collection fetch combined with `Pageable`
- [ ] `@Param` on every named parameter
- [ ] Bulk writes use `@Modifying` with a transactional caller
