# liveKlass Architecture Validation

If $ARGUMENTS is provided, validate only those files. Otherwise, validate all changed `.java` files from `git diff --name-only HEAD`.

Run the following checks in order and produce a unified report.

---

## 1. Layer Dependencies

Read and apply rules from `.claude/commands/arch-layers.md`.

## 2. Domain Invariants

Read and apply rules from `.claude/commands/arch-domain.md`.

## 3. Code Quality

Read and apply rules from `.claude/commands/arch-code.md`.

## 4. REST API Design

Read and apply rules from `.claude/commands/arch-rest-api.md`.

## 5. Test Conventions

Read and apply rules from `.claude/commands/arch-test.md`.

---

## Report Format

For each check:
- Passed: one-line summary
- Violation: `FileName.java:line — rule violated — suggested fix`

End with a total: **N violations / M checks passed**
