# 코드 흐름도

> **최종 수정**: 2026-07-24 (T-04 `BookingTimeoutBatch` 추가)
> **목적**: 실제로 만든 파일들이 서로를 어떻게 호출하는지 시퀀스 다이어그램으로 추적한다.
> 설계 이유(왜 이렇게 만들었는가)는 [backend/backend.md](backend/backend.md) 참고 — 이 문서는 "무엇이 무엇을 부르는가"에만 집중한다.
> **구조(정적) vs 흐름(동적)**: 인터페이스/구현체 경계나 도메인 간 의존 "구조", 클래스/패키지 다이어그램만 보고 싶으면
> [backend/diagram.md](backend/diagram.md) 참고 — 이 문서(§1 이하)는 HTTP 라우팅·DB 타깃까지 포함한 실제 호출
> 경로와 시간 순서(시퀀스)에 집중한다.
> **갱신 규칙**: 도메인 하나가 완성될 때마다 사용자가 말하지 않아도 자동으로 이 문서를 갱신한다 (AGENT.md §7).
> 모든 Controller는 `@RestController`다 — 뷰를 만들지 않고 JSON만 응답한다. 화면(정적 HTML/JS)이 fetch로 호출한다.

---

## 1. 전체 그림 (도메인 간 호출) ✅ 전체 완성 — 백엔드 + 프론트 end-to-end 연결됨

```mermaid
graph TD
    Static["static/*.html (fetch)"] -->|"GET /schedules, /schedules/{id}, /schedules/{id}/seats"| ScheduleController
    Static -->|"POST /bookings, GET /bookings/{id}"| Controller

    ScheduleController["ScheduleController (screening)"] --> ScreeningFacade["ScreeningFacade"]
    ScreeningFacade --> ScreeningMapper["ScreeningMapper (+XML)"]
    ScreeningMapper --> DB4[("MySQL: schedule ⋈ movie ⋈ theater")]
    ScheduleController -->|"좌석 격자 조회"| SeatFacade

    Controller["BookingController"] -->|"POST /bookings"| BFacade["BookingFacade"]
    Controller -->|"GET /bookings/{id}"| BFacade
    BFacade --> Orchestrator["BookingOrchestrator"]
    Orchestrator -->|"① hold"| SeatFacade["SeatFacade"]
    Orchestrator -->|"② pay"| PaymentFacade["PaymentFacade"]
    Orchestrator -->|"③ confirm/release"| SeatFacade
    Orchestrator -->|"③ send"| NotiFacade["NotificationFacade"]
    Orchestrator -->|"지연 재조정: 상태 조회"| PaymentFacade
    Orchestrator --> BookingService["BookingService"]

    AdminBatch["BookingTimeoutBatchController (수동) / @Scheduled(주석, 미사용)"] -->|"T-04"| Batch["BookingTimeoutBatch"]
    Batch --> BookingService
    Batch -->|"tryConfirmIfPaid 재사용"| Orchestrator
    Batch -->|"release"| SeatFacade

    SeatFacade --> SeatService["SeatService"]
    SeatService --> SeatMapper["SeatMapper (+XML)"]
    SeatMapper --> DB1[("MySQL: schedule_seat ⋈ seat")]

    PaymentFacade --> PaymentService["PaymentService"]
    PaymentFacade --> Gateway["PaymentGateway → MockPaymentGateway"]
    PaymentService --> PaymentMapper["PaymentMapper (+XML)"]
    PaymentMapper --> DB2[("MySQL: payment")]

    NotiFacade --> Log[("로그 출력만, DB/외부 호출 없음")]

    BookingService --> BookingMapper["BookingMapper (+XML)"]
    BookingMapper --> DB3[("MySQL: booking")]

    Controller -.->|"CinemaException 발생 시"| GEH["GlobalExceptionHandler → 409/400 JSON"]
```

백엔드·프론트·에러 처리까지 전부 연결되어 브라우저로 목록→좌석선택→예매→결과 전체 플로우 실제 검증 완료 (2026-07-18).
남은 건 `T-04`(HELD 타임아웃)뿐 — `T-06`(낙관적 락 충돌 재시도)은 재시도 없이 그대로 예외를 던지는 것으로 결정 완료.

---

## 2. `screening` (스케줄 탐색) ✅ 완성

Saga에 참여하지 않는 순수 조회. `ScheduleController`도 다른 도메인과 동일하게 `ScreeningFacade`를 거쳐서만
`ScreeningMapper`에 닿는다 — 상태 전이 로직이 없어 Service 계층만 생략했다 (docs/backend/backend.md §2 참고).

```mermaid
sequenceDiagram
    participant Browser as static/*.html (fetch)
    participant Controller as ScheduleController
    participant Facade as ScreeningFacade / ScreeningFacadeImpl
    participant Mapper as ScreeningMapper (+XML)
    participant DB as MySQL: schedule ⋈ movie ⋈ theater

    Browser->>Controller: GET /schedules
    Controller->>Facade: findAllSchedules()
    Facade->>Mapper: findAllSchedules()
    Mapper->>DB: SELECT ... JOIN movie, theater
    DB-->>Mapper: List<ScheduleView>
    Mapper-->>Facade: List<ScheduleView>
    Facade-->>Controller: List<ScheduleView>
    Controller-->>Browser: 200 JSON

    Browser->>Controller: GET /schedules/{id}  (좌석 화면 헤더용)
    Controller->>Facade: findScheduleById(id)
    Facade->>Mapper: findScheduleById(id)
    Mapper-->>Facade: ScheduleView 또는 null
    Facade-->>Controller: ScheduleView 또는 null
    Controller-->>Browser: 200 JSON 또는 404
```

좌석 격자(`GET /schedules/{id}/seats`)는 screening 소유가 아니라 `SeatFacade.getSeatGrid()`를 그대로 호출한다 — §3 참고.

---

## 3. `seat` 도메인 ✅ 완성

### 3-1. `holdPessimistic` — 대기형 (비관적 락)

```mermaid
sequenceDiagram
    participant Caller as 호출자 (예: BookingOrchestrator)
    participant Facade as SeatFacade / SeatFacadeImpl
    participant Service as SeatService
    participant Mapper as SeatMapper (+XML)
    participant DB as MySQL: schedule_seat

    Caller->>Facade: holdPessimistic(SeatHoldCommand)
    Facade->>Service: holdPessimistic(cmd)
    Service->>Mapper: findForUpdate(ScheduleSeatKey)
    Mapper->>DB: SELECT ... FOR UPDATE
    DB-->>Mapper: ScheduleSeat 행 (락 걸림, 다른 트랜잭션은 대기)
    Mapper-->>Service: ScheduleSeat
    alt status != AVAILABLE
        Service-->>Caller: throw SeatNotAvailableException
    else AVAILABLE
        Service->>Mapper: updateStatus(UpdateStatusParams: AVAILABLE→HELD)
        Mapper->>DB: UPDATE ... WHERE status='AVAILABLE'
        DB-->>Mapper: affected=1 (락 안에서 실행되므로 항상 성공)
        Service-->>Facade: 정상 종료 (REQUIRES_NEW 커밋, 락 해제)
        Facade-->>Caller: void
    end
```

### 3-2. `holdOptimistic` — 충돌감지형 (낙관적 락)

```mermaid
sequenceDiagram
    participant Caller as 호출자
    participant Facade as SeatFacade / SeatFacadeImpl
    participant Service as SeatService
    participant Mapper as SeatMapper (+XML)
    participant DB as MySQL: schedule_seat

    Caller->>Facade: holdOptimistic(SeatHoldCommand)
    Facade->>Service: holdOptimistic(cmd)
    Service->>Mapper: findByScheduleIdAndSeatId(ScheduleSeatKey)
    Mapper->>DB: SELECT (잠금 없음)
    DB-->>Mapper: ScheduleSeat 행 (status, version)
    Mapper-->>Service: ScheduleSeat
    alt status != AVAILABLE
        Service-->>Caller: throw SeatNotAvailableException
    else AVAILABLE
        Service->>Mapper: updateStatusWithVersion(UpdateVersionParams: version=읽은값)
        Mapper->>DB: UPDATE ... WHERE version=?
        alt 그 사이 다른 트랜잭션이 먼저 바꿈
            DB-->>Mapper: affected=0
            Mapper-->>Service: 0
            Service-->>Caller: throw SeatConflictException
        else 충돌 없음
            DB-->>Mapper: affected=1
            Mapper-->>Service: 1
            Service-->>Facade: 정상 종료
            Facade-->>Caller: void
        end
    end
```

### 3-3. `confirm` / `release` — 단순 조건부 UPDATE (락 경합 없음)

```mermaid
sequenceDiagram
    participant Caller as 호출자
    participant Facade as SeatFacade / SeatFacadeImpl
    participant Service as SeatService
    participant Mapper as SeatMapper (+XML)
    participant DB as MySQL: schedule_seat

    Caller->>Facade: confirm(SeatConfirmCommand) 또는 release(SeatReleaseCommand)
    Facade->>Service: confirm(cmd) / release(cmd)
    Service->>Mapper: updateStatus(UpdateStatusParams)
    Mapper->>DB: UPDATE ... WHERE status='HELD' (confirm: →BOOKED, release: →AVAILABLE)
    DB-->>Mapper: affected 0 또는 1 (리턴값 확인 안 함 → release는 이 덕분에 멱등)
    Mapper-->>Service: (무시)
    Service-->>Facade: 정상 종료
    Facade-->>Caller: void
```

### 3-4. `getSeatGrid` — 좌석 격자 조회 (ScheduleController, polling 재사용)

```mermaid
sequenceDiagram
    participant Caller as ScheduleController
    participant Facade as SeatFacade / SeatFacadeImpl
    participant Service as SeatService
    participant Mapper as SeatMapper (+XML)
    participant DB as MySQL: schedule_seat ⋈ seat

    Caller->>Facade: getSeatGrid(scheduleId)
    Facade->>Service: getSeatGrid(scheduleId)
    Service->>Mapper: findGridByScheduleId(scheduleId)
    Mapper->>DB: SELECT ... JOIN seat (row_num, col_num) — 잠금 없음
    DB-->>Mapper: List<SeatGridItem>
    Mapper-->>Service: List<SeatGridItem>
    Service-->>Facade: List<SeatGridItem>
    Facade-->>Caller: List<SeatGridItem>
```

---

## 4. `payment` 도메인 ✅ 완성

### 4-1. `pay()` — 정상 성공 경로

```mermaid
sequenceDiagram
    participant Caller as 호출자 (예: BookingOrchestrator)
    participant Facade as PaymentFacadeImpl
    participant Service as PaymentService
    participant Gateway as MockPaymentGateway
    participant DB as MySQL: payment

    Caller->>Facade: pay(PaymentRequest)
    Facade->>Service: insertPending(request)
    Service->>DB: INSERT ... status=PENDING
    DB-->>Service: OK
    Service-->>Facade: true (gateway 호출 필요)

    Facade->>Gateway: pay(PgChargeRequest)
    Note over Gateway: Thread.sleep(3000) + 20% 확률 실패
    Gateway-->>Facade: PgChargeResult(success=true)

    Facade->>Service: markSuccess(paymentKey)
    Service->>DB: UPDATE status=SUCCESS
    Facade-->>Caller: void
```

### 4-2. `pay()` — 결제 거절 경로

```mermaid
sequenceDiagram
    participant Facade as PaymentFacadeImpl
    participant Service as PaymentService
    participant Gateway as MockPaymentGateway
    participant DB as MySQL: payment

    Facade->>Service: insertPending(request)
    Service-->>Facade: true
    Facade->>Gateway: pay(PgChargeRequest)
    Gateway-->>Facade: PgChargeResult(success=false)
    Facade->>Service: markFailed(paymentKey)
    Service->>DB: UPDATE status=FAILED
    Facade-->>Facade: throw PaymentFailedException
    Note over Facade: 이 예외를 BookingOrchestrator가 catch해서 보상 트랜잭션 발동 (§5-2 참고)
```

### 4-3. `pay()` — 중복 `payment_key` 경로 (재시도 상황)

```mermaid
sequenceDiagram
    participant Facade as PaymentFacadeImpl
    participant Service as PaymentService
    participant DB as MySQL: payment

    Facade->>Service: insertPending(request)
    Service->>DB: INSERT (같은 payment_key)
    DB-->>Service: DuplicateKeyException (UNIQUE 제약)
    Service->>DB: findByPaymentKey(paymentKey)
    DB-->>Service: 기존 Payment 행

    alt 기존 status=SUCCESS
        Service-->>Facade: false (멱등 — gateway 재호출 안 함)
    else 기존 status=FAILED
        Service-->>Facade: throw PaymentFailedException
    else 기존 status=PENDING
        Service-->>Facade: throw IllegalStateException (예측 외 상황, 버블업)
    end
```

---

## 5. `notification` 도메인 ✅ 완성

Service 계층이 없다 — DB도, 외부 호출도 없어서 Facade가 바로 처리한다.

```mermaid
sequenceDiagram
    participant Caller as 호출자 (예: BookingOrchestrator)
    participant Facade as NotificationFacade / NotificationFacadeImpl

    Caller->>Facade: send(NotificationRequest)
    Facade->>Facade: log.info(userId, message)
    Facade-->>Caller: void
    Note over Facade: 지금은 실패할 일이 거의 없음.<br/>나중에 진짜 이메일/SMS로 교체되면 NotificationFailedException을 던질 수 있음 (BookingOrchestrator가 이미 이 예외를 넓게 catch해서 로그만 남기도록 처리됨 — §5-1 참고)
```

---

## 6. `booking` 도메인 ✅ 완성 — 전체 Saga가 여기서 조립됨

### 6-1. `reserve()` — 성공 경로 (POST /bookings)

```mermaid
sequenceDiagram
    participant Controller as BookingController
    participant Facade as BookingFacade / BookingFacadeImpl
    participant Orch as BookingOrchestrator
    participant SeatFacade
    participant BookingSvc as BookingService
    participant PaymentFacade
    participant NotiFacade as NotificationFacade

    Controller->>Facade: reserve(ReservationRequest)
    Facade->>Orch: reserve(request)

    Orch->>SeatFacade: holdPessimistic 또는 holdOptimistic (lockType에 따라 분기)
    Note over SeatFacade: AVAILABLE → HELD

    Orch->>BookingSvc: insertPending(scheduleId, seatId, userId)
    BookingSvc-->>Orch: bookingId (LAST_INSERT_ID)

    Orch->>PaymentFacade: pay(bookingId, amount, UUID paymentKey)
    Note over PaymentFacade: 3초 지연 + 20% 실패 확률. 여기서는 성공 가정

    Orch->>SeatFacade: confirm(scheduleId, seatId)
    Note over SeatFacade: HELD → BOOKED
    Orch->>BookingSvc: confirm(bookingId)
    Note over BookingSvc: PENDING → CONFIRMED

    Orch->>NotiFacade: send(userId, "예매가 완료되었습니다.")
    Note over NotiFacade: 실패해도 catch해서 로그만 (예매 결과에 영향 없음)

    Orch-->>Facade: bookingId
    Facade-->>Controller: bookingId
    Controller-->>Controller: redirect:/bookings/{bookingId}
```

### 6-2. `reserve()` — 결제 실패 경로 (보상 트랜잭션)

```mermaid
sequenceDiagram
    participant Orch as BookingOrchestrator
    participant SeatFacade
    participant BookingSvc as BookingService
    participant PaymentFacade

    Orch->>SeatFacade: holdPessimistic/holdOptimistic
    Note over SeatFacade: AVAILABLE → HELD
    Orch->>BookingSvc: insertPending(...)
    BookingSvc-->>Orch: bookingId

    Orch->>PaymentFacade: pay(...)
    PaymentFacade-->>Orch: throw PaymentFailedException

    Note over Orch: catch (PaymentFailedException) — 보상 발동
    Orch->>SeatFacade: release(scheduleId, seatId)
    Note over SeatFacade: HELD → AVAILABLE
    Orch->>BookingSvc: cancel(bookingId)
    Note over BookingSvc: PENDING → CANCELLED

    Orch-->>Orch: return bookingId (예외를 던지지 않음 — 컨트롤러는 항상 결과 페이지로 리다이렉트)
```

### 6-3. `getResult()` — 지연 재조정 (GET /bookings/{id})

```mermaid
sequenceDiagram
    participant Controller as BookingController
    participant Facade as BookingFacade / BookingFacadeImpl
    participant Orch as BookingOrchestrator
    participant BookingSvc as BookingService
    participant PaymentFacade
    participant SeatFacade

    Controller->>Facade: getResult(bookingId)
    Facade->>Orch: getResult(bookingId)
    Orch->>BookingSvc: findById(bookingId)
    BookingSvc-->>Orch: Booking(status=...)

    alt status == PENDING
        Orch->>PaymentFacade: findStatusByBookingId(bookingId)
        PaymentFacade-->>Orch: PaymentStatus
        alt PaymentStatus == SUCCESS
            Note over Orch: payment는 성공했는데 confirm이 실패했던 상황 → 재시도
            Orch->>SeatFacade: confirm(scheduleId, seatId)
            Orch->>BookingSvc: confirm(bookingId)
            Orch-->>Facade: BookingResult(CONFIRMED)
        else 그 외
            Orch-->>Facade: BookingResult(PENDING)
        end
    else CONFIRMED 또는 CANCELLED
        Orch-->>Facade: BookingResult(현재 상태 그대로)
    end

    Facade-->>Controller: BookingResult
    Controller-->>Controller: booking-result 뷰 렌더링
```

### 6-4. `BookingTimeoutBatch.reconcilePendingBookings()` — HELD 타임아웃 회수 (T-04, 2026-07-24)

`@Scheduled`는 아직 주석 처리 상태(사용자가 직접 해제 예정)라 지금은 `POST /admin/batch/reconcile-pending-bookings`
(`BookingTimeoutBatchController`)로만 호출된다. 두 분기(confirm 구제/release+cancel) 다 수동 검증 완료(2026-07-24).

```mermaid
sequenceDiagram
    participant Trigger as BookingTimeoutBatchController (수동) / @Scheduled(주석, 미사용)
    participant Batch as BookingTimeoutBatch
    participant BookingSvc as BookingService
    participant Orch as BookingOrchestrator
    participant PaymentFacade
    participant SeatFacade

    Trigger->>Batch: reconcilePendingBookings()
    Batch->>BookingSvc: findStalePending(now - 1분)
    BookingSvc-->>Batch: List<Booking> (status=PENDING, created_at < cutoff)

    loop 각 stale booking
        Batch->>Orch: tryConfirmIfPaid(bookingId, scheduleId, seatId)
        Orch->>PaymentFacade: findStatusByBookingId(bookingId)
        PaymentFacade-->>Orch: PaymentStatus

        alt PaymentStatus == SUCCESS
            Note over Orch: 결제는 성공했는데 confirm이 못 됐던 상황 → 구제
            Orch->>SeatFacade: confirm(scheduleId, seatId)
            Orch->>BookingSvc: confirm(bookingId)
            Orch-->>Batch: true
        else 그 외
            Orch-->>Batch: false
            Batch->>SeatFacade: release(scheduleId, seatId)
            Note over SeatFacade: HELD → AVAILABLE
            Batch->>BookingSvc: cancel(bookingId)
            Note over BookingSvc: PENDING → CANCELLED
        end
    end
```

`tryConfirmIfPaid`는 `getResult()`(§6-3)와 완전히 같은 메서드 — package-private으로 풀어서 재사용한다
(다른 도메인이 `BookingOrchestrator`를 직접 호출 못 하게 `public`은 피함, AGENT.md §1). 배치도 Saga
오케스트레이터와 동일하게 `seat`는 `SeatFacade`를 거쳐서만 건드린다.
