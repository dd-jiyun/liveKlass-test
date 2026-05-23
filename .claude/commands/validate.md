# liveKlass Architecture Validation

If $ARGUMENTS is provided, validate only those files. Otherwise, validate all changed `.java` files from `git diff --name-only HEAD`.

Run the following checks in order and produce a unified report.

---

## 1. Layer Dependencies & Coupling

Read and apply rules from `.claude/commands/arch-layers.md`.

## 2. Exception Handling

Read and apply rules from `.claude/commands/arch-exceptions.md`.

## 3. Flexibility & Extensibility

Read and apply rules from `.claude/commands/arch-extensibility.md`.

---

## Report Format

For each check:
- Passed: one-line summary
- Violation: `FileName.java:line — what rule was broken — suggested fix`

End with a total: **N violations / M checks passed**
