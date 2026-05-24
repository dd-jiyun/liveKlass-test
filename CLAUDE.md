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

Detailed rules and validation:
- `/project:arch-layers` — 레이어 의존 방향, Lombok·JPA 규칙
- `/project:arch-domain` — 도메인 불변식, 상태 전이, 조건 캡슐화 규칙
- `/project:arch-code` — 예외 처리, HTTP 상태 코드, 코드 품질 규칙
- `/project:arch-rest-api` — RESTful 설계 원칙
- `/project:validate` — 전체 규칙 검증 실행

---

## Test Conventions

```
src/test/java/com/liveklass
├── domain/         # Unit tests (no Spring context)
│   ├── enrollment/
│   ├── klass/
│   └── waitlist/
└── integration/    # Integration tests (Spring context + H2)
```

See `/project:arch-test` for validation rules.
