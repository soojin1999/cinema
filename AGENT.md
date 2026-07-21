# AGENT.md — AI 개발 규칙 명세

> 이 파일은 AI가 매 작업 전에 읽어야 하는 **개발 제약 명세**다.
> 배경·목표·기술 스택 설명은 [README.md](README.md) 참고.
> 불명확한 상태로 임의 진행 금지. 반드시 사용자에게 먼저 질문할 것.

---

## 1. 불변 규칙 (위반 불가)

### [경계] 도메인 간 소통은 Facade만

```
✅ BookingOrchestrator → SeatFacade.hold()
❌ BookingOrchestrator → SeatService.hold()      직접 호출 금지
❌ BookingOrchestrator → SeatMapper.update()     직접 호출 금지
```

- 한 도메인의 `mapper` / `domain` 클래스는 **그 패키지 밖에서 import 금지**
- Saga 오케스트레이터뿐 아니라 배치(`@Scheduled` 등) 같은 도메인 밖 호출자도 예외 없이 Facade를 통해서만 접근한다
- **Saga에 참여하지 않는 조회 전용 도메인(예: `screening`)도 예외 없이 Facade를 둔다.** "상태 전이가 없다"는 이유로 Facade 자체를 생략하지 않는다 — 생략 가능한 건 로직이 없는 `Service` 계층뿐 (`ScreeningFacadeImpl`이 `Service` 없이 `Mapper`로 바로 위임하는 게 이 패턴, 2026-07-18 결정)

### [아키텍처] 모든 Controller는 REST API (JSON 전용)

- Controller는 전부 `@RestController`로 작성한다. `@Controller` + `Model` + 서버사이드 뷰 렌더링(Thymeleaf 등)은 쓰지 않는다 (2026-07-18, Thymeleaf 제거하고 REST로 전면 전환)
- 화면은 `src/main/resources/static/`의 정적 HTML + Vanilla JS가 `fetch()`로 위 API를 호출해서 그린다. 서버는 View를 만들지 않는다
- 비즈니스 예외(`CinemaException` 하위)는 개별 컨트롤러가 잡지 않고 `common/exception/GlobalExceptionHandler`(`@RestControllerAdvice`)가 공통으로 409/400 JSON으로 변환한다

### [경계] Facade는 dto만 주고받는다

```
seat/
├── SeatFacade         ← 파라미터·리턴 타입은 항상 dto
├── SeatService         ← 상태 전이 + 동시성 제어 (mapper 전용 파라미터 객체는 여기서만 조립)
├── domain/            ← ScheduleSeat.java (schedule_seat 한 행의 SELECT 스냅샷, 밖으로 노출 금지)
├── mapper/             ← SeatMapper, ScheduleSeatKey, UpdateStatusParams 등 (SeatService만 사용, 밖으로 노출 금지)
└── dto/                ← SeatHoldCommand, SeatConfirmCommand, SeatReleaseCommand 등 Facade의 공개 계약
```

- Facade가 `domain` 객체나 원시값을 그대로 반환/수신하지 않는다. domain 클래스가 Facade 시그니처를 타고 밖으로 유출되는 것을 막기 위함
- **`mapper` 전용 파라미터 객체(예: `UpdateStatusParams`)는 `dto/`가 아니라 `mapper/`에 둔다.** `dto/`는 "다른 도메인이 봐도 되는 것"이라는 신호이고, mapper 파라미터는 SQL 조건값(예: `expectedStatus`) 같은 내부 구현 지식을 담고 있어 다른 도메인이 알아서는 안 됨
- **record(데이터)와 프로세스(인터페이스/클래스)가 같은 관심사면 기능별 서브패키지로 함께 묶는다** (예: `payment/gateway/`에 `PaymentGateway`+`MockPaymentGateway`+`PgChargeRequest`+`PgChargeResult`). `mapper/`가 이미 이 원칙(SeatMapper + 파라미터 객체들)을 따르고 있음 — 관심사 단위 폴더링이지 record 전용 폴더를 별도로 만들진 않음
- dto와 domain 객체는 **Java `record`**로 작성한다 (불변). dto는 "그 순간 한 번 쓰고 버려지는 요청값", domain 객체는 "SELECT 결과를 읽어서 검증만 하고 버려지는 스냅샷"이라 상태가 바뀔 이유가 없음. 상태 전이는 항상 객체를 고치는 게 아니라 새 UPDATE SQL로 수행
  - **예외**: SELECT 결과가 가공·참조 없이 그대로 클라이언트 응답으로 리턴되는 "순수 조회 응답" dto(예: `ScheduleView`, `SeatGridItem`)는 record 대신 Lombok `@Data` 클래스로 작성한다. MyBatis가 `<constructor>` 매핑 없이 `resultType`만으로 자동 매핑할 수 있어 XML이 짧아짐 (2026-07-18 결정, Todo.md 기록). **판단 기준**: API 호출부터 응답까지 그 dto의 값을 코드에서 참조·분기·가공하는 곳이 하나라도 생기면 즉시 record로 되돌린다 — null 체크(`schedule == null`)처럼 참조가 아닌 값 자체를 안 보는 경우는 예외 대상 유지 가능
  - command dto(`SeatHoldCommand` 등), 요청 dto(`ReservationRequest`), domain 객체(`ScheduleSeat`, `Booking`, `Payment`)는 전부 필드가 코드에서 참조되므로 이 예외 대상이 아니다 — 계속 record
- **같은 모양이어도 독립적으로 진화할 수 있는 두 지점은 공유하지 않고 각자 정의한다.** dto뿐 아니라 SQL도 동일하게 적용 — 지금 컬럼이 똑같아도 `<sql>`/`<include>`로 묶지 않고 쿼리마다 SELECT 컬럼을 명시한다 (`ScreeningMapper.xml`의 `findAllSchedules`/`findScheduleById`가 이 패턴, 2026-07-18 결정). 근거는 위 command dto를 안 합치는 이유와 동일 — 나중에 한쪽만 바뀌어도 다른 쪽엔 영향 없게 하기 위함
- Facade는 **인터페이스 + Impl 구현체**로 작성한다 (예: `SeatFacade` + `SeatFacadeImpl`, `PaymentGateway` + `MockPaymentGateway`와 동일 패턴). 나중에 물리 분리 시 구현체만 HTTP 클라이언트로 교체하기 위함 — 호출부(BookingOrchestrator 등) 코드는 손대지 않음

### [Saga] 오케스트레이터에 `@Transactional` 금지

```java
// ❌ 금지
@Transactional
public void reserve(...) { hold(); pay(); confirm(); }

// ✅ 올바른 방식 — 각 단계 메서드 내부에만 @Transactional
public void reserve(...) {
    seatFacade.hold(...);      // 내부: @Transactional → 즉시 커밋
    paymentFacade.pay(...);    // 트랜잭션 밖 실행
    seatFacade.confirm(...);   // 내부: @Transactional → 즉시 커밋
}
```

- `release()` (보상 트랜잭션)는 **멱등적으로** 구현. AVAILABLE 상태에 release가 다시 와도 안전해야 함.

### [범위] 절대 추가하지 말 것

Kafka / RabbitMQ / MSA 물리 분리 / K8s / 실제 PG 연동

### [예외] 보상 트랜잭션 발동 조건 (확정)

```java
// BookingOrchestrator.reserve() 내부
try {
    paymentFacade.pay(...);
} catch (PaymentFailedException e) {
    // ✅ 예측된 비즈니스 실패 → 보상 발동
    seatFacade.release(new SeatReleaseCommand(scheduleId, seatId));   // HELD → AVAILABLE
    bookingService.cancel(bookingId);          // PENDING → CANCELLED
} catch (Exception e) {
    // ✅ 예측 외 기술 오류(DB 장애 등) → 보상 없이 위로 던짐
    //    좌석은 HELD 상태로 남음 (추후 HELD 타임아웃으로 회수)
    throw e;
}
```

### [예외] 예외 계층 구조 (확정)

```
RuntimeException
└── CinemaException          ← 모든 비즈니스 예외의 베이스 (common 패키지)
    ├── SeatNotAvailableException   ← hold() 시 읽은 시점에 이미 HELD / BOOKED
    ├── SeatConflictException       ← 충돌감지형(낙관적) 락: 읽을 땐 AVAILABLE이었으나 UPDATE 순간 다른 트랜잭션이 먼저 채감 (재시도 여지 있음)
    ├── PaymentFailedException      ← pay() 시 결제 거절 → 보상 발동 트리거
    └── NotificationFailedException ← send() 실패. 보상 트리거 아님 (booking은 이미 확정됨). BookingOrchestrator가 이 타입을 어떻게 다룰지는 미결 (Todo.md)
```

- `SeatNotAvailableException`: hold() 단계 실패. 아직 booking이 생성 안 됐으므로 보상 불필요.
- `SeatConflictException`: `SeatService.holdOptimistic()` 전용. 지금은 재시도 없이 그대로 예외만 던짐 — 재시도 로직은 별도 논제로 `Todo.md`에 추적.
- `PaymentFailedException`: pay() 단계 실패. 보상(release + cancel) 발동.
- `NotificationFailedException`: send() 단계 실패. 예매는 이미 확정된 뒤라 보상 대상 아님. 지금은 로그만 찍는 구현이라 사실상 발생 안 함 — 나중에 진짜 발송 로직으로 교체될 때를 대비한 계약.
- 그 외 `Exception`: 예측 외 오류. 보상 없이 던짐. 좌석은 HELD 잔류.

### [트랜잭션] Saga 각 단계 전파 방식 (확정)

```java
// SeatService, PaymentService 등 Saga 각 단계 메서드 (파라미터는 dto)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void holdPessimistic(SeatHoldCommand cmd) { ... }

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void holdOptimistic(SeatHoldCommand cmd) { ... }

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void confirm(SeatConfirmCommand cmd) { ... }

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void release(SeatReleaseCommand cmd) { ... }  // 보상, 멱등적으로 구현됨
```

**이유**: 호출자(오케스트레이터)가 실수로 `@Transactional`을 달더라도
각 단계가 무조건 독립 커밋됨을 코드 레벨에서 강제한다.
`REQUIRED`는 합류 가능성이 있어 Saga 의도가 깨질 수 있음.

---

## 2. Saga 실행 흐름

> 진입점은 `BookingController → BookingFacade → BookingOrchestrator`.
> 다른 도메인이 booking에 접근할 일이 생기면(예: 회원 도메인) `BookingFacade`를 통한다 — orchestrator 직접 참조 금지.

```
reserve(ReservationRequest: scheduleId, seatId, userId, amount, lockType)
  ①  lockType에 따라 seatFacade.holdPessimistic/holdOptimistic(SeatHoldCommand) → schedule_seat: AVAILABLE → HELD
  hold 성공 후 bookingService.insertPending(...) → booking: (없음) → PENDING, bookingId 확보
  ②  paymentFacade.pay(new PaymentRequest(bookingId, amount, paymentKey))  → 트랜잭션 밖, 실패 가능 (paymentKey는 UUID로 여기서 생성)
  ③-성공  seatFacade.confirm(SeatConfirmCommand)    → HELD → BOOKED
           bookingService.confirm(bookingId)         → PENDING → CONFIRMED
           notificationFacade.send(NotificationRequest) → 실패해도 catch해서 로그만 (예매 성공에 영향 없음)
  ③-실패  seatFacade.release(SeatReleaseCommand)    → HELD → AVAILABLE  (보상)
           bookingService.cancel(bookingId)          → PENDING → CANCELLED
```

### [예외] confirm 단계 실패에 대한 지연 재조정 (확정)

`②`가 SUCCESS로 끝난 뒤 `③`(`seatFacade.confirm()` / `bookingService.confirm()`)이 기술적 오류로 실패하면,
결제는 이미 성공했는데 `booking`은 `PENDING`, 좌석은 `HELD`로 남는다.
이 경우 즉시 복구하지 않고 **다음 조회 시점에 재조정**한다.

```
GET /bookings/{bookingId}
  → booking.status == PENDING 이고 payment.status == SUCCESS 라면
    그 자리에서 seatFacade.confirm() + bookingService.confirm() 재시도
```

---

## 3. DB 핵심 제약

| 테이블 | PK | 주요 제약 |
|--------|-----|---------|
| `schedule_seat` | `(schedule_id, seat_id)` 복합 | 두 컬럼이 PK이자 FK. 락 최소 단위 |
| `booking` | `booking_id` | `(schedule_id, seat_id)` → `schedule_seat` 복합 FK |
| `payment` | `payment_id` | `payment_key` UNIQUE → DB 레벨 멱등성 |
| 모든 status | ENUM | DB 레벨 상태값 제약 |

상세 → [docs/db/db.md](docs/db/db.md) / [docs/db/diagram.md](docs/db/diagram.md)

### [스타일] DB 네이밍은 소문자 snake_case 고정

테이블/컬럼명은 항상 소문자 snake_case로 쓴다 (`schedule_seat`, `movie_id` 등). 대문자·camelCase 금지.
이유: (1) MySQL 공식 권장 컨벤션이기도 하고, (2) 이 프로젝트는 Windows 호스트 + Linux(Docker) MySQL 조합이라
테이블명 대소문자 구분 여부가 OS마다 달라서(Linux는 구분함) 대문자를 섞으면 크로스플랫폼 버그가 날 수 있음,
(3) `map-underscore-to-camel-case: true` 설정이 이 컨벤션을 전제로 `movie_id` ↔ `movieId` 자동 변환을 해줌.

---

## 4. 동시성 제어 (확정)

`SeatService.hold()`는 **대기형(비관적) 락**과 **충돌감지형(낙관적) 락**을 둘 다 구현해서 비교한다.
- 대기형 = 잠그고 다른 요청을 기다리게 함
- 충돌감지형 = 잠그지 않고 진행하다가, 충돌이 나면 그 자리에서 바로 실패시킴

토이 프로젝트 목적상 이 두 실패 모드(대기 vs 즉시 충돌 예외)를 직접 관찰하는 것이 학습 목표.
트랜잭션 전파는 위 §1 `REQUIRES_NEW`로 확정됨.

| 방식 | 구현 | `SeatService` 메서드 |
|------|------|------|
| 대기형 (비관적) | `SELECT ... FOR UPDATE` (`findForUpdate`) → 조건부 UPDATE (`updateStatus`) | `holdPessimistic()` |
| 충돌감지형 (낙관적) | 일반 `SELECT`(`findByScheduleIdAndSeatId`) → `version` 조건부 UPDATE (`updateStatusWithVersion`) | `holdOptimistic()` |

- `schedule_seat`에 `version INT NOT NULL DEFAULT 0` 컬럼 추가 (충돌감지형 락의 비교 기준). 상세 → [docs/db/db.md](docs/db/db.md)
- `confirm()`/`release()`는 락 종류를 나누지 않는다 — `hold()`로 이미 좌석을 독점한 뒤에만 호출되므로 경합이 없음. 단순 조건부 UPDATE(`updateStatus`)만 사용
- 충돌감지형 락에서 충돌(UPDATE 영향 행 0) 발생 시 `SeatConflictException` 발생. 재시도 로직은 미결 (Todo.md T-06)

---

## 5. 사용자 처리 방식 (확정)

- 현재: `user_id`를 요청 파라미터로 수신 (인증 없음)
- 나중에: 회원 도메인 추가 개발

---

## 6. 협업 방식

| 영역 | 방식 |
|------|------|
| 백엔드 / DB 설계 | **티키타카** — 트레이드오프를 먼저 제시, 정답 먼저 내놓지 않음 |
| 프론트엔드 (정적 HTML+JS) | **AI 주도** — 직접 작성 후 원리를 짧게 설명 |
| 코드 작성(구현 단계) | **단계별 진행 + 설명.** 사용자가 학습 목적이므로 한 번에 다 만들지 않는다. 파일/클래스 단위로 끊어서 "왜 이렇게 했는지, 무슨 문제를 해결하는지, 뭐가 좋은지, 무슨 패턴/개념인지"를 먼저 설명하고, 다음 단계로 넘어가기 전에 확인받는다 |
| 실행 검증 | **사용자가 명시적으로 요청하기 전까지 AI가 직접 서버를 실행하거나 브라우저로 구동해서 테스트하지 않는다** (예: "지금 localhost 띄워서 테스트해봐" 같은 명시적 지시가 있을 때만). `./gradlew compileJava` 같은 컴파일 확인까지는 자유롭게 함 — 컴파일과 "실행+브라우저 조작"을 구분할 것 (2026-07-18) |

### [스타일] 코딩 컨벤션

- 의존성 주입은 **생성자 주입만** 사용 (`private final` 필드 + Lombok `@RequiredArgsConstructor`). 필드 주입(`@Autowired` on field) 금지 — 누락된 의존성을 기동 시점에 바로 잡아내고, 테스트에서 mock을 생성자로 바로 넣을 수 있게 하기 위함
- **새 매퍼 인터페이스를 만들면 반드시 `@Mapper`(org.apache.ibatis.annotations.Mapper)를 붙인다.** T-09(2026-07-21, DB 스키마 분리) 이후 전역 `@MapperScan`은 없고, `config` 패키지의 도메인별 `DataSourceConfig`(`SeatDataSourceConfig` 등)가 각자 `@MapperScan(basePackages = "com.toy.cinema.<도메인>", annotationClass = Mapper.class, sqlSessionFactoryRef = "...")`로 자기 도메인만 스캔한다 — 새 도메인을 추가하면 이 config 클래스도 함께 만들어야 한다. `annotationClass` 없이 `basePackages`만 넓게 스캔하면 `SeatFacade` 같은 일반 인터페이스까지 매퍼로 착각해 가짜 프록시가 만들어지는 사고가 실제로 발생했음(`Invalid bound statement` 에러) — 그래서 각 매퍼에 `@Mapper`를 명시하고 `annotationClass`로 스캔 대상을 제한하는 원칙은 그대로 유지
- **파라미터가 2개 이상인 메서드는 무조건 dto(record)로 묶어서 받는다.** 원시값을 여러 개 나열하지 않는다 — 특히 같은 타입이 여럿이면(예: `Long scheduleId, Long seatId`) 순서를 실수로 바꿔도 컴파일러가 못 잡는 위험이 있음. `BookingController`/`BookingFacade`/`BookingOrchestrator`의 `reserve()`가 이 규칙 적용 대상 (`ReservationRequest`로 묶음). 파라미터가 1개면 그대로 원시값/단일 타입으로 받아도 됨
- **`@Transactional`을 쓰는 새 Service를 만들면 반드시 `transactionManager`를 명시한다** (예: `@Transactional(transactionManager = "seatTransactionManager", propagation = Propagation.REQUIRES_NEW)`). T-09 이후 `DataSource`가 도메인별로 4개라 Spring Boot가 `PlatformTransactionManager`를 자동으로 못 만들어주고, `config` 패키지의 도메인별 `DataSourceConfig`가 각자 명시적으로 등록한 빈(`seatTransactionManager` 등)을 이름으로 지정해서 써야 한다. **이걸 빠뜨리면 에러 없이 `@Transactional`이 조용히 무시된다** — 실제로 T-10 4단계 동시성 테스트에서 이 사고가 났었음(순차 요청으로는 절대 안 드러나고, 진짜 동시 요청을 만들어야만 발견됨). 새 도메인을 추가하면 그 도메인의 `DataSourceConfig`에 `PlatformTransactionManager` 빈부터 등록할 것

---

## 7. 문서 갱신 규칙

코드 변경 시 반드시 해당 문서도 함께 갱신한다.

| 변경 영역 | 갱신 파일 |
|----------|---------|
| DB 구조·컬럼·키 | `docs/db/db.md` + `docs/db/diagram.md` |
| 아키텍처·Saga·트랜잭션 | `docs/backend.md` |
| 화면·라우팅 | `docs/frontend.md` |
| 테스트 코드 추가·범위 변경 | `docs/testing.md` |
| 파일 상태·전체 방향·세션 시작 지점 | [STATUS.md](STATUS.md) |
| 미결 논제 발생 또는 해결 | `Todo.md` |
| **도메인 하나 완성 시** | `docs/flow.md` — **사용자가 요청하지 않아도 자동으로** 시퀀스 다이어그램 갱신 |

> 지금 어디까지 됐는지, 다음 세션에 뭘 할지는 [STATUS.md](STATUS.md) 참고. 이 파일(AGENT.md)은 세션이 바뀌어도 변하지 않는 규칙만 담는다.
