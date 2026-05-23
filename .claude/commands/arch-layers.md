# liveKlass Layer Dependency & Coupling Rules

If $ARGUMENTS is provided, validate only those files. Otherwise, validate all changed `.java` files.

---

## Package Structure

```
com.liveklass.domain.*       — domain layer
com.liveklass.repository.*   — repository layer
com.liveklass.service.*      — service layer
com.liveklass.controller.*   — controller layer
com.liveklass.dto.*          — data transfer objects
```

---

## Allowed Dependency Direction

Only these import directions are permitted. Everything else is a violation.

```
controller → service, dto
service    → domain, repository, dto
repository → domain
domain     → (nothing)
dto        → (nothing)
```

### Domain Layer (`domain.*`)

- Must NOT import from `service`, `repository`, `controller`, or `dto`
- Only `java.*` and `lombok.*` allowed as external imports
  - Exception: `jakarta.persistence.*` is allowed for JPA annotations only
- Cross-domain references are allowed (e.g., `Enrollment → Klass`)

### Service Layer (`service.*`)

- Must NOT import from `controller.*`
- `@Transactional` allowed only in service layer — violation if found in domain, repository, or controller
- Must NOT access domain fields directly — always go through domain methods
  - Violation: `enrollment.status = CONFIRMED`
  - Allowed: `enrollment.confirm(now)`
- `enrolledCount` must only be modified via `increaseEnrolledCount()` / `decreaseEnrolledCount()`

### Repository Layer (`repository.*`)

- Must NOT import from `service.*` or `controller.*`
- Must be interface only, extending Spring Data `JpaRepository`
- Concrete repository implementation classes are a violation

### Controller Layer (`controller.*`)

- Must NOT import from `repository.*` or `domain.*` — only service and dto
- Must NOT contain business logic (conditionals, state checks)
- Must NOT use `@Transactional`

---

## Import Rules

- Wildcard imports (`import jakarta.persistence.*`, `import java.util.*`, etc.) are forbidden
- Every import must be a single explicit class import

## Lombok Rules

- Allowed: `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
- Forbidden: `@Setter`, `@Data`, `@Builder`, `@AllArgsConstructor`, `@RequiredArgsConstructor`

## Domain Object Construction

- Direct constructor calls (`new Klass(...)`) outside the domain class itself are a violation
- All instantiation must go through the static factory method (`create()`)

## JPA Rules

- `@ManyToOne` without `fetch = FetchType.LAZY` is a violation
- `@OneToMany` is forbidden — use queries instead of bidirectional navigation
