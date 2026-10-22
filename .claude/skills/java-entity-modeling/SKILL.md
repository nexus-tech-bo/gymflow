---
name: java-entity-modeling
description: Model JPA/Hibernate entities in a Spring Boot backend - field mapping, Lombok setup, enums, auditing, optimistic locking, and every relationship type (one-to-one, one-to-many, many-to-one, many-to-many, join entity with extra columns). Use when creating or changing an @Entity, adding a relationship between entities, designing a table, or writing the matching Flyway migration.
---

# Java Entity Modeling (JPA / Hibernate)

Conventions for modeling persistence entities in a Spring Boot + Spring Data JPA project.
Examples use a fictional **library** domain: `Author`, `Book`, `Publisher`, `Genre`, `Member`, `MemberProfile`, `Loan`.
Replace names with your own domain; the patterns stay the same.

## Base template

Every entity follows the same shape:

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "books")
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, unique = true, updatable = false)
    private String isbn;

    @Column(length = 2000)
    private String synopsis;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
```

Rules:

- **Never use `@Data`** on entities. It generates `equals/hashCode/toString` over all fields, which triggers lazy loading and infinite recursion across bidirectional relations.
- `@EqualsAndHashCode(of = "id")` — identity is the primary key only.
- `@Builder` + `@NoArgsConstructor` + `@AllArgsConstructor` together: JPA needs the no-args constructor, the builder needs the all-args one.
- `@Builder.Default` on every field with an initializer (`active = true`, `new ArrayList<>()`), otherwise the builder sets it to `null`/`false`.
- Table names are plural `snake_case` (`books`, `loan_items`); column names derive from camelCase fields (Hibernate converts `createdAt` → `created_at`).
- Declare DB constraints on `@Column` (`nullable`, `unique`, `length`, `updatable`) so the mapping documents the schema, but the **schema itself is owned by migrations** (Flyway/Liquibase) with `spring.jpa.hibernate.ddl-auto: none`.
- `updatable = false` for values that must never change after insert (usernames, codes, `createdAt`).
- Composite uniqueness goes on `@Table`: `@Table(name = "genres", uniqueConstraints = @UniqueConstraint(columnNames = {"name"}))`.

### Types

| Data             | Java type                                     | Notes                                    |
|------------------|-----------------------------------------------|------------------------------------------|
| Primary key      | `Long` + `IDENTITY`                           | Use `SEQUENCE` if you need batch inserts |
| Money            | `BigDecimal`                                  | `@Column(precision = 12, scale = 2)`     |
| Timestamp        | `LocalDateTime` / `Instant`                   | `Instant` when the app spans time zones  |
| Date / time only | `LocalDate` / `LocalTime`                     |                                          |
| Day of week      | `java.time.DayOfWeek` + `@Enumerated(STRING)` |                                          |
| Flags            | `boolean` (not-null) / `Boolean` (nullable)   |                                          |

### Enums

Always `EnumType.STRING` — `ORDINAL` breaks silently when constants are reordered.

```java
public enum LoanStatus {
    ACTIVE, RETURNED, OVERDUE;
}

@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 30)
@Builder.Default
private LoanStatus status = LoanStatus.ACTIVE;
```

### Optimistic locking

Add `@Version private Long version;` to entities that are edited concurrently (users, stock, bookings).
A stale update throws `OptimisticLockingFailureException` → map it to **409 Conflict** in the global exception handler.
Ignore `version` in MapStruct mappers (`@Mapping(target = "version", ignore = true)`).

### Domain behavior

Small, self-contained rules that only use the entity's own state can live on the entity:

```java
public boolean isOverdue(LocalDate today) {
    return status == LoanStatus.ACTIVE && dueDate.isBefore(today);
}
```

Anything that needs repositories or other services belongs in the service layer.

## Relationships

**Golden rules**

1. Every association is `fetch = FetchType.LAZY`. `@ManyToOne` and `@OneToOne` default to EAGER — always override them.
2. Load what a use case needs with `@EntityGraph` or `JOIN FETCH` in the repository (see the `java-jpa-query-strategies` skill), never by switching to EAGER.
3. Keep `spring.jpa.open-in-view: false`; map to DTOs inside the `@Transactional` service method.
4. Prefer **unidirectional `@ManyToOne`**. Add the inverse `@OneToMany` only when you really navigate parent → children.
5. Collections are initialized with `@Builder.Default ... = new ArrayList<>()` (or `HashSet`).
6. Never expose entities from controllers — map to DTO records.

### Many-to-one (most common)

A book has one publisher; a publisher has many books. The FK lives on the "many" side.

```java
// Book.java
@ManyToOne(optional = false, fetch = FetchType.LAZY)
@JoinColumn(name = "publisher_id")
private Publisher publisher;
```

- `optional = false` when the FK is `NOT NULL` (lets Hibernate use inner joins).
- Omit `optional = false` when the relation is nullable:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "translator_id")
private Author translator;
```

`Publisher` does **not** need a `List<Book>`; query books by `publisherId` from `BookRepository` instead.

### One-to-many (bidirectional, parent owns lifecycle)

Use when children only exist inside the parent (order → order lines, author → biography sections).

```java
// Author.java
@OneToMany(mappedBy = "author", cascade = CascadeType.ALL, orphanRemoval = true)
@Builder.Default
private List<Book> books = new ArrayList<>();

public void addBook(Book book) {
    books.add(book);
    book.setAuthor(this);
}

public void removeBook(Book book) {
    books.remove(book);
    book.setAuthor(null);
}

// Book.java
@ManyToOne(optional = false, fetch = FetchType.LAZY)
@JoinColumn(name = "author_id")
private Author author;
```

- `mappedBy` points at the field on the owning side (the one with `@JoinColumn`).
- Keep both sides in sync through helper methods (`addBook` / `removeBook`).
- `cascade = ALL` + `orphanRemoval = true` only when the parent truly owns the children. Never cascade from a child to a parent, and never cascade `REMOVE` on `@ManyToMany`.

### One-to-one

A member has one profile. Put the FK on the dependent side and share the lifecycle.

```java
// MemberProfile.java (owning side)
@OneToOne(optional = false, fetch = FetchType.LAZY)
@JoinColumn(name = "member_id", unique = true)
private Member member;

// Member.java (inverse side — optional, add only if you navigate it)
@OneToOne(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private MemberProfile profile;
```

Alternative that shares the primary key (no separate FK column):

```java
@Id
private Long id;

@OneToOne(fetch = FetchType.LAZY)
@MapsId
@JoinColumn(name = "member_id")
private Member member;
```

The inverse side of a `@OneToOne` cannot be truly lazy in Hibernate without bytecode enhancement; if it matters, make it unidirectional.

### Many-to-many (plain link table)

Books ↔ genres with no extra columns on the link.

```java
// Book.java (owning side: declares the join table)
@ManyToMany(fetch = FetchType.LAZY)
@JoinTable(
    name = "book_genre",
    joinColumns = @JoinColumn(name = "book_id"),
    inverseJoinColumns = @JoinColumn(name = "genre_id")
)
@Builder.Default
private Set<Genre> genres = new HashSet<>();

// Genre.java (inverse side — optional)
@ManyToMany(mappedBy = "genres", fetch = FetchType.LAZY)
@Builder.Default
private Set<Book> books = new HashSet<>();
```

- Only the owning side writes to the join table; always modify `book.getGenres()`.
- Prefer `Set` to avoid Hibernate's "delete all + reinsert" behavior on `List` bags, and to avoid `MultipleBagFetchException` when fetching more than one collection.
- No `cascade = REMOVE` / `ALL` — deleting a book must not delete the genre.

### Many-to-many with extra columns (join entity)

As soon as the link needs data (loan date, quantity, role), replace `@ManyToMany` with an explicit entity and two `@ManyToOne`s.

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "loans")
public class Loan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(nullable = false)
    private LocalDate loanDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    private LocalDate returnedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LoanStatus status = LoanStatus.ACTIVE;

    @Version
    private Long version;
}
```

This is usually better than a composite `@EmbeddedId` key: simpler repositories, simpler DTOs, and the same member can borrow the same book twice.

### Self-reference

```java
// Genre.java — hierarchical categories
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "parent_id")
private Genre parent;
```

## Soft delete

When records must be kept (users, customers), use an `active` flag instead of `DELETE`:

- `@Builder.Default private boolean active = true;`
- `DELETE /api/books/{id}` sets `active = false`; `PATCH /api/books/{id}/activate` restores it.
- Repository methods filter explicitly: `findByIdAndActiveTrue`, `existsByIdAndActiveTrue`.

## Migration that matches the entity (Flyway, PostgreSQL)

```sql
-- V3__create_books.sql
CREATE TABLE books (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200)  NOT NULL,
    isbn          VARCHAR(20)   NOT NULL UNIQUE,
    synopsis      VARCHAR(2000),
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    version       BIGINT,
    created_at    TIMESTAMP     NOT NULL,
    publisher_id  BIGINT        NOT NULL REFERENCES publishers(id),
    author_id     BIGINT        NOT NULL REFERENCES authors(id)
);

CREATE INDEX idx_books_publisher ON books(publisher_id);
CREATE INDEX idx_books_author    ON books(author_id);

CREATE TABLE book_genre (
    book_id  BIGINT NOT NULL REFERENCES books(id)  ON DELETE CASCADE,
    genre_id BIGINT NOT NULL REFERENCES genres(id) ON DELETE CASCADE,
    PRIMARY KEY (book_id, genre_id)
);
```

- Index every FK column used in filters or joins.
- Never edit an applied migration; add a new `V{n}__description.sql`.

## Checklist

- [ ] No `@Data`; `@EqualsAndHashCode(of = "id")`
- [ ] All associations `LAZY`; `optional = false` where the FK is NOT NULL
- [ ] `@Builder.Default` on every initialized field
- [ ] Enums with `EnumType.STRING`
- [ ] `@Version` on concurrently edited entities
- [ ] Bidirectional sides kept in sync with helper methods
- [ ] Cascade only from owner parent to owned children
- [ ] Migration written, FKs indexed
