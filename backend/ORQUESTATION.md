# Agent Orchestration — Backend (Spring Boot)

Agents defined in `backend/.claude/agents/`. Subagents **do not talk to each other**: the main Claude Code session acts as the orchestrator. It launches each agent, receives its report, and decides the next step by passing the previous agent's output to the next one.

## Agents

### 1. `java-expert-mvc` — Implementer

**Task:** write, refactor and fix backend code in a layered MVC architecture (Controller → Service → Repository) on modern Java and Spring Boot 4.

**Tools:** all (reads, edits, writes and runs Gradle).

**Preloaded skills:** `spring-layered-crud`, `spring-pagination`, `spring-error-handling`, `spring-security`, `spring-multi-tenancy`, `spring-custom-validator`, `spring-websocket`, `java-springboot`, `java-junit`, `java-docs`.

**Functions:**
- Create complete new resources: entity, repository, DTOs (records), MapStruct mapper, service (interface + `impl`) and controller.
- Add paginated/filterable endpoints, error handling with the project's exception hierarchy, and JWT/role security (`@PreAuthorize`, ownership checks).
- Add custom Bean Validation constraints (single-field or cross-field) in `validator/`.
- Add real-time push with WebSocket + STOMP (JWT on CONNECT, per-user/per-topic messages sent after commit).
- Work with the multi-tenant schema (one schema per clinic) and Flyway migrations (`db/migration/public` and `db/migration/tenant`).
- Fix bugs at the root cause (checks every caller before touching a shared method).
- Write unit tests (JUnit 5 + Mockito + AssertJ) for every business rule that throws, and Javadoc on public APIs.
- Verify with `./gradlew compileJava` and the relevant tests before reporting.
- **Output:** short report with changed files (`file:line`), what was verified, and pending risks.

**Does not do (unless asked):** commit/push, change major versions or build tooling, reformat unrelated files.

### 2. `code-reviewer` — Reviewer (read-only)

**Task:** review Java/Spring Boot code and report prioritized findings. **Never modifies files.**

**Tools:** `Read`, `Grep`, `Glob`, `Bash` (read-only commands only: `git diff/log/show/status`, `./gradlew compileJava`, `./gradlew test`).

**Preloaded skills:** `spring-layered-crud`, `spring-pagination`, `spring-error-handling`, `spring-security`.

**Functions:**
- Determine scope: whatever it is given, otherwise pending changes (`git diff`), otherwise the last commit.
- Review in this order: correctness (transactions, lazy loading, race conditions, dates), SOLID, design patterns (missing and over-engineered), MVC layering, clean code, modern Java, persistence/performance (N+1, pagination, indexes), security (IDOR, `@PreAuthorize`, secrets) and tests.
- Verify each finding before reporting it, marking it `CONFIRMED` or `PLAUSIBLE`.
- **Output:** verdict (`Approve` / `Approve with nits` / `Changes requested`) and up to ~15 findings by severity (`Critical`, `Major`, `Minor`, `Nit`), each with `file:line`, problem and fix; ends with strengths and what was not reviewed.

### 3. `java-backend-tester` — Tester

**Task:** prove that new or changed endpoints work according to the contract in `API.md`. Writes and runs tests; **never modifies production code** (`src/main/**`).

**Tools:** `Read`, `Grep`, `Glob`, `Bash`, `Edit`, `Write` (only on `src/test/**`, plus `build.gradle` to add a missing test dependency).

**Preloaded skills:** `java-junit`, `spring-error-handling`, `spring-security`, `spring-pagination`.

**Functions:**
- Determine scope: the endpoints it is given, otherwise endpoints without tests (controllers vs `API.md` vs `src/test`).
- Build a test matrix per endpoint: happy path, validation (400), malformed JSON, 401, 403 per role, 404 / other business, 409, 422, pagination and date ranges.
- Write controller slice tests (`@WebMvcTest` + MockMvc, service mocked) and service unit tests (JUnit 5 + Mockito), and run them with `./gradlew test`.
- Optional curl smoke test against the running app (`localhost:8085`), only when asked or already up; no destructive calls on data it did not create.
- A failing test is either fixed (test bug) or kept failing and reported (production bug); assertions are never weakened to go green.
- **Output:** verdict (`All passing` / `Passing with gaps` / `Failures found`), a table per endpoint (case, expected, actual, result), `file:line` + suggested fix for each FAIL, test files created and the command to rerun them.

## Available skills

| Skill                          | Purpose                                           | Preloaded in                                              |
|--------------------------------|---------------------------------------------------|-----------------------------------------------------------|
| `spring-layered-crud`          | Layered structure of a resource                   | `java-expert-mvc`, `code-reviewer`                        |
| `spring-pagination`            | List endpoints with `Pageable`                    | `java-expert-mvc`, `code-reviewer`, `java-backend-tester` |
| `spring-error-handling`        | Exceptions + `@RestControllerAdvice`              | `java-expert-mvc`, `code-reviewer`, `java-backend-tester` |
| `spring-security`              | JWT and role-based authorization                  | `java-expert-mvc`, `code-reviewer`, `java-backend-tester` |
| `spring-multi-tenancy`         | Schema-per-tenant with Hibernate + Flyway         | `java-expert-mvc`                                         |
| `spring-custom-validator`      | Custom Bean Validation constraints (`validator/`) | `java-expert-mvc`                                         |
| `spring-websocket`             | Real-time push with WebSocket + STOMP + JWT       | `java-expert-mvc`                                         |
| `java-junit`                   | JUnit 5 tests, parameterized tests                | `java-expert-mvc`, `java-backend-tester`                  |
| `java-springboot`, `java-docs` | Best practices, Javadoc                           | `java-expert-mvc`                                         |

## Communication flow

```mermaid
sequenceDiagram
    actor U as User
    participant O as Orchestrator (main session)
    participant J as java-expert-mvc
    participant T as java-backend-tester
    participant R as code-reviewer

    U->>O: Request (feature, bug, refactor)
    O->>J: Task + context (files, business rules)
    J->>J: Reads code and skills, implements, compiles and tests
    J-->>O: Report: changed files, verification, risks
    O->>T: New/changed endpoints to test
    T->>T: Writes and runs tests against API.md
    T-->>O: Verdict + pass/fail table
    alt Failures found
        O->>J: Failing cases + suggested fixes
        J-->>O: Fixes + new verification
        O->>T: Rerun the failing tests
        T-->>O: New verdict
    end
    O->>R: Review the resulting diff
    R-->>O: Verdict + findings by severity
    alt Changes requested (Critical/Major)
        O->>J: Findings to fix
        J-->>O: Fixes + new verification
        O->>R: Re-review only the fixes
        R-->>O: New verdict
    end
    O-->>U: Final summary (changes, tests, pending findings)
```

### Flow rules

1. **Implement → test → review**, always in that order; the tester and the reviewer receive the changed endpoints / diff, not the implementer's report, so they work without bias.
2. The tester runs only when endpoints were added or their HTTP contract changed; internal refactors go straight to review.
3. Only test failures and `Critical`/`Major` findings go back to the implementer. `Minor`/`Nit` are reported to the user, who decides.
4. At most **2 fix cycles** per stage (test, review); if failures or findings persist, escalate to the user.
5. Review only (no changes): the orchestrator calls `code-reviewer` directly. Test only: it calls `java-backend-tester` directly.
6. Changes to the HTTP contract (DTOs, routes, status codes) must be passed to the frontend orchestrator (`frontend/ORQUESTATION.md`) so it updates `src/app/core/models` and `src/app/core/api`.
