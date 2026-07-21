# Backend 설계 문서

> **최종 수정**: 2026-07-21
> **원칙**: 기술 레이어(controller/service/mapper)가 아닌 **도메인**으로 먼저 나눈다.
> **T-09(2026-07-21)**: DB를 도메인별 스키마 4개로 분리하고 `config` 패키지에 도메인별
> `DataSource`+`SqlSessionFactory`+`@MapperScan` 구성을 추가했다. 상세 → §2 `config` 패키지,
> [docs/db/db.md](db/db.md).

---

## 1. 스택

| 항목 | 선택 | 버전 |
|------|------|------|
| Language | Java | 17 |
| Framework | Spring Boot | 4.0.7 |
| ORM | MyBatis | 4.0.1 |
| DB | MySQL | 8.0 |
| Build | Gradle | Groovy DSL |

> Spring Boot 4.0.7은 2025-11-20 GA 릴리즈. 안정 버전 확인 완료. 다운그레이드 불필요.

---

## 2. 패키지 구조

```
com.toy.cinema
├── booking                        ← 예매 도메인 (Saga 오케스트레이터 상주) ✅ 완성
│   ├── BookingController          ← @RestController, JSON 진입점 (POST /bookings, GET /bookings/{id})
│   ├── BookingFacade              ← 외부 도메인이 booking을 만지는 유일한 창구 인터페이스 ⭐
│   ├── BookingFacadeImpl          ← 구현체 (BookingOrchestrator로 위임만 함)
│   ├── BookingOrchestrator        ← Saga 흐름 조율자 (핵심, Facade 아님 — booking 내부에서만 호출). reserve() + getResult()(지연 재조정)
│   ├── BookingService             ← 독립 트랜잭션 메서드: insertPending/confirm/cancel/findById (각각 REQUIRES_NEW)
│   ├── LockType                   ← PESSIMISTIC/OPTIMISTIC. 요청 파라미터로 받아 seatFacade.hold* 중 무엇을 부를지 결정
│   ├── domain/  Booking           ← booking 한 행 스냅샷
│   ├── mapper/  BookingMapper(+XML), InsertBookingParams, UpdateBookingStatusParams
│   └── dto/     ReservationRequest(scheduleId, seatId, userId, amount, lockType) ← reserve() 파라미터
│                BookingResult     ← BookingFacade.getResult() 리턴 타입
│                BookingCreated    ← POST /bookings 응답 전용 (bookingId를 JSON으로 감쌈)
│
├── seat                           ← 좌석 도메인 ✅ 완성 (다른 도메인의 기준 구조)
│   ├── SeatFacade                 ← 외부 도메인이 좌석을 만지는 유일한 창구 인터페이스 ⭐ (dto만 주고받음)
│   ├── SeatFacadeImpl             ← 구현체 (인프로세스 위임, 나중에 HTTP 클라이언트로 교체 가능)
│   ├── SeatService                ← 상태 전이 + 동시성 제어: holdPessimistic / holdOptimistic / confirm / release / getSeatGrid(조회)
│   ├── domain/
│   │   └── ScheduleSeat           ← schedule_seat 한 행의 SELECT 스냅샷 (SeatService 내부 전용)
│   ├── mapper/
│   │   ├── SeatMapper (+ XML)     ← findForUpdate / findByScheduleIdAndSeatId / updateStatus / updateStatusWithVersion / findGridByScheduleId
│   │   ├── ScheduleSeatKey        ← find 2개 공용 파라미터 (mapper 내부 전용, dto 아님)
│   │   ├── UpdateStatusParams     ← updateStatus 전용 파라미터
│   │   └── UpdateVersionParams    ← updateStatusWithVersion 전용 파라미터
│   └── dto/
│       ├── SeatHoldCommand        ← SeatFacade.hold*() 파라미터
│       ├── SeatConfirmCommand     ← SeatFacade.confirm() 파라미터
│       ├── SeatReleaseCommand     ← SeatFacade.release() 파라미터
│       └── SeatGridItem           ← getSeatGrid() 리턴 원소 (seat+schedule_seat 조인 프로젝션, seatId/rowNum/colNum/status). 순수 조회 응답이라 record 아닌 Lombok @Data (AGENT.md §1)
│
├── screening                       ← 스케줄 탐색 조회 전용 ✅ 완성 (Saga 미참여, Service 없는 예외 — Facade는 다른 도메인과 동일하게 있음)
│   ├── ScheduleController         ← @RestController: GET /schedules, GET /schedules/{id}, GET /schedules/{id}/seats. ScreeningFacade/SeatFacade를 통해서만 접근
│   ├── ScreeningFacade            ← 외부(web 계층)가 screening을 조회하는 유일한 창구 인터페이스 ⭐
│   ├── ScreeningFacadeImpl        ← 구현체 (ScreeningMapper로 그대로 위임만 함 — 상태 전이 로직이 없어 Service 계층은 두지 않음)
│   ├── ScreeningMapper (+XML)     ← movie/theater/schedule 조인 조회 전용. ScreeningFacadeImpl만 사용, 패키지 밖에서 import 금지 (다른 도메인 mapper와 동일 규칙)
│   └── dto/     ScheduleView      ← schedule ⋈ movie ⋈ theater 조인 결과 (scheduleId, movieTitle, theaterName, startTime). 순수 조회 응답이라 record 아닌 Lombok @Data (AGENT.md §1)
│
├── payment                        ← 결제 도메인 ✅ 완성
│   ├── PaymentFacade              ← 외부 도메인이 결제를 만지는 유일한 창구 인터페이스 ⭐ (dto만 주고받음). pay() + findStatusByBookingId()(지연 재조정용)
│   ├── PaymentFacadeImpl          ← 구현체. insertPending → gateway 호출 → markSuccess/Failed 순서로 오케스트레이션(작은 Saga)
│   ├── PaymentService             ← 독립 트랜잭션 메서드만 보유: insertPending / markSuccess / markFailed (각각 REQUIRES_NEW)
│   ├── gateway/                   ← "record + 프로세스 파일이 같은 관심사"라 하나로 묶은 서브패키지
│   │   ├── PaymentGateway         ← 인터페이스 (실제 PG 교체 지점, PgChargeRequest/Result만 주고받음)
│   │   ├── MockPaymentGateway     ← 구현체: 20% 확률 실패 + 인위적 지연(Thread.sleep) 시뮬레이션
│   │   ├── PgChargeRequest        ← Gateway 전용 (amount, paymentKey만 — bookingId는 PG가 모르는 개념)
│   │   └── PgChargeResult         ← Gateway 응답 (success, message). 실패해도 예외 아닌 리턴값
│   ├── domain/  Payment           ← payment 한 행 스냅샷 (중복 payment_key 판단, 재조정 조회에 사용)
│   ├── mapper/  PaymentMapper(+XML), InsertPaymentParams, UpdatePaymentStatusParams
│   └── dto/     PaymentRequest    ← PaymentFacade.pay() 파라미터 (bookingId, amount, paymentKey)
│
├── notification                   ← 알림 도메인 ✅ 완성
│   ├── NotificationFacade         ← 인터페이스
│   ├── NotificationFacadeImpl     ← 구현체 (현재는 로그만 출력, Service 계층 불필요 — DB/외부 호출 없음)
│   └── dto/     NotificationRequest (userId, message)
│
├── common                         ← 공통 ✅ 완성
│   ├── exception                  ← CinemaException, SeatNotAvailableException, SeatConflictException, PaymentFailedException,
│   │                                 GlobalExceptionHandler(@RestControllerAdvice — CinemaException 하위를 409/400 JSON으로 변환), ErrorResponse
│   ├── enums                      ← SeatStatus, BookingStatus, PaymentStatus
│   └── logging                    ← SqlLogFormat — p6spy 콘솔 로그 포맷터 (spy.properties가 참조, 개발용)
│
└── config                         ← 도메인별 DataSource 배선 ✅ 완성 (T-09, 2026-07-21)
    ├── ScreeningDataSourceConfig  ← screening_db 전용 DataSource+SqlSessionFactory+@MapperScan(sqlSessionFactoryRef="screeningSqlSessionFactory")
    ├── SeatDataSourceConfig       ← seat_db 전용 (위와 동일 패턴)
    ├── BookingDataSourceConfig    ← booking_db 전용
    └── PaymentDataSourceConfig    ← payment_db 전용
```

> **도메인별 스키마 분리(T-09)**: `CinemaApplication`엔 더 이상 전역 `@MapperScan`이 없다. 도메인 패키지(`basePackages`)와
> DB 커넥션(`sqlSessionFactoryRef`)을 함께 지정해야 해서, `config` 패키지의 클래스 4개가 각자 자기 도메인만 스캔하고
> 자기 스키마로만 연결한다. `mybatis.mapper-locations`/`configuration.*`(map-underscore-to-camel-case 등)도 YAML이
> 아니라 각 `SqlSessionFactoryBean`에서 직접 설정 — Spring Boot의 자동 설정은 `DataSource`/`SqlSessionFactory`가
> 하나도 없을 때만 동작하는데, 지금은 4개를 수동으로 만들어서 자동 설정 자체가 개입하지 않기 때문.
>
> **⚠️ `PlatformTransactionManager`도 도메인별로 직접 등록해야 함 (T-10 4단계에서 실제로 겪은 버그, 2026-07-21)**:
> `DataSource`와 마찬가지로, Spring Boot는 `DataSource`가 정확히 1개일 때만 `PlatformTransactionManager`를 자동
> 생성해준다. 4개로 쪼갠 뒤 아무도 명시적으로 안 만들어주면 `@EnableTransactionManagement` 자체가 비활성화돼서
> `@Transactional`이 **에러 없이 조용히 무시**된다 — 순차 요청으로는 절대 안 드러나고 진짜 동시성 테스트를 돌려야만
> 발견됨. 그래서 `config` 패키지 4개 클래스 전부에 `PlatformTransactionManager` 빈을 두고(`seatTransactionManager`
> 등), `SeatService`/`PaymentService`/`BookingService`의 `@Transactional`에 `transactionManager = "..."`를 명시한다.
> **새 도메인을 추가할 때 이 빈과 명시를 빠뜨리면 트랜잭션이 아무 말 없이 그냥 안 걸린다** — 반드시 세트로 추가할 것.

> **Facade 경계 규칙**: 각 도메인의 Facade는 인터페이스 + Impl 구현체로 작성하고(나중에 물리 분리 대비), `domain` 객체나 원시값이 아닌 `dto`(Command/Info 등)만 파라미터·리턴 타입으로 사용한다. `BookingController`도 `BookingOrchestrator`를 직접 참조하지 않고 `BookingFacade`를 통해서만 호출한다.
> **mapper 전용 파라미터 객체**(예: `ScheduleSeatKey`, `UpdateStatusParams`, `PgChargeRequest`)는 `dto/`가 아니라 해당 도메인 패키지에 둔다 — 다른 도메인이 알아야 할 개념이 아니라, Service↔Mapper/Gateway 사이의 내부 배관이기 때문.

---

## 3. 경계 규칙 (불변)

### 규칙 1 — 도메인 간 소통은 오직 Facade를 통해서만

```
✅ BookingOrchestrator → SeatFacade.hold()
❌ BookingOrchestrator → SeatService.hold()     직접 호출 금지
❌ BookingOrchestrator → SeatMapper.updateStatus() 직접 호출 금지
```

**이유**: 나중에 seat을 별도 서비스로 물리 분리할 때
`SeatFacade` 구현체만 HTTP 클라이언트로 교체하면 된다.
Facade가 없으면 BookingOrchestrator 전체를 수정해야 한다.

### 규칙 2 — 도메인 내부 클래스는 패키지 밖에서 import 금지

```
✅ com.toy.cinema.booking 패키지에서 BookingMapper import
❌ com.toy.cinema.seat    패키지에서 BookingMapper import
```

**이유**: 도메인 경계를 코드 레벨에서 강제하는 최소한의 장치.
익숙해지면 ArchUnit으로 자동 검증 가능(선택사항).

---

## 4. Saga 흐름 (오케스트레이션 방식)

> **방식 선택 이유**: 코레오그래피(이벤트 방식) 대신 오케스트레이션을 선택.
> 흐름을 `BookingOrchestrator` 한 파일에서 눈으로 따라갈 수 있어 학습에 유리.

### 4-1. 성공 경로

```
[사용자 요청] POST /bookings (ReservationRequest: scheduleId, seatId, userId, amount, lockType)

BookingOrchestrator.reserve(ReservationRequest)
  │
  ├─ ① lockType에 따라 분기
  │       seatFacade.holdPessimistic(SeatHoldCommand) 또는 holdOptimistic(SeatHoldCommand)
  │       → schedule_seat.status: AVAILABLE → HELD
  │       → 짧은 로컬 트랜잭션, 즉시 커밋 (@Transactional on SeatService)
  │
  ├─ hold 성공 후 bookingService.insertPending(...) → booking.status: (없음) → PENDING, bookingId 확보
  │
  ├─ ② paymentFacade.pay(new PaymentRequest(bookingId, amount, paymentKey))
  │       → paymentKey는 BookingOrchestrator가 UUID로 생성
  │       → MockPaymentGateway 호출 (DB 트랜잭션 밖, 실패 가능 지점)
  │       → payment.status: PENDING → SUCCESS
  │
  └─ ③ seatFacade.confirm(SeatConfirmCommand)
          → schedule_seat.status: HELD → BOOKED
          bookingService.confirm(bookingId)
          → booking.status: PENDING → CONFIRMED
          notificationFacade.send(NotificationRequest) — 실패해도 catch해서 로그만 (Todo.md T-07)
```

> **순서 이유**: `booking.confirm()`을 `notification.send()`보다 먼저 실행한다.
> 알림은 부가 기능이므로, 알림 전송이 실패하더라도 이미 확정된 예매 상태에는 영향을 주지 않는다.

### 4-2. 실패 경로 (보상 트랜잭션)

```
② paymentFacade.pay() 에서 PaymentFailedException 발생
  │
  └─ catch → seatFacade.release(SeatReleaseCommand)  ← 보상 트랜잭션
                → schedule_seat.status: HELD → AVAILABLE
                bookingService.cancel(bookingId)
                → booking.status: PENDING → CANCELLED
                (예외를 다시 던지지 않고 bookingId를 그대로 리턴 — 컨트롤러는 항상 결과 페이지로 리다이렉트)
```

### 4-2b. confirm 단계 실패에 대한 지연 재조정

`②` payment가 SUCCESS로 끝난 뒤 `③` 단계(`seatFacade.confirm()` / `bookingService.confirm()`)가
기술적 오류(DB 장애 등)로 실패하면, 결제는 이미 성공했는데 `booking`은 `PENDING`,
좌석은 `HELD`인 상태로 남는다. 이 경우 즉시 복구하지 않고 다음 조회 시점에 맞춘다.

```
GET /bookings/{bookingId}
  → booking.status == PENDING 이고 payment.status == SUCCESS 라면
    그 자리에서 seatFacade.confirm() + bookingService.confirm() 재시도
```

### 4-3. 좌석 상태 머신

```
AVAILABLE ──hold()──▶ HELD ──confirm()──▶ BOOKED
                       │
                       └──release()(보상)──▶ AVAILABLE
```

---

## 5. 트랜잭션 설계 (핵심 학습 포인트)

### ❌ 하면 안 되는 방식

```java
@Transactional  // ← 오케스트레이터 전체에 걸기 금지
public Long reserve(ReservationRequest request) {
    seatFacade.holdPessimistic(...);
    paymentFacade.pay(...);   // ← 이 동안 DB 커넥션·락을 계속 점유
    seatFacade.confirm(...);
}
```

**왜 안 되는가:**
- `pay()`는 외부 시스템(Mock이지만 개념상 외부) 호출 → 수 초 걸릴 수 있음
- 그 동안 `schedule_seat` 행에 락이 걸린 채 커넥션이 유지됨
- 다른 사용자가 같은 좌석을 클릭하면 락 해제를 기다리며 블로킹
- Saga가 존재하는 이유(긴 비즈니스 트랜잭션을 짧은 로컬 트랜잭션으로 분해)가 사라짐

### ✅ 올바른 방식

```java
// BookingOrchestrator: @Transactional 없음
public Long reserve(ReservationRequest request) {
    seatFacade.holdPessimistic(...);  // 내부: @Transactional(REQUIRES_NEW) → 즉시 커밋
    paymentFacade.pay(...);            // 트랜잭션 밖에서 실행
    seatFacade.confirm(...);           // 내부: @Transactional(REQUIRES_NEW) → 즉시 커밋
}

// SeatService: 각 메서드에만 @Transactional(REQUIRES_NEW)  ← 확정 (파라미터는 dto)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void holdPessimistic(SeatHoldCommand cmd) { ... }

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void confirm(SeatConfirmCommand cmd) { ... }

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void release(SeatReleaseCommand cmd) { ... }
```

**`REQUIRES_NEW`를 선택한 이유**: 호출자가 실수로 `@Transactional`을 달더라도
각 단계가 무조건 독립 커밋됨을 코드 레벨에서 강제한다.
`REQUIRED`(기본값)은 합류 가능성이 있어 Saga 의도가 깨질 수 있다.

---

## 6. 동시성 제어 (확정)

"같은 좌석을 두 명이 동시에 누르면?" → `SeatService.hold()`가 원자적으로 처리해야 함.
**대기형(비관적) 락과 충돌감지형(낙관적) 락을 둘 다 구현해서 비교**한다 — 다른 요청을 기다리게 하는 방식(대기형)과
즉시 충돌 예외를 던지는 방식(충돌감지형), 두 실패 모드를 직접 관찰하는 것이 학습 목표.

| 방식 | 구현 | 특징 |
|------|------|------|
| 대기형 (비관적 락) | `SELECT ... FOR UPDATE` | 잡는 순간 다른 트랜잭션을 기다리게 만듦. 충돌이 잦을 때 유리 |
| 충돌감지형 (낙관적 락) | 버전 컬럼 비교 | 잡으려는 순간 충돌 감지 후 예외. 충돌이 드물 때 유리 |

---

## 7. MockPaymentGateway 설계

실제 PG 연동 없이 결제 불확실성을 시뮬레이션한다.

| 시나리오 | 구현 방법 | 학습 목적 |
|---------|---------|---------|
| 20% 확률 결제 실패 | `Random`으로 예외 throw | 보상 트랜잭션 발동 확인 |
| 인위적 지연 | `Thread.sleep(3000)` | 타임아웃·재시도 실습 |
| 중복 요청 방어 | `payment_key` UNIQUE로 DB가 차단 | 멱등성 실습 |

### 확장 경로

```java
// payment/gateway/PaymentGateway.java — 인터페이스
public interface PaymentGateway {
    PgChargeResult pay(PgChargeRequest request);
}

// payment/gateway/MockPaymentGateway.java — 현재 구현체
public class MockPaymentGateway implements PaymentGateway { ... }

// 나중에 교체 가능 (기존 코드 수정 없음)
public class TossPaymentGateway implements PaymentGateway { ... }
```

`PgChargeRequest`/`PgChargeResult`는 PG가 실제로 아는 값(amount, paymentKey)만 담는다 — `PaymentFacade.pay()`의
`PaymentRequest`(bookingId 포함, 우리 내부 개념)와는 별도 타입. §2 패키지 구조 참고.

---

## 8. 사용자(회원) 처리 방식

| 현재 | 나중에 |
|------|--------|
| `user_id`를 요청 파라미터로 수신 (인증 없음) | 로그인·회원가입 로직 추가 개발 |

**이유**: 사용자 인증은 이 프로젝트의 학습 목표(Saga·경계 설계)와 무관.
목표에 집중한 뒤 회원 도메인을 독립적으로 추가하는 것이 경계 설계 실습에도 더 좋음.

---

## 9. 개발 진행 상태

| 항목 | 상태 |
|------|------|
| `build.gradle` 의존성 | ✅ 완료 |
| `application.yaml` DB 설정 | ✅ 완료 — `app.datasource.{screening,seat,booking,payment}` 4개 블록, 비밀번호는 `.env`의 `DB_PASSWORD` 하나로 통일 (T-09, 2026-07-21) |
| `01~04-*-schema.sql` 도메인별 DDL | ✅ 완료 (`schedule_seat.version` 포함, 도메인 간 FK 4개 제거, 번호 접두사로 실행 순서 강제) |
| `05-data.sql` 시드 데이터 | ✅ 완료 (스키마 전환하며 시딩, T-09) |
| `config` 패키지 (도메인별 DataSource+SqlSessionFactory) | ✅ 완료 (T-09, 2026-07-21) |
| `common` (enums, exception) | ✅ 완료 |
| `seat` 도메인 (dto/domain/mapper/Service/Facade) | ✅ 완료 |
| `SeatService` 동시성 구현 | ✅ 완료 (대기형/충돌감지형 락 둘 다) |
| `payment` 도메인 (dto/domain/mapper/gateway/Service/Facade) | ✅ 완료 |
| `MockPaymentGateway` | ✅ 완료 (20% 확률 실패 + 3초 지연) |
| `notification` 도메인 (Facade+Impl, dto) | ✅ 완료 |
| `booking` 도메인 + `BookingOrchestrator` Saga 흐름 | ✅ 완료 (지연 재조정 포함) |
| `BookingController` (POST/GET 라우팅) | ✅ 완료 |
| 실제 HTTP 요청으로 Saga 흐름 테스트 | ✅ 완료 (성공/보상/재조정 경로 curl로 검증, 2026-07-17) |
| `screening` 패키지 (ScheduleController, ScreeningFacade+Impl, ScreeningMapper) | ✅ 완료 (2026-07-18) |
| `BookingController`/`ScheduleController` REST 전환 + `GlobalExceptionHandler` | ✅ 완료 — Thymeleaf 제거, JSON API + 정적 HTML/JS로 전환 (2026-07-18, [STATUS.md](../STATUS.md) 참고) |
| 화면(정적 HTML, `seats.html` polling 포함) | ✅ 완료 — [docs/frontend.md](frontend.md) 참고 |
| p6spy 개발용 SQL 로깅 (`common/logging/SqlLogFormat`) | ✅ 완료 — 파라미터 치환된 완성 SQL 콘솔 출력 (2026-07-18, [STATUS.md](../STATUS.md) 트러블슈팅 기록 참고) |
