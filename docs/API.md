# liveKlass — API 명세

## 공통 규칙

### 인증

모든 요청에 사용자 ID를 헤더로 전달합니다. 별도 인증 미들웨어는 구현하지 않았습니다.

```
X-User-Id: {userId}
```

### 응답 형식

성공 시 모든 응답은 `data` 필드로 래핑됩니다.

```json
{ "data": { ... } }
```

목록 응답:

```json
{ "data": [ ... ] }
```

반환할 본문이 없는 경우 (상태 전이 엔드포인트 등):

```json
{ "data": null }
```

### 에러 응답 형식

```json
{
  "name": "에러_코드",
  "message": "에러 설명"
}
```

---

## 강의 (Klass)

### POST /api/klasses — 강의 등록

강의를 DRAFT 상태로 생성합니다. CREATOR 권한 사용자만 호출 가능합니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 크리에이터 ID |

**요청 본문**

| 필드 | 타입 | 필수 | 설명 |
|------|------|:---:|------|
| title | String | O | 강의 제목 (공백 불가) |
| description | String | | 강의 설명 |
| price | BigDecimal | O | 수강료 (0이면 무료) |
| maxCapacity | Integer | O | 최대 정원 (1 이상) |
| startDate | LocalDate | O | 강의 시작일 (`YYYY-MM-DD`) |
| endDate | LocalDate | O | 강의 종료일 (시작일 이후) |
| enrollmentDeadline | LocalDate | O | 신청 마감일 (시작일 이전) |
| cancellationDeadlineDays | Integer | | 취소 가능 기간 (일, 0이면 취소 불가, 기본값 7) |

```json
{
  "title": "스프링 부트 심화",
  "description": "JPA와 트랜잭션을 깊이 다룹니다.",
  "price": 49000,
  "maxCapacity": 30,
  "startDate": "2026-07-01",
  "endDate": "2026-08-31",
  "enrollmentDeadline": "2026-06-25",
  "cancellationDeadlineDays": 7
}
```

**응답 `201 Created`**

```json
{
  "data": {
    "id": 1,
    "title": "스프링 부트 심화",
    "description": "JPA와 트랜잭션을 깊이 다룹니다.",
    "status": "DRAFT",
    "price": 49000.00,
    "maxCapacity": 30,
    "enrolledCount": 0,
    "isFull": false,
    "startDate": "2026-07-01",
    "endDate": "2026-08-31",
    "enrollmentDeadline": "2026-06-25",
    "cancellationDeadlineDays": 7,
    "creatorId": 1,
    "createdAt": "2026-05-24T10:00:00"
  }
}
```

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| KLASS_USER_NOT_FOUND | 404 | 사용자를 찾을 수 없음 |

---

### GET /api/klasses — 강의 목록 조회

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 사용자 ID |

**쿼리 파라미터**

| 파라미터 | 설명 |
|----------|------|
| status | 필터링할 강의 상태 (`DRAFT`, `OPEN`, `CLOSED`). 미전달 시 동작은 역할에 따라 다름 |

**역할별 동작**

- CREATOR + `status` 없음: 자신이 등록한 강의 전체 (DRAFT 포함)
- CREATOR + `status=OPEN`: 플랫폼 내 모든 OPEN 강의
- STUDENT + `status=OPEN`: 플랫폼 내 모든 OPEN 강의

**응답 `200 OK`**

```json
{
  "data": [
    {
      "id": 1,
      "title": "스프링 부트 심화",
      "description": "JPA와 트랜잭션을 깊이 다룹니다.",
      "status": "OPEN",
      "price": 49000.00,
      "maxCapacity": 30,
      "enrolledCount": 5,
      "isFull": false,
      "startDate": "2026-07-01",
      "endDate": "2026-08-31",
      "enrollmentDeadline": "2026-06-25",
      "cancellationDeadlineDays": 7,
      "creatorId": 1,
      "createdAt": "2026-05-24T10:00:00"
    }
  ]
}
```

---

### GET /api/klasses/{id} — 강의 상세 조회

헤더 없이 공개 조회 가능합니다.

**응답 `200 OK`**

```json
{
  "data": {
    "id": 1,
    "title": "스프링 부트 심화",
    "description": "JPA와 트랜잭션을 깊이 다룹니다.",
    "status": "OPEN",
    "price": 49000.00,
    "maxCapacity": 30,
    "enrolledCount": 5,
    "isFull": false,
    "startDate": "2026-07-01",
    "endDate": "2026-08-31",
    "enrollmentDeadline": "2026-06-25",
    "cancellationDeadlineDays": 7,
    "creatorId": 1,
    "createdAt": "2026-05-24T10:00:00"
  }
}
```

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| KLASS_NOT_FOUND | 404 | 강의를 찾을 수 없음 |

---

### PATCH /api/klasses/{id} — 강의 수정

DRAFT 상태인 강의의 필드를 수정합니다. 전달된 필드만 변경되며, 나머지는 유지됩니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 크리에이터 ID |

**요청 본문** (모든 필드 선택)

| 필드 | 타입 | 설명 |
|------|------|------|
| title | String | 강의 제목 |
| description | String | 강의 설명 |
| price | BigDecimal | 수강료 |
| maxCapacity | Integer | 최대 정원 |
| startDate | LocalDate | 강의 시작일 |
| endDate | LocalDate | 강의 종료일 |
| enrollmentDeadline | LocalDate | 신청 마감일 |
| cancellationDeadlineDays | Integer | 취소 가능 기간 (일) |

```json
{
  "title": "스프링 부트 심화 v2",
  "maxCapacity": 40
}
```

**응답 `200 OK`** — 수정된 강의 객체 (KlassResponse)

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| KLASS_NOT_FOUND | 404 | 강의를 찾을 수 없음 |
| KLASS_FORBIDDEN | 403 | 본인 강의가 아님 |
| KLASS_STATE_ERROR | 400 | DRAFT 상태가 아님 |

---

### POST /api/klasses/{id}/open — 강의 오픈

DRAFT → OPEN 전이. 신청 마감일이 현재 날짜 이후여야 합니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 크리에이터 ID |

**응답 `200 OK`** — 변경된 강의 객체 (KlassResponse)

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| KLASS_NOT_FOUND | 404 | 강의를 찾을 수 없음 |
| KLASS_FORBIDDEN | 403 | 본인 강의가 아님 |
| KLASS_STATE_ERROR | 400 | DRAFT 상태가 아니거나 마감일이 이미 지남 |

---

### POST /api/klasses/{id}/close — 강의 마감

OPEN → CLOSED 전이. WAITING/NOTIFIED 상태의 대기자를 전원 자동 취소합니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 크리에이터 ID |

**응답 `200 OK`** — 변경된 강의 객체 (KlassResponse)

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| KLASS_NOT_FOUND | 404 | 강의를 찾을 수 없음 |
| KLASS_FORBIDDEN | 403 | 본인 강의가 아님 |
| KLASS_STATE_ERROR | 400 | OPEN 상태가 아님 |

---

### POST /api/klasses/{id}/reopen — 강의 초안 복원

CLOSED → DRAFT 전이. 마감일을 수정한 뒤 다시 오픈하기 위한 단계입니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 크리에이터 ID |

**응답 `200 OK`** — 변경된 강의 객체 (KlassResponse)

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| KLASS_NOT_FOUND | 404 | 강의를 찾을 수 없음 |
| KLASS_FORBIDDEN | 403 | 본인 강의가 아님 |
| KLASS_STATE_ERROR | 400 | CLOSED 상태가 아님 |

---

### DELETE /api/klasses/{id} — 강의 삭제

DRAFT 상태인 강의만 삭제 가능합니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 크리에이터 ID |

**응답 `200 OK`**

```json
{ "data": null }
```

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| KLASS_NOT_FOUND | 404 | 강의를 찾을 수 없음 |
| KLASS_FORBIDDEN | 403 | 본인 강의가 아님 |
| KLASS_STATE_ERROR | 400 | DRAFT 상태가 아님 |

---

### GET /api/klasses/{id}/students — 수강생 목록 조회

CONFIRMED 상태 수강생 목록을 반환합니다. CREATOR 전용입니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 크리에이터 ID |

**응답 `200 OK`**

```json
{
  "data": [
    {
      "enrollmentId": 10,
      "userId": 2,
      "userName": "홍길동",
      "confirmedAt": "2026-05-24T10:05:00"
    }
  ]
}
```

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| KLASS_NOT_FOUND | 404 | 강의를 찾을 수 없음 |
| KLASS_FORBIDDEN | 403 | 본인 강의가 아님 |

---

## 수강 신청 (Enrollment)

### POST /api/enrollments — 수강 신청

OPEN 강의에 수강 신청합니다. 신청 성공 시 PENDING 상태로 생성되며, 무료 강의는 즉시 CONFIRMED로 자동 전환됩니다. NOTIFIED 대기자는 이 엔드포인트를 사용할 수 없으며 `POST /api/waitlists/{id}/convert`를 호출해야 합니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 수강 신청할 사용자 ID |

**요청 본문**

| 필드 | 타입 | 필수 | 설명 |
|------|------|:---:|------|
| klassId | Long | O | 신청할 강의 ID |

```json
{ "klassId": 1 }
```

**응답 `201 Created`**

```json
{
  "data": {
    "id": 10,
    "klassId": 1,
    "klassTitle": "스프링 부트 심화",
    "status": "PENDING",
    "pendingExpiresAt": "2026-05-24T10:20:00",
    "confirmedAt": null,
    "enrolledAt": "2026-05-24T10:00:00"
  }
}
```

무료 강의인 경우 내부적으로 PENDING 생성 후 즉시 자동 확정됩니다. `status`는 `"CONFIRMED"`, `confirmedAt`과 `pendingExpiresAt` 모두 채워진 채로 반환됩니다.

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| ENROLLMENT_USER_NOT_FOUND | 404 | 사용자를 찾을 수 없음 |
| KLASS_NOT_FOUND | 404 | 강의를 찾을 수 없음 |
| KLASS_STATE_ERROR | 400 | OPEN 상태가 아닌 강의 |
| ENROLLMENT_DUPLICATE | 409 | 이미 PENDING 또는 CONFIRMED 신청이 존재함 |
| ENROLLMENT_CAPACITY_EXCEEDED | 409 | 정원 초과 (대기 등록 안내) |
| ENROLLMENT_WAITLIST_PRIORITY | 409 | NOTIFIED 대기자가 존재해 신청 차단됨 |

---

### GET /api/enrollments/me — 내 수강 신청 목록

사용자의 수강 신청 내역 전체를 반환합니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 사용자 ID |

**응답 `200 OK`**

```json
{
  "data": [
    {
      "id": 10,
      "klassId": 1,
      "klassTitle": "스프링 부트 심화",
      "status": "CONFIRMED",
      "pendingExpiresAt": null,
      "confirmedAt": "2026-05-24T10:05:00",
      "enrolledAt": "2026-05-24T10:00:00"
    }
  ]
}
```

---

### POST /api/enrollments/{id}/confirm — 결제 확정

PENDING 상태 수강 신청을 CONFIRMED로 전환합니다. 20분 이내에만 가능합니다. 본인 신청에만 가능합니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 사용자 ID |

**응답 `200 OK`**

```json
{ "data": null }
```

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| ENROLLMENT_NOT_FOUND | 404 | 수강 신청 내역을 찾을 수 없음 |
| ENROLLMENT_FORBIDDEN | 403 | 본인 신청이 아님 |
| ENROLLMENT_STATE_ERROR | 400 | PENDING 상태가 아니거나 20분이 만료됨 |

---

### POST /api/enrollments/{id}/cancel — 수강 취소

PENDING 또는 취소 가능 기간 내 CONFIRMED 수강 신청을 취소합니다. 취소 성공 시 수강 인원이 감소하고 대기 1순위에게 알림이 전송됩니다. 본인 신청에만 가능합니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 사용자 ID |

**응답 `200 OK`**

```json
{ "data": null }
```

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| ENROLLMENT_NOT_FOUND | 404 | 수강 신청 내역을 찾을 수 없음 |
| ENROLLMENT_FORBIDDEN | 403 | 본인 신청이 아님 |
| ENROLLMENT_STATE_ERROR | 400 | 취소 불가 상태 (이미 취소됨, 취소 가능 기간 초과 등) |

---

## 대기열 (Waitlist)

### POST /api/waitlists — 대기 등록

정원이 꽉 찬 OPEN 강의에 대기 등록합니다. 자리 발생 시 FIFO 순서로 알림을 받습니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 사용자 ID |

**요청 본문**

| 필드 | 타입 | 필수 | 설명 |
|------|------|:---:|------|
| klassId | Long | O | 대기 등록할 강의 ID |

```json
{ "klassId": 1 }
```

**응답 `201 Created`**

```json
{
  "data": {
    "id": 5,
    "klassId": 1,
    "klassTitle": "스프링 부트 심화",
    "status": "WAITING",
    "position": 3,
    "notifiedAt": null,
    "joinedAt": "2026-05-24T11:00:00"
  }
}
```

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| WAITLIST_USER_NOT_FOUND | 404 | 사용자를 찾을 수 없음 |
| KLASS_NOT_FOUND | 404 | 강의를 찾을 수 없음 |
| KLASS_STATE_ERROR | 400 | OPEN 상태가 아닌 강의 |
| WAITLIST_NOT_FULL | 400 | 정원이 남아 있는 강의에는 대기 불가 |
| WAITLIST_DUPLICATE | 409 | 이미 WAITING 또는 NOTIFIED 상태인 대기 항목 존재 |
| WAITLIST_ALREADY_ENROLLED | 409 | 이미 PENDING 또는 CONFIRMED 수강 신청이 존재함 |

---

### POST /api/waitlists/{id}/convert — 대기 → 수강 전환

NOTIFIED 상태에서 20분 이내에 수강 신청으로 전환합니다. 내부적으로 대기 상태를 CONVERTED로 변경하고 PENDING 수강 신청을 생성합니다. 본인 대기 항목에만 가능합니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 사용자 ID |

**응답 `200 OK`**

```json
{ "data": null }
```

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| WAITLIST_NOT_FOUND | 404 | 대기 항목을 찾을 수 없음 |
| WAITLIST_FORBIDDEN | 403 | 본인 대기 항목이 아님 |
| WAITLIST_STATE_ERROR | 400 | NOTIFIED 상태가 아니거나 20분이 만료됨 |
| WAITLIST_CAPACITY_EXCEEDED | 409 | 전환 시점에 정원 재초과 |

---

### POST /api/waitlists/{id}/cancel — 대기 취소

WAITING 또는 NOTIFIED 상태인 대기를 취소합니다. NOTIFIED 상태 취소 시 다음 순번 대기자에게 알림이 전달됩니다. 본인 대기 항목에만 가능합니다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
|------|:---:|------|
| X-User-Id | O | 사용자 ID |

**응답 `200 OK`**

```json
{ "data": null }
```

**에러**

| 코드 | HTTP | 설명 |
|------|:----:|------|
| WAITLIST_NOT_FOUND | 404 | 대기 항목을 찾을 수 없음 |
| WAITLIST_FORBIDDEN | 403 | 본인 대기 항목이 아님 |
| WAITLIST_STATE_ERROR | 400 | WAITING 또는 NOTIFIED 상태가 아님 |

---

## 에러 코드 목록

### 강의 (Klass)

| 코드 | HTTP | 설명 |
|------|:----:|------|
| KLASS_NOT_FOUND | 404 | 강의를 찾을 수 없습니다 |
| KLASS_USER_NOT_FOUND | 404 | 사용자를 찾을 수 없습니다 |
| KLASS_FORBIDDEN | 403 | 해당 강의에 대한 권한이 없습니다 |
| KLASS_STATE_ERROR | 400 | 현재 상태에서는 처리할 수 없습니다 |

### 수강 신청 (Enrollment)

| 코드 | HTTP | 설명 |
|------|:----:|------|
| ENROLLMENT_NOT_FOUND | 404 | 수강 신청 내역을 찾을 수 없습니다 |
| ENROLLMENT_USER_NOT_FOUND | 404 | 사용자를 찾을 수 없습니다 |
| ENROLLMENT_DUPLICATE | 409 | 이미 신청한 강의입니다 |
| ENROLLMENT_CAPACITY_EXCEEDED | 409 | 정원이 꽉 찼습니다. 대기 등록을 해보시겠습니까? |
| ENROLLMENT_WAITLIST_PRIORITY | 409 | 알림을 받은 대기자가 있어 수강 신청할 수 없습니다 |
| ENROLLMENT_FORBIDDEN | 403 | 본인의 수강 신청만 처리할 수 있습니다 |
| ENROLLMENT_STATE_ERROR | 400 | 현재 상태에서는 처리할 수 없습니다 |

### 대기열 (Waitlist)

| 코드 | HTTP | 설명 |
|------|:----:|------|
| WAITLIST_NOT_FOUND | 404 | 대기 항목을 찾을 수 없습니다 |
| WAITLIST_USER_NOT_FOUND | 404 | 사용자를 찾을 수 없습니다 |
| WAITLIST_EMPTY | 404 | 대기 중인 인원이 없습니다 |
| WAITLIST_DUPLICATE | 409 | 이미 대기 중인 강의입니다 |
| WAITLIST_NOT_FULL | 400 | 정원이 남아 있는 강의에는 대기 등록을 할 수 없습니다 |
| WAITLIST_FORBIDDEN | 403 | 본인의 대기 항목만 처리할 수 있습니다 |
| WAITLIST_CAPACITY_EXCEEDED | 409 | 현재 정원이 초과되어 수강 전환이 불가합니다 |
| WAITLIST_STATE_ERROR | 400 | 현재 상태에서는 처리할 수 없습니다 |
| WAITLIST_ALREADY_ENROLLED | 409 | 이미 수강 신청 내역이 있습니다 |
