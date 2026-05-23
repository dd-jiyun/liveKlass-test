# liveKlass Architecture Validation

If $ARGUMENTS is provided, validate only those files. Otherwise, validate all changed `.java` files from `git diff --name-only HEAD`.

Run the following checks in order and produce a unified report.

---

## 1. Layer Dependencies & Coupling

Apply rules from `/project:arch-layers`.

## 2. Exception Handling

Apply rules from `/project:arch-exceptions`.

## 3. Flexibility & Extensibility

Apply rules from `/project:arch-extensibility`.

---

## Report Format

For each check:
- Passed: one-line summary
- Violation: `FileName.java:line — what rule was broken — suggested fix`

End with a total: **N violations / M checks passed**
