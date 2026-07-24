# DB 설계 문서

> **최종 수정**: 2026-07-21
> **원칙**: 기술 레이어가 아닌 도메인 중심으로 테이블을 설계한다.
> 상태(status)는 ENUM으로 DB 레벨에서 제약한다.
> **T-09(2026-07-21) 이후**: 도메인별로 스키마(DB)를 분리했다. 같은 MySQL 인스턴스 안에서 스키마만
> 나눈 것이며 물리 서버 분리는 아니다. 스키마를 건너뛰는 FK는 전부 제거됐고, 그 정합성은
> 애플리케이션(Saga 호출 순서) 레벨로 넘어갔다. 상세 → [testing.md](../testing.md)는 아니고
> `Todo.md` T-09 참고.

---

## 1. 스키마 배치

| 스키마(DB) | 소속 테이블 | 담당 도메인 패키지 |
|---|---|---|
| `screening_db` | `movie`, `theater`, `schedule` | `screening` |
| `seat_db` | `seat`, `schedule_seat` | `seat` |
| `booking_db` | `booking` | `booking` |
| `payment_db` | `payment` | `payment` |

## 2. 테이블 목록 및 역할

| 테이블 | 역할 | 초기 관리 방식 |
|--------|------|--------------|
| `movie` | 영화 정보 | 시드 데이터 고정 (관리 UI 나중에) |
| `theater` | 상영관 정보 | 시드 데이터 고정 |
| `seat` | 물리 좌석 (상태 없음) | 시드 데이터 고정 |
| `schedule` | 상영 스케줄 (영화 × 상영관 × 시간) | 시드 데이터 고정 |
| `schedule_seat` | **상영별 좌석 상태** ← 동시성 제어 핵심 | Saga가 업데이트 |
| `booking` | 예매 기록 (Saga가 생성·업데이트) | Saga가 관리 |
| `payment` | 결제 기록 (Mock 게이트웨이 대상) | Saga가 관리 |

---

## 3. 테이블 상세

### 2-1. `movie` — 영화

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `movie_id` | BIGINT | **PK**, AUTO_INCREMENT | 영화 ID |
| `title` | VARCHAR(200) | NOT NULL | 영화 제목 |
| `duration` | INT | NOT NULL | 상영 시간(분) |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성일시 |

```
PK: movie_id
FK: 없음
```

---

### 2-2. `theater` — 상영관

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `theater_id` | BIGINT | **PK**, AUTO_INCREMENT | 상영관 ID |
| `name` | VARCHAR(100) | NOT NULL | 상영관 이름 (예: A관) |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성일시 |

```
PK: theater_id
FK: 없음
```

---

### 2-3. `seat` — 물리 좌석

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `seat_id` | BIGINT | **PK**, AUTO_INCREMENT | 좌석 ID |
| `theater_id` | BIGINT | NOT NULL | 어느 상영관 소속인지 (`screening_db.theater` 참조, **FK 아님** — 스키마 분리, T-09) |
| `row_num` | VARCHAR(5) | NOT NULL | 행 (A, B, C …) |
| `col_num` | INT | NOT NULL | 열 (1, 2, 3 …) |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성일시 |

```
PK : seat_id
FK : 없음 (theater_id는 screening_db.theater를 값으로만 참조. 스키마가 갈려서 DB가 검증 못 함 — T-09)
UQ : (theater_id, row_num, col_num)  ← 같은 상영관에 동일 좌석 중복 방지
```

> **⚠️ T-09(2026-07-21): `theater_id` FK 제거**
> `seat`는 `seat_db`, `theater`는 `screening_db`로 스키마가 갈라지면서 MySQL이 스키마를 건너뛰는
> FK를 지원하지 않아 제거했다. 지금은 시드 데이터로만 채워지고 런타임에 새로 INSERT하는 경로가
> 없어 당장 위험은 없음 — `T-08`(관리자 CRUD) 착수 시 애플리케이션 레벨 검증 추가 예정.

> **⚠️ `status` 컬럼이 없는 이유**
> 같은 물리 좌석이 상영 A에서는 BOOKED, 상영 B에서는 AVAILABLE일 수 있다.
> 상태를 seat에 직접 두면 상영 스케줄 개념이 생기는 순간 구조가 깨진다.
> → 상태는 `schedule_seat`에 위임한다.

---

### 2-4. `schedule` — 상영 스케줄

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `schedule_id` | BIGINT | **PK**, AUTO_INCREMENT | 스케줄 ID |
| `movie_id` | BIGINT | NOT NULL, **FK** → `movie` | 상영할 영화 |
| `theater_id` | BIGINT | NOT NULL, **FK** → `theater` | 상영할 상영관 |
| `start_time` | DATETIME | NOT NULL | 상영 시작 시각 |
| `end_time` | DATETIME | NOT NULL | 상영 종료 시각 |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성일시 |

```
PK : schedule_id
FK : movie_id   → movie(movie_id)
     theater_id → theater(theater_id)
```

---

### 2-5. `schedule_seat` — 상영별 좌석 상태 ⭐ 핵심 테이블

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `schedule_id` | BIGINT | **PK(1/2)** | 어느 상영 (`screening_db.schedule` 참조, **FK 아님** — 스키마 분리, T-09) |
| `seat_id` | BIGINT | **PK(2/2)**, **FK** → `seat` | 어느 좌석 (같은 스키마라 FK 유지) |
| `status` | ENUM | NOT NULL, DEFAULT 'AVAILABLE' | 좌석 상태 |
| `version` | INT | NOT NULL, DEFAULT 0 | 충돌감지형(낙관적) 락 버전 (UPDATE 시마다 +1) |
| `updated_at` | DATETIME | NOT NULL, ON UPDATE NOW | 상태 변경 시각 (감사용) |

```
PK : (schedule_id, seat_id)  ← 복합 PK
FK : seat_id → seat(seat_id)  (같은 스키마 seat_db 내부)
     schedule_id는 FK 없음 — screening_db.schedule을 값으로만 참조 (T-09, 스키마 분리)
```

> **⚠️ T-09(2026-07-21): `schedule_id` FK 제거**
> `seat`와 같은 이유 — `schedule`이 `screening_db`로 넘어가면서 스키마를 건너뛰는 FK를 제거했다.
> `seat_id` FK는 `seat`와 같은 스키마(`seat_db`)에 남아 있어 그대로 유지된다.

**ENUM 값:**
```
AVAILABLE  → HELD (hold: 임시 점유)
HELD       → BOOKED (confirm: 결제 성공 후 확정)
HELD       → AVAILABLE (release: 결제 실패 → 보상 트랜잭션)
```

> **⚠️ 복합 PK를 선택한 이유**
> `(schedule_id, seat_id)` 조합이 "특정 상영의 특정 좌석"을 유일하게 식별한다.
> MySQL은 PK에 묵시적 클러스터드 인덱스를 생성하기 때문에,
> `WHERE schedule_id = ? AND seat_id = ?` 조건의 검색·락 획득이 가장 빠르다.
> `SELECT ... FOR UPDATE`의 락 범위가 이 행 하나로 최소화된다.

> **⚠️ `updated_at`을 두는 이유**
> 동시성 이슈 발생 시 "언제 HELD로 바뀌었는가"를 로그 없이도 추적 가능하다.
> 나중에 HELD 타임아웃(일정 시간 후 자동 AVAILABLE 복원) 구현 시 기준 컬럼으로 활용 가능.

> **⚠️ `version`을 두는 이유**
> 충돌감지형(낙관적, optimistic) 락의 비교 기준 — 잠그지 않고 진행하다가 충돌을 감지하는 방식.
> `status`를 조건으로 써도 비슷한 효과를 낼 수 있지만, `version`은 상태값의 의미와 무관하게
> "이 행이 그 사이 바뀌었는가"만 순수하게 추적하는 범용 카운터라는 점에서 개념이 더 명확하다.

### `version`이 정확히 뭘 의미하는가

한마디로: **"이 행이 지금까지 몇 번 바뀌었는지 세는 숫자"**다. `status`는 "지금 무슨 상태인가"를 말해주고,
`version`은 "이 상태에 도달하기까지 몇 번 바뀌었는가"를 말해준다 — 서로 다른 정보다.

**언제 올라가는가**: `hold`/`confirm`/`release` 셋 다, 상태가 바뀔 때마다 `+1` 된다 (대기형/비관적 락 경로로
`hold`했을 때도 마찬가지 — 대기형은 `version`을 조건으로 안 쓰지만, "이 행이 몇 번 바뀌었는가"라는 의미 자체는
두 방식 다 동일하게 유지하기 위함).

| 시점 | version |
|------|---------|
| 처음 (AVAILABLE) | 0 |
| hold() 성공 (→HELD) | 1 |
| confirm() 성공 (→BOOKED) | 2 |
| (만약 release()였다면, →AVAILABLE) | 2 |

**충돌감지형 락에서 실제로 어떻게 쓰이는가 — 두 사용자가 같은 좌석을 동시에 클릭하는 상황:**

```
초기: schedule_seat(schedule_id=1, seat_id=5) → status='AVAILABLE', version=0

1단계 — 둘 다 조회 (잠금 없음, 동시에 똑같은 값을 읽음)
  A: SELECT status, version ... → AVAILABLE, version=0
  B: SELECT status, version ... → AVAILABLE, version=0

2단계 — A가 먼저 UPDATE 시도
  UPDATE schedule_seat SET status='HELD', version=version+1
  WHERE schedule_id=1 AND seat_id=5 AND version=0   -- A가 읽은 값
  → DB의 실제 version이 0이 맞으므로 성공. DB: status='HELD', version=1

3단계 — B가 조금 뒤 UPDATE 시도
  UPDATE schedule_seat SET status='HELD', version=version+1
  WHERE schedule_id=1 AND seat_id=5 AND version=0   -- B도 0을 읽었지만...
  → DB엔 이미 A가 바꿔서 version=1 → 조건 불일치 → 영향받은 행 0
  → SeatService가 "영향받은 행 0"을 보고 SeatConflictException을 던짐
```

A와 B는 조회 시점엔 똑같은 버전(0)을 봤지만, DB에 실제로 반영하는 순간엔 항상 딱 한 명만 성공한다.
`version`이 "누가 먼저 실제로 반영했는가"를 가려주는 심판 역할을 하는 것 — 이것이 낙관적 락의 핵심 메커니즘이다.

---

### 2-6. `booking` — 예매

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `booking_id` | BIGINT | **PK**, AUTO_INCREMENT | 예매 ID |
| `schedule_id` | BIGINT | NOT NULL | 상영 스케줄 (`seat_db.schedule_seat` 참조, **FK 아님** — 스키마 분리, T-09) |
| `seat_id` | BIGINT | NOT NULL | 좌석 (`seat_db.schedule_seat` 참조, **FK 아님**) |
| `user_id` | VARCHAR(50) | NOT NULL | 사용자 ID (현재: 파라미터 수신, 인증 없음) |
| `status` | ENUM | NOT NULL, DEFAULT 'PENDING' | 예매 상태 |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성일시 |
| `updated_at` | DATETIME | NOT NULL, ON UPDATE NOW | 수정일시 |

```
PK : booking_id
FK : 없음 (T-09) — (schedule_id, seat_id)는 seat_db.schedule_seat를 값으로만 참조
```

**ENUM 값:**

| 상태 | 의미 | Saga 단계 |
|------|------|-----------|
| `PENDING` | Saga 진행 중 (좌석은 HELD 상태) | hold() 직후 생성 |
| `CONFIRMED` | 결제 성공 → 좌석 BOOKED | confirm() 후 갱신 |
| `CANCELLED` | 결제 실패 or 보상 완료 → 좌석 AVAILABLE | release() 후 갱신 |

> **⚠️ T-09(2026-07-21): `(schedule_id, seat_id)` 복합 FK 제거**
> 원래는 booking이 반드시 실제로 존재하는 `schedule_seat` 행을 참조하도록 DB가 마지막 방어선
> 역할을 했다. `booking_db`/`seat_db`로 스키마가 갈리면서 이 복합 FK도 제거됐다 — 다만 Saga의
> `hold()` 단계가 성공해야만(=schedule_seat가 실제로 HELD로 바뀌어야만) booking이 생성되는
> 흐름이라, 애플리케이션 레벨에서 이미 같은 효과를 내고 있어 FK 제거의 실질적 영향은 적다.

---

### 2-7. `payment` — 결제

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| `payment_id` | BIGINT | **PK**, AUTO_INCREMENT | 결제 ID |
| `booking_id` | BIGINT | NOT NULL | 어느 예매의 결제 (`booking_db.booking` 참조, **FK 아님** — 스키마 분리, T-09) |
| `payment_key` | VARCHAR(100) | NOT NULL, **UNIQUE** | 멱등성 키 |
| `amount` | INT | NOT NULL | 결제 금액(원) |
| `status` | ENUM | NOT NULL, DEFAULT 'PENDING' | 결제 상태 |
| `created_at` | DATETIME | NOT NULL, DEFAULT NOW | 생성일시 |
| `updated_at` | DATETIME | NOT NULL, ON UPDATE NOW | 수정일시 |

```
PK : payment_id
FK : 없음 (T-09) — booking_id는 booking_db.booking을 값으로만 참조
UQ : payment_key  ← 멱등성 보장
```

> **⚠️ T-09(2026-07-21): `booking_id` FK 제거**
> `payment_db`/`booking_db`로 스키마가 갈리면서 제거. Saga 순서상 `BookingService.insertPending()`이
> 커밋된 뒤에만 `payment` INSERT가 일어나므로, booking_id는 항상 실제로 존재하는 값이 보장된다
> (호출 순서 자체가 정합성을 담보 — 별도 검증 로직 불필요).

**ENUM 값:**

| 상태 | 의미 |
|------|------|
| `PENDING` | Mock 게이트웨이 호출 직전 |
| `SUCCESS` | 결제 성공 |
| `FAILED` | 결제 실패 (보상 트랜잭션 발동 트리거) |

> **⚠️ `payment_key` UNIQUE KEY를 두는 이유**
> `MockPaymentGateway`에서 같은 `payment_key`로 두 번 INSERT를 시도하면
> 두 번째는 MySQL이 `DuplicateKeyException`을 던진다.
> 어플리케이션이 아닌 DB가 멱등성의 마지막 방어선이 된다.

---

## 4. 전체 관계도

테이블 간 관계 시각화(ERD, 스키마별 배치도)는 [diagram.md](diagram.md) 참고 — 이 문서는 컬럼·제약·설계
이유 같은 상세 레퍼런스에 집중하고, 그림은 diagram.md 하나로 모아둔다.

---

## 5. 핵심 설계 결정 요약

| 결정 | 이유 |
|------|------|
| `seat`에 `status` 없음 | 같은 물리 좌석이 상영마다 다른 상태를 가질 수 있어야 하므로 |
| `schedule_seat` 복합 PK | 동시성 락(SELECT FOR UPDATE)의 범위를 "상영 × 좌석" 행 하나로 최소화 |
| `payment_key` UNIQUE | DB가 멱등성의 마지막 방어선 역할. 중복 결제 시 DuplicateKeyException |
| `status` 전부 ENUM | 유효하지 않은 상태값을 DB 레벨에서 거부. CHECK 제약 없이도 안전 |
| `updated_at` ON UPDATE | 상태 변경 시각 자동 기록. 동시성 디버깅·HELD 타임아웃 확장 기반 |
| `schedule_seat.version` | 충돌감지형(낙관적) 락의 비교 기준 — "이 행이 그 사이 바뀌었는가"를 순수하게 추적하는 범용 카운터. 상세 → 2-5절 |
| 도메인별 스키마 분리 (T-09, 2026-07-21) | 도메인 간 FK 4개(`seat.theater_id`, `schedule_seat.schedule_id`, `booking→schedule_seat`, `payment.booking_id`) 제거. 정합성은 애플리케이션(Saga 호출 순서) 레벨로 이전. 상세 → `Todo.md` T-09 |
