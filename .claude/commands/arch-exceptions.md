# liveKlass Exception Handling Rules

If $ARGUMENTS is provided, validate only those files. Otherwise, validate all changed `.java` files.

---

## Exception Hierarchy

```
Java standard
  IllegalArgumentException  — input validation failure (domain creation)
  IllegalStateException     — invalid state transition (business rule violation)

Custom (defined in service layer)
  BusinessException (abstract)
    ├── EnrollmentException
    ├── WaitlistException
    └── KlassException
```

---

## Rules by Layer

### Domain Layer

- Use `IllegalArgumentException` for creation-time input validation (`Klass.create()` etc.)
- Use `IllegalStateException` for invalid state transitions (`confirm()`, `cancel()` etc.)
- Must NOT throw custom exception classes — domain must not depend on external classes
- Must NOT throw `RuntimeException` directly — use a specific subtype

### Service Layer

- Must NOT let raw domain exceptions (`IllegalStateException`) propagate to the controller unchecked
- Wrap domain exceptions into custom exceptions with the original cause preserved
- Empty catch blocks are a violation

```java
// Violation
catch (IllegalStateException e) { }

// Allowed
catch (IllegalStateException e) {
    throw new EnrollmentException("enrollment failed", e);
}
```

### Controller Layer

- Must NOT catch business exceptions directly — delegate to `@RestControllerAdvice` + `@ExceptionHandler`
- `@ExceptionHandler` may handle domain exceptions (`IllegalStateException`) directly, but must return user-friendly messages written in Korean
- Exception messages returned to the client must be in Korean (e.g., `"신청 마감일이 지났습니다."`, not `"enrollment deadline exceeded"`)
- Exception messages must never expose internal details (stack traces, SQL, field names)

### All Layers

- `e.printStackTrace()` is a violation — use `log.error()`
- Exception messages must not contain sensitive data (passwords, tokens)

---

## HTTP Status Code Mapping

| Situation | Status |
|-----------|--------|
| Input validation failure | 400 |
| State mismatch (e.g., enrolling in DRAFT class, cancelling after deadline) | 400 |
| Duplicate enrollment, capacity exceeded | 409 |
| Resource not found | 404 |
| Unexpected server error | 500 |
