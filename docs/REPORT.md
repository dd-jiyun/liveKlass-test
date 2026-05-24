# liveKlass — 과제 작업 일지 및 회고

## 작업 내역

### Day 1 (2026-05-22) — 도메인 설계 및 단위 테스트

도메인 모델링과 비즈니스 규칙을 먼저 문서화했습니다. 코드보다 "무엇을 구현할 것인가"를 명확히 하는 것이 먼저라고 판단했습니다.

- 비즈니스 규칙 정리 (`BUSINESS_RULES.md`, `DECISIONS.md`)
- ERD 설계 (Klass, Enrollment, Waitlist, EnrollmentHistory)
- 도메인 단위 테스트 작성 후 Green 확인 (KlassTest 13개, EnrollmentTest 11개, WaitlistTest 8개, EnrollmentHistoryTest 5개)

#### 주요 결정
- `startDate/endDate/enrollmentDeadline`을 `KlassPeriod` 임베디드 VO로 분리
- `enrolled_count` 컬럼 방식 채택 (COUNT 집계 대신 카운터로 정원 즉시 검증)

### Day 2 (2026-05-23) — 서비스 레이어 설계 논의

코드를 본격적으로 작성하기 전, 핵심 로직의 흐름과 제약을 먼저 정리했습니다.

#### 주요 결정
- 취소 가능 기간 기준: 결제일 → **강의 시작일 기준**으로 변경 (늦게 결제할수록 강의 이후에도 취소가 가능해지는 모순 해소)
- 중복 신청 허용 기준: CANCELLED 이후만 재신청 허용
- 서비스 테스트 방식: 비관적 락은 실제 DB 트랜잭션 없이는 검증 불가 → 통합 테스트 채택
- 에러 코드 구조: 도메인별 enum + 전역 핸들러(`ErrorCode` 인터페이스)

### Day 3 (2026-05-24) — 서비스/컨트롤러 구현 및 Clock 도입

전체 레이어를 완성한 날입니다.

- 서비스 레이어 전체 구현 (KlassService, EnrollmentService, WaitlistService)
- 서비스 단일 책임 분리 — 각 메서드를 검증/저장/조회 private 헬퍼로 분리
- Controller + DTO 레이어 구현 (16개 엔드포인트, record 기반 DTO)
- **Clock 빈 주입 도입**: `LocalDateTime.now()` → `LocalDateTime.now(clock)`으로 전환해 서비스 테스트에서 시각 제어 가능하게 변경
- `@MockitoBean` + `fixClock()` 헬퍼 방식과 `Clock.fixed()` + `@TestConfiguration` 방식을 역할에 따라 분리 적용

#### 문서 간 불일치 4건 발견 및 해결
- CLOSED → OPEN 직접 전이 → CLOSED → DRAFT → OPEN으로 통일
- 대기 수락 시 즉시 CONFIRMED → 유료 PENDING 경유 / 무료 즉시 CONFIRMED로 수정
- 수동 마감 시 대기자 처리 로직 구현
- NOTIFIED 대기자 신청 우선권 구현 (`ENROLLMENT_WAITLIST_PRIORITY`)

---

코드 리뷰를 통해 API 설계를 개선하고 SchedulerService를 구현했습니다.

코드 리뷰 후 개선한 사항:
- api 복수 명사로 통일
- `PATCH /{id} + status` 방식 → 액션 기반 엔드포인트로 분리 (`/confirm`, `/cancel`, `/open`, `/close`, `/reopen`)
- `KlassPatchRequest`에서 상태 전이 필드 분리 (필드 수정과 상태 전이 책임 분리)

SchedulerService 구현:
- `expirePendingEnrollments()` — PENDING 20분 초과 자동 취소 + 대기자 알림 (1분 주기)
- `expireNotifiedWaitlists()` — NOTIFIED 수락 기간 초과 → EXPIRED + 다음 대기자 알림
- `closeExpiredKlasses()` — 신청 마감일 경과 OPEN 강의 자동 CLOSED

---

동시성 테스트 구현 (`ConcurrencyTest.java`):
- CON-01: 정원 1명 강의에 10스레드 동시 신청 → 1건만 PENDING 성공
- CON-02: 정원 3명 강의에 20스레드 동시 신청 → enrolledCount = 3
- CON-03: NOTIFIED 대기자 존재 시 일반 신청 차단

코드 리뷰를 통해 보완한 사항:
- 수강 신청 소유권 검증 추가 (403 ENROLLMENT_FORBIDDEN)
- NOTIFIED 대기자 취소 시 다음 순번 알림 연결
- 유료 강의 대기 전환 시 PENDING 경유 흐름 확인 및 테스트 보강
- `afterCommit` 콜백에서 연관관계 미리 초기화 (`JOIN FETCH` 적용)
- 대기 순번 채번 방식 `MAX(position) + 1`으로 단조 증가 보장
- 대기 전환 시 중복 수강 신청 방지 (`WAITLIST_ALREADY_ENROLLED`)

## 최종 현황

| 항목 | 내용 |
|------|------|
| 전체 테스트 수 | 97개+ |
| 테스트 통과 | BUILD SUCCESSFUL |
| 구현 엔드포인트 | 16개 (강의 9, 수강 신청 4, 대기열 3) |
| 스케줄러 | 3종 (PENDING 만료 / NOTIFIED 만료 / 강의 자동 마감) |

## 회고

### 잘 된 점

**도메인을 먼저 문서화한 것이 이후 개발을 수월하게 했습니다.**  
비즈니스 규칙과 ERD를 먼저 정리하고 단위 테스트로 검증한 뒤 서비스를 구현했기 때문에, 서비스 레이어 구현 중 도메인 설계를 다시 뒤집는 상황이 거의 없었습니다.

**코드 리뷰 → 논의 → 수정의 루프가 실제로 작동했습니다.**  
소유권 검증 누락(보안), 상태 검증 순서 버그, `LazyInitializationException` 등 단순한 기능 테스트에서는 잡히지 않는 문제들이 코드 리뷰 단계에서 발견되었습니다.

**Clock 빈 주입으로 시간 의존 로직을 테스트할 수 있게 된 것**이 가장 체감이 컸습니다. PENDING 만료, 취소 가능 기간 초과, NOTIFIED 수락 시간 초과를 모두 통합 테스트 수준에서 검증할 수 있었습니다.

### 아쉬운 점

**취소 가능 기간 기준을 처음부터 강의 시작일 기준으로 잡지 못했습니다.**  
"결제 후 N일 이내"로 설계했다가 늦게 결제할수록 강의 이후에도 취소가 가능해지는 모순을 발견하고 수정했습니다. 시나리오를 더 구체적으로 검토했다면 초기 설계에서 잡을 수 있었습니다.

**대기 수락 시 CONFIRMED 직접 생성 결정도 늦게 뒤집었습니다.**  
초기에 "대기 수락 = 결제 확정과 동일"로 판단했으나, 유료 강의에서 결제 확인 없이 수강이 확정되는 것이 비즈니스 규칙에 맞지 않아 수정했습니다. 유료/무료 케이스를 같이 고려했다면 처음부터 PENDING 경유로 설계할 수 있었습니다.

**신청 내역 페이지네이션을 구현하지 못했습니다.**  
PENDING 항목이 스케줄러에 의해 자동 삭제되는 구조라 offset 방식은 페이지 이동 중 항목 누락이 발생할 수 있어 cursor 기반이 기술적으로 더 적합하다고 판단했습니다. 다만 내 수강 목록은 대부분 데이터가 많지 않을 것 같아 사용자에게 실제로 필요한 기능인지 확신이 서지 않았고, 그 이유로 구현 범위에서 제외했습니다.
