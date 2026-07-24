# Backend 다이어그램

> **최종 수정**: 2026-07-24
> 클래스 구조·패키지 의존 관계를 한눈에 파악하기 위한 시각화 문서.
> 각 클래스가 "왜" 그 자리에 있는지 같은 설계 이유는 [backend.md](backend.md) 참고 — 이 문서는 그림만 담는다.
> "무엇을 부르는가"(구조)는 여기, "언제/어떤 순서로 호출되는가"(흐름)는 [../flow.md](../flow.md)의 시퀀스 다이어그램 참고.

---

## 1. 클래스 다이어그램

> Facade(인터페이스+Impl) 경계와 각 클래스의 실제 메서드 시그니처까지 보여준다. `dto`/`mapper`/`domain`
> 세부 클래스는 [backend.md §2](backend.md#2-패키지-구조)의 패키지 트리 참고.
> **`XxxFacadeImpl`은 일부러 메서드를 안 적었다** — 전부 같은 이름의 메서드를 받아서 내부 Service에 그대로
> 위임만 하는 얇은(thin) 클래스라, 인터페이스(`XxxFacade`)에 적힌 시그니처와 100% 동일해서 중복 표기임.
> 하나로 합치면 가로로 너무 넓어져서(줌해도 안 보임) [flow.md](../flow.md)처럼 **도메인별로 쪼개서 세로로 쌓았다** —
> 도메인 간 연결은 맨 아래 "도메인 간 의존 관계(개요)" 하나로 따로 모음.

### booking 도메인

```mermaid
classDiagram
    direction TB
    class BookingController {
        +reserve(ReservationRequest) ResponseEntity~BookingCreated~
        +result(Long bookingId) BookingResult
    }
    class BookingFacade {
        <<interface>>
        +reserve(ReservationRequest) Long
        +getResult(Long bookingId) BookingResult
    }
    class BookingFacadeImpl
    class BookingOrchestrator {
        +reserve(ReservationRequest) Long
        +getResult(Long bookingId) BookingResult
        -tryConfirmIfPaid(Long bookingId, Long scheduleId, Long seatId) boolean
    }
    class BookingService {
        +insertPending(InsertBookingParams) Long
        +confirm(Long bookingId)
        +cancel(Long bookingId)
        +findById(Long bookingId) Booking
    }
    class LockType {
        <<enumeration>>
        PESSIMISTIC
        OPTIMISTIC
    }

    BookingController --> BookingFacade
    BookingFacadeImpl ..|> BookingFacade
    BookingFacadeImpl --> BookingOrchestrator
    BookingOrchestrator --> BookingService
    BookingOrchestrator ..> LockType : lockType 분기
```

### seat 도메인

```mermaid
classDiagram
    direction TB
    class SeatFacade {
        <<interface>>
        +holdPessimistic(SeatHoldCommand)
        +holdOptimistic(SeatHoldCommand)
        +confirm(SeatConfirmCommand)
        +release(SeatReleaseCommand)
        +getSeatGrid(Long scheduleId) List~SeatGridItem~
    }
    class SeatFacadeImpl
    class SeatService {
        +holdPessimistic(SeatHoldCommand cmd)
        +holdOptimistic(SeatHoldCommand cmd)
        +confirm(SeatConfirmCommand cmd)
        +release(SeatReleaseCommand cmd)
        +getSeatGrid(Long scheduleId) List~SeatGridItem~
    }

    SeatFacadeImpl ..|> SeatFacade
    SeatFacadeImpl --> SeatService
```

### screening 도메인

```mermaid
classDiagram
    direction TB
    class ScheduleController {
        +list() List~ScheduleView~
        +get(Long scheduleId) ResponseEntity~ScheduleView~
        +seats(Long scheduleId) List~SeatGridItem~
    }
    class ScreeningFacade {
        <<interface>>
        +findAllSchedules() List~ScheduleView~
        +findScheduleById(Long scheduleId) ScheduleView
    }
    class ScreeningFacadeImpl

    ScheduleController --> ScreeningFacade
    ScreeningFacadeImpl ..|> ScreeningFacade
```

### payment 도메인

```mermaid
classDiagram
    direction TB
    class PaymentFacade {
        <<interface>>
        +pay(PaymentRequest)
        +findStatusByBookingId(Long bookingId) PaymentStatus
    }
    class PaymentFacadeImpl
    class PaymentService {
        +insertPending(PaymentRequest) boolean
        +markSuccess(String paymentKey)
        +markFailed(String paymentKey)
        +findByBookingId(Long bookingId) Payment
        -handleDuplicatePaymentKey(String paymentKey) boolean
    }
    class PaymentGateway {
        <<interface>>
        +pay(PgChargeRequest) PgChargeResult
    }
    class MockPaymentGateway {
        +pay(PgChargeRequest) PgChargeResult
    }

    PaymentFacadeImpl ..|> PaymentFacade
    PaymentFacadeImpl --> PaymentService
    PaymentFacadeImpl --> PaymentGateway
    MockPaymentGateway ..|> PaymentGateway
```

### notification 도메인

```mermaid
classDiagram
    direction TB
    class NotificationFacade {
        <<interface>>
        +send(NotificationRequest)
    }
    class NotificationFacadeImpl

    NotificationFacadeImpl ..|> NotificationFacade
```

### 도메인 간 의존 관계 (개요)

> 위 5개 다이어그램에서 뺀 **cross-domain 화살표만** 모았다 — `BookingOrchestrator`/`ScheduleController`가
> 다른 도메인의 `Facade` 인터페이스만 아는 모습(AGENT.md §1 "경계는 Facade만")이 여기서 드러난다.

```mermaid
classDiagram
    direction TB
    class BookingOrchestrator
    class ScheduleController
    class SeatFacade { <<interface>> }
    class PaymentFacade { <<interface>> }
    class NotificationFacade { <<interface>> }
    class ScreeningFacade { <<interface>> }

    BookingOrchestrator --> SeatFacade : hold*/confirm/release
    BookingOrchestrator --> PaymentFacade : pay/findStatusByBookingId
    BookingOrchestrator --> NotificationFacade : send
    ScheduleController --> ScreeningFacade
    ScheduleController --> SeatFacade : getSeatGrid
```

한눈에 보이는 것: (1) 모든 도메인이 `Facade`(인터페이스) + `Impl` 쌍으로 돼 있고 다른 도메인은 인터페이스만 의존 —
`BookingOrchestrator`가 `SeatFacadeImpl`이 아니라 `SeatFacade`만 아는 게 AGENT.md §1 "경계는 Facade만" 규칙이 코드로
드러난 모습. (2) `PaymentFacadeImpl`도 `PaymentGateway` 인터페이스만 의존하고 `MockPaymentGateway`는 그 구현체 중
하나일 뿐 — 나중에 실 PG로 교체해도 `PaymentFacadeImpl` 코드는 안 바뀜. (3) `-`로 표시된 `private` 메서드
(`BookingOrchestrator.tryConfirmIfPaid`, `PaymentService.handleDuplicatePaymentKey`)는 같은 클래스 안에서만
쓰는 내부 헬퍼라는 뜻 — 다른 클래스가 호출할 수 없다.

---

## 2. 패키지 다이어그램

> 클래스 다이어그램(§1)이 "클래스 하나하나가 뭘 하는가"라면, 이건 한 단계 위 — **패키지 자체를 하나의 박스로
> 취급**해서 어느 패키지가 어느 패키지를 아는지만 본다. 박스 안에 중첩된 박스(`payment` 안의 `gateway`)는
> "포함" 관계, 점선 화살표는 "의존"(import) 관계 — 표준 UML 패키지 다이어그램 표기법.

```mermaid
graph TD
    booking["booking"]
    seat["seat"]
    screening["screening"]
    notification["notification"]
    common["common"]
    config["config"]

    subgraph payment_pkg["payment"]
        direction TB
        gateway["gateway"]
    end

    booking -.->|Facade만 의존| seat
    booking -.->|Facade만 의존| payment_pkg
    booking -.->|Facade만 의존| notification
    screening -.->|Facade만 의존| seat

    booking -.-> common
    seat -.-> common
    payment_pkg -.-> common
    screening -.-> common
    notification -.-> common

    booking -.-> config
    seat -.-> config
    payment_pkg -.-> config
    screening -.-> config
```

한눈에 보이는 것: (1) 화살표가 전부 도메인 패키지 → `Facade`가 있는 패키지 방향으로만 나간다 — 역방향(예:
`seat` → `booking`)이 하나도 없다는 게 AGENT.md §1 "도메인 간 소통은 Facade만" 규칙이 패키지 레벨에서도
지켜지고 있다는 뜻. (2) `payment.gateway`는 `payment` 안에 중첩된 박스로 그렸다 — 독립 도메인이 아니라
"record+프로세스가 같은 관심사"라 묶은 서브패키지이기 때문(backend.md §2). (3) `common`/`config`로 가는
화살표는 전부 방향이 한쪽(도메인 → common/config)이다 — `common`/`config`는 어떤 도메인도 모른다(순환 의존 없음).
