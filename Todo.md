# Todo.md — 미결 논제 추적

> **AI 행동 규칙**:
> - 코드 작성 전 매번 이 파일을 확인한다.
> - 해당 논제의 구현 시점이 됐을 때 사용자에게 먼저 꺼내 논의한다.
> - 논의가 끝나 결정이 나면 해당 항목을 ~~취소선~~으로 표시하고 결정 내용을 기록한다.
> - 새로운 미결 논제가 생기면 즉시 이 파일에 추가한다.

---

## 🔴 착수 전 결정 필요 (코드 작성 시작 전 논의)

(현재 없음 — T-01, T-02는 결정 완료로 이동)

---

## 🟡 해당 구현 시점에 논의 (그때 가서 결정)

### T-04. HELD 타임아웃 처리
- **논제**: 결제 도중 브라우저 종료 등으로 좌석이 HELD에 영구 잔류하는 경우 어떻게 처리할 것인가
  - 후보 A: Spring `@Scheduled`로 주기적으로 만료된 HELD 행 스캔 → AVAILABLE 복원
  - 후보 B: `schedule_seat`에 `held_at` 컬럼 추가, SELECT 시 만료 여부 함께 체크
  - 후보 C: 현재 토이 범위에서 제외 (수동 개입)
- **결정 시점**: `BookingOrchestrator` 구현 완료 후, 예외 처리(`Exception` 버블업) 코드 작성 시
- **확정된 제약**: 후보 A(`@Scheduled`)로 결정되더라도 배치 코드는 예외 없이 `seatFacade.release()`를 통해서만 동작한다 (도메인 경계 규칙은 호출자가 Saga든 배치든 동일 적용)

---

## 🟢 나중에 (메인 구현 완료 후 추가 개발)

### T-05. 회원/사용자 도메인
- **현재**: `user_id`를 요청 파라미터로 수신 (인증 없음)
- **나중에**: 로그인·회원가입 로직 추가, `user` 도메인 독립 설계
- **착수 조건**: Saga + 동시성 구현 완료 후
- **연관**: `BookingFacade`가 이미 신설되어 있으므로, 회원 도메인이 booking 상태를 조회/트리거해야 할 때도 Facade를 통해 접근

### T-08. 관리자 CRUD (테이블별) — 극장 추가, 영화 추가, 상영 스케줄 추가, 좌석 배치 커스터마이징
- **현재**: `screening`(movie/theater/schedule) 전부 시드 데이터로 고정, 조회만 가능. `seat`도 이론상 상영관마다 행/열이 다를 수 있는데 지금은 25석(A~E × 1~5) 고정
- **나중에**: 극장 등록, 영화 등록, 스케줄 등록 CRUD + 상영관마다 좌석 배치(예: 3열×5행)를 다르게 구성할 수 있는 기능
- **착수 조건**: 학습 페이즈(경계 설계·Saga·테스트) 완료 후. README.md "최종 목표" 참고
- **미정**: `screening`을 그대로 확장할지, movie/theater/schedule을 각자 독립 도메인(Facade+Service)으로 쪼갤지는 착수 시점에 논의 — CRUD가 생기면 지금의 "Service 없는 예외"(상태 전이 없음을 근거로 함)가 더 이상 성립하지 않음

### T-10. JUnit 테스트 작성 (사용자 최초 학습 — 단계별 진행)
- **현재**: 4단계 착수. `SeatServiceConcurrencyTest`에 대기형(비관적) 락 동시성 테스트(스레드 2개) 작성 완료 (2026-07-21).
  충돌감지형(낙관적) 락 쪽은 아직 미작성. 상세 커버리지 → [docs/testing.md](docs/testing.md)
- **목표**: 지금까지 구현된 로직(특히 동시성 제어)을 자동화된 테스트로 관리
- **계획된 4단계** (한 번에 다 안 하고 순서대로, 매 단계 확인받으며 진행):
  1. JUnit 기초 문법 — `@Test`, assertion, `./gradlew test` 실행법 ✅ (2026-07-21)
  2. 순수 로직 테스트 — `SeatService`의 상태 검증(예외 던지는 경로) 위주 — `holdPessimistic`/`holdOptimistic` 완료, `confirm`/`release`/`getSeatGrid` 남음
  3. Mockito로 의존성 모킹 — `SeatMapper`를 가짜로 대체해서 `holdPessimistic`/`holdOptimistic` 성공/충돌 경로 테스트 ✅ (2026-07-21, 4개 테스트 완료)
  4. 동시성 통합 테스트 — 진짜 DB 위에서 여러 스레드가 동시에 같은 좌석을 잡을 때 대기형 vs 충돌감지형 락이 실제로 어떻게 다른지 검증 (이 프로젝트 핵심 학습 대상). **착수함** — 대기형 락(스레드 2개) 완료 (2026-07-21), 충돌감지형 락 쪽 남음
- **2026-07-21 부수 발견**: 대기형 락 동시성 테스트를 처음 돌렸을 때 스레드 2개가 다 성공해버리는(락이 전혀 안 걸리는) 현상 발견 →
  `PlatformTransactionManager` 빈이 하나도 없어서 `@Transactional`이 앱 전체(seat/payment/booking)에서 조용히 무시되고
  있던 버그였음(T-09가 `DataSource`를 4개로 쪼개면서 Spring Boot의 자동 트랜잭션 매니저 생성이 조건 불충족으로 빠짐).
  `config` 패키지 4개 클래스에 `PlatformTransactionManager` 빈 추가 + `SeatService`/`PaymentService`/`BookingService`의
  `@Transactional`에 `transactionManager` 명시로 수정. 순차 요청(curl/브라우저)으로는 절대 안 드러나고 진짜 동시성
  테스트로만 잡을 수 있었던 버그 — T-10 4단계가 왜 "이 프로젝트 핵심 학습 대상"인지 보여준 사례
- **착수 시점**: 바로 다음 세션부터 (2026-07-18 결정). 1~3단계는 `T-09`(DB 스키마 분리)와 무관하게 진행 가능 — 진짜 DB를 안 보기 때문. `T-09`는 2026-07-21 완료돼서 4단계(진짜 DB 동시성 통합테스트) 착수 조건은 이미 충족됨

### T-11. MySQL을 로컬 Docker가 아닌 실제 서버로 배포
- **현재**: MySQL이 로컬 PC의 Docker 컨테이너로만 떠 있음 (`docker compose up`). 앱도 `./gradlew bootRun`으로 로컬에서만 실행
- **나중에**: 실제 서버(클라우드 등)에 DB를 올려서 로컬 환경 의존 없이 접근 가능하게 배포
- **착수 조건**: 학습 페이즈(경계 설계·Saga·테스트) 완료 후. `T-08`(관리자 CRUD)과 함께 "실제로 써먹을 수 있는 앱"으로 격상시키는 단계에서 같이 검토
- **연관**: `.env` 기반 시크릿 분리(`DB_PASSWORD`)가 이미 되어 있어서, 실제 서버 접속 정보로 교체하는 것 자체는 `.env` 값만 바꾸면 됨. `T-09`(DB 스키마 분리)와 순서·구조를 함께 고려해야 함 — 스키마 분리를 실제 서버 이전 전에 할지 후에 할지는 별도 논의 필요

### T-12. CI/CD — git push 트리거 + nginx 무중단 배포
- **현재**: 배포 파이프라인 없음. 앱도 로컬 `./gradlew bootRun`으로만 실행, MySQL만 Docker 컨테이너
- **나중에**: GitHub Actions(또는 self-hosted runner)로 push 훅 → 이미지 빌드 → 배포 서버에서 새 컨테이너 기동 → 헬스체크 통과 시 nginx upstream 스위칭(blue/green)으로 무중단 배포
- **착수 조건**: 이 프로젝트에서 구현하려던 것(Saga/동시성/테스트 등 학습 페이즈 + T-05/T-08 등 나중 항목)을 다 끝낸 뒤, 마지막에 해볼 주제로 보류
- **선행 조건**: 앱 자체가 아직 도커라이즈 안 돼 있음(`docker-compose.yml`엔 MySQL만 있음) — 이것부터 해야 blue/green이 의미가 생김
- **연관**: `T-11`(MySQL 실서버 배포)과 같은 "실제로 써먹을 수 있는 앱" 격상 단계에서 함께 검토

---

## 완료된 논제

| ID | 논제 | 결정 내용 | 완료일 |
|----|------|---------|--------|
| - | Spring Boot 버전 | 4.0.7 유지 (안정 버전 확인) | 2026-07-15 |
| - | DB 테이블 구조 방향 | schedule_seat 복합 PK, 초기 범위 좁게 | 2026-07-15 |
| - | status 타입 | ENUM (DB 레벨 제약) | 2026-07-15 |
| - | 결제 구현 방식 | 내부 Mock (PaymentGateway 인터페이스) | 2026-07-15 |
| - | 보상 발동 조건 | PaymentFailedException만 보상, 나머지는 버블업 | 2026-07-15 |
| - | Saga 트랜잭션 전파 | REQUIRES_NEW (각 단계 독립 커밋 강제) | 2026-07-15 |
| - | 사용자 처리 방식 | 파라미터 수신, 인증 없음, 나중에 추가 | 2026-07-15 |
| T-01 | 패키지 내부 구조 | 각 도메인에 `mapper/domain/dto` 서브패키지. `dto/`는 Facade의 공개 계약, `domain/`은 절대 밖으로 노출 안 함 | 2026-07-17 |
| T-02 | Facade 메서드 시그니처 | Command/Info 등 dto 객체 사용 (원시값·domain 객체 직접 노출 금지). dto는 각 도메인의 `dto/` 패키지에 위치 | 2026-07-17 |
| T-03 | SeatService 동시성 제어 방식 | 대기형(비관적, `FOR UPDATE`)과 충돌감지형(낙관적, 버전 컬럼) 둘 다 구현해서 비교. `SeatService`에 `holdPessimistic`/`holdOptimistic`처럼 메서드를 분리. `schedule_seat`에 `version INT` 컬럼 추가 | 2026-07-17 |
| - | 충돌감지형 락 충돌 예외 분리 | `SeatNotAvailableException`(애초에 못 잡음)과 `SeatConflictException`(잡을 수 있었는데 타이밍에서 짐, 재시도 여지 있음)을 별도 타입으로 분리 | 2026-07-17 |
| - | Saga 알림(notification) 순서 | `seatFacade.confirm()` → `bookingService.confirm()` → `notificationFacade.send()` 순서로 통일. 알림 실패가 이미 확정된 예매 상태에 영향 주지 않도록 | 2026-07-17 |
| - | confirm 단계 실패 시 재조정 | payment SUCCESS인데 booking/seat 미확정으로 남으면 즉시 복구하지 않고, `GET /bookings/{id}` 조회 시점에 payment 상태 확인 후 그 자리에서 confirm 재시도 (지연 재조정) | 2026-07-17 |
| - | BookingFacade 신설 | `BookingController → BookingFacade → BookingOrchestrator` 구조로 다른 도메인 Facade들과 대칭 확보 | 2026-07-17 |
| - | 동시성 데모 방식 | 좌석 격자 화면에 시연용 polling(자동 갱신) 추가 — 다른 사용자의 좌석 상태 변경을 화면에서 눈으로 확인 | 2026-07-17 |
| - | 락 종류(lockType) 선택 위치 | 요청 파라미터로 외부 노출(`?lockType=PESSIMISTIC`). 분기는 `BookingOrchestrator`가 함 (`SeatFacade`/`SeatHoldCommand`는 변경 없음) | 2026-07-17 |
| - | booking_id 채번 회수 방식 | `InsertBookingParams`는 record 유지. `useGeneratedKeys` 대신 `SELECT LAST_INSERT_ID()`를 같은 트랜잭션 안에서 별도 호출 | 2026-07-17 |
| T-07 | `NotificationFailedException` 처리 | `BookingOrchestrator.reserve()`가 notification 단계만 넓게 catch해서 로그만 남기고 삼킴 (booking은 이미 CONFIRMED 커밋된 뒤라 되돌릴 게 없음). `GET /bookings/{id}`가 항상 실제 상태를 보여주므로 별도 안내 메시지 불필요 | 2026-07-17 |
| - | `release()` 명칭 | 그대로 유지 (`rollback`으로 바꾸지 않음). 이유: hold()가 이미 커밋된 뒤라 진짜 DB rollback이 아니라 별도의 보상 트랜잭션이므로, `rollback`이라는 이름은 Saga 핵심 개념(보상 ≠ 롤백)을 오도할 수 있음 | 2026-07-17 |
| - | 지연 재조정 구현 범위 | 지금 바로 완전히 구현. `PaymentFacade.findStatusByBookingId()` 신설, `BookingOrchestrator.getResult()`에서 PENDING+SUCCESS 조합 시 confirm 재시도 | 2026-07-17 |
| - | 화면 진입 흐름 | 상영관(theater) 선택 단계 없이, schedule(=영화) 선택 → 좌석 선택 2단계로 단순화. movie/theater는 schedule 키로 조인해서 헤더 정보만 보여줌. `screening` 패키지 신설(최초엔 `catalog`라는 이름으로, Facade 없는 조회 전용으로 시작 — 바로 아래 "조회 경계" 항목에서 Facade 추가로 정정, 이후 `screening`으로 리네이밍) | 2026-07-18 |
| - | 프론트엔드 아키텍처 | Thymeleaf(SSR) → `@RestController` + JSON API + 정적 HTML/JS(fetch)로 전면 전환. 백엔드를 REST API 서버로 통일하기로 하면서 뷰 템플릿 엔진 자체가 불필요해짐. 상세 → docs/frontend.md | 2026-07-18 |
| - | screening 조회 경계 | `ScheduleController`가 `ScreeningMapper`를 직접 호출하던 예외를 없애고 `ScreeningFacade`(+Impl) 신설 — Service는 상태 전이 로직이 없어 생략, 경계 일관성만 확보 | 2026-07-18 |
| - | catalog → screening 리네이밍 | 패키지명이 안에 뭐가 들었는지 한눈에 안 들어온다는 지적 — movie/theater/schedule을 아우르는 "상영" 개념으로 `screening`으로 통일 (Java 패키지·클래스·XML·계획 중인 DB 스키마명까지 전부) | 2026-07-18 |
| - | dto record vs Lombok | "SELECT 결과가 가공·참조 없이 그대로 클라이언트 응답으로 리턴되는" 순수 조회 응답 dto(`ScheduleView`, `SeatGridItem`)는 record 대신 Lombok `@Data`로 작성 — MyBatis `<constructor>` 매핑 생략 가능. command/domain dto는 계속 record. 상세 기준 → AGENT.md §1 | 2026-07-18 |
| - | 최종 목표 범위 확정 | 학습 페이즈(경계·Saga·테스트) 이후 최종 목표는 실제 운영 가능한 영화관 예매 앱 — 극장/영화/스케줄 등록, 좌석 배치 커스터마이징. 베이스는 여전히 MSA 구조 학습. 상세 → README.md "최종 목표" | 2026-07-18 |
| - | DB 스키마 분리 방향 | "MSA 구조를 제대로 공부하려면 데이터 레벨 경계도 지금 겪어보는 게 낫다"고 판단 — 도메인별 스키마 분리로 방향 확정 (물리 서버 분리는 아님, 범위 밖 유지). 지금 규모가 작을 때 하는 게 관리자 CRUD까지 붙은 뒤보다 훨씬 저렴하다는 게 근거. 착수 시점은 T-09에서 별도 논의 | 2026-07-18 |
| T-09 | DB 스키마 분리 실행 | `screening_db`/`seat_db`/`booking_db`/`payment_db` 4개 스키마로 분리, 도메인 간 FK 4개(`seat.theater_id`, `schedule_seat.schedule_id`, `booking→schedule_seat`, `payment.booking_id`) 제거하고 애플리케이션(Saga 호출 순서) 레벨 정합성으로 대체. `config` 패키지에 도메인별 `DataSource`+`SqlSessionFactory`+`@MapperScan(sqlSessionFactoryRef=...)` 4벌 구성, `CinemaApplication`의 전역 `@MapperScan` 제거. FK 제거로 생기는 위험은 T-08(관리자 CRUD) 착수 시점에 애플리케이션 레벨 검증 추가하기로 결정(지금은 시드 데이터만 채워지고 런타임 위험 없음). 실행 후 `docker compose down -v`로 볼륨 재초기화 + 전체 Saga 흐름(hold→pay→confirm, 3개 스키마 관통) 실제 HTTP 검증 완료 | 2026-07-21 |
| T-06 | 낙관적 락 충돌(`SeatConflictException`) 재시도 로직 | 후보 B(재시도 없이 그대로 예외 던짐)로 확정, 재시도 로직 추가 안 함. 이유: 좌석 hold는 배타적 자원이라 `updateStatusWithVersion` 영향 행 0(=충돌)은 기술적 노이즈가 아니라 "이미 다른 사람이 가져간" 진짜 비즈니스 결과 — 재시도해도 재조회 시 이미 `HELD`/`BOOKED`라 결국 `SeatNotAvailableException`으로 귀결되므로 DB 왕복만 늘어남 | 2026-07-24 |
