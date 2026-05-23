# liveKlass Flexibility & Extensibility Rules

## Interface Abstraction

### Notification

- Notification logic must NOT be written directly in service classes
- Must call through an interface (`NotificationService` or `NotificationPort`)
- Direct use of email clients, push clients, or any concrete notifier in service is a violation

```java
// Violation
waitlistRepository.save(waitlist);
emailClient.send(user.getEmail(), "A seat is available");

// Allowed
waitlistRepository.save(waitlist);
notificationService.notifyWaiter(waitlist.getUserId());
```

### Repository

- Only `JpaRepository`-extending interfaces are allowed
- Injecting `EntityManager` directly into a service is a violation
  - Exception: `@Lock` query methods inside a repository interface are allowed

---

## Scheduler Coupling

- `@Scheduled` methods must live in a dedicated class (`SchedulerService`) — not mixed into domain services
- `@Scheduled` methods must NOT contain business logic directly — delegate to service methods

```java
// Violation
@Scheduled(fixedDelay = 60000)
public void expirePending() {
    enrollmentRepository.findExpired().forEach(e -> {
        e.expireIfOverdue(LocalDateTime.now());
        enrollmentRepository.save(e);
    });
}

// Allowed
@Scheduled(fixedDelay = 60000)
public void expirePending() {
    enrollmentService.expireOverduePending();
}
```

---

## Magic Numbers & Hardcoding

The following literal values in code are violations:

| Value | Meaning | Required replacement |
|-------|---------|----------------------|
| `20` (minutes) | PENDING expiry / NOTIFIED accept window | `EnrollmentPolicy.PENDING_EXPIRE_MINUTES` |
| `1` (minute) | Scheduler interval | extracted to config |

Use a constants class or `@ConfigurationProperties`.

---

## Testability

- `LocalDateTime.now()` called inside a service method is a violation
  - Reason: time-dependent logic becomes untestable without waiting real time
  - Fix: accept `now` as a parameter, or inject a `Clock` bean

```java
// Violation
public void expireOverduePending() {
    LocalDateTime now = LocalDateTime.now();
    ...
}

// Allowed
public void expireOverduePending(LocalDateTime now) {
    ...
}
```

- `static` utility methods must not depend on external state (DB, time, random)
