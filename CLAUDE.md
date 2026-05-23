# LiveKlass — Claude Code Guide

## Project Overview

Online lecture enrollment system built on Spring Boot with a domain-centric design (DDD-lite).

- Java 21 / Spring Boot 4.0 / JPA + H2 (test) / MySQL (production)

---

## Package Structure

```
com.liveklass
├── domain/         # Domain entities, VOs, Enums (pure Java)
│   ├── enrollment/
│   ├── klass/
│   ├── user/
│   └── waitlist/
├── repository/     # JpaRepository interfaces
├── service/        # Use cases, transaction boundaries
├── controller/     # REST API entry points
├── dto/            # Request/response objects
└── global/         # Common config, exception handlers
```

---

## Architecture Rules

Dependencies must flow in one direction only.

```
controller → service, dto
service    → domain, repository, dto
repository → domain
domain     → (nothing)
dto        → (nothing)
```

Detailed rules are in `.claude/commands/`:
- `/project:arch-layers` — layer dependencies, Lombok/JPA rules
- `/project:arch-exceptions` — exception hierarchy, HTTP status codes
- `/project:arch-extensibility` — interface abstraction, scheduler separation
- `/project:validate` — run full architecture validation

---

## Coding Conventions

### Domain Objects

- Always instantiate through the `create()` static factory method.
- Direct constructor calls (`new Klass(...)`) are only allowed inside the domain class itself.
- Forbidden annotations: `@Setter`, `@Data`, `@Builder`, `@AllArgsConstructor`.
- `@ManyToOne` must always specify `fetch = FetchType.LAZY`.
- `@OneToMany` is forbidden — use queries instead.

### Service Layer

- `@Transactional` is only allowed in the service layer.
- Never access domain fields directly — always go through domain methods.
- Never call `LocalDateTime.now()` inside a service — inject `now` as a parameter.

### Exception Handling

- Domain: `IllegalArgumentException` (creation-time validation), `IllegalStateException` (invalid state transitions).
- Service: wrap domain exceptions into custom exceptions (e.g., `EnrollmentException`).
- Client-facing error messages must be written in Korean.
- Never use `e.printStackTrace()` — use `log.error()`.

### General

- Wildcard imports are forbidden (e.g., `import java.util.*`).
- Magic numbers must be extracted to `EnrollmentPolicy` constants.
- Omit unnecessary comments — code should communicate intent on its own.

---

## Test Conventions

### Test Structure

```
src/test/java/com/liveklass
├── domain/         # Unit tests (no Spring context)
│   ├── enrollment/
│   ├── klass/
│   └── waitlist/
└── integration/    # Integration tests (Spring context + H2)
```

### Writing Rules

- Method names must start with `should`.  
  e.g. `shouldCreatePendingEnrollmentWhenEnrolled()`
- `@DisplayName` must describe the expected outcome as a natural language sentence.  
  e.g. `"수강 신청하면 PENDING 상태의 Enrollment가 생성된다"`
- Unit tests must be pure Java with no Spring context.
- Do not use divider comments (e.g., `// ───`).
