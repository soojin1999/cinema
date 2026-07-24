# Testing 현황

> **최종 수정**: 2026-07-24
> `T-10`(JUnit 테스트 작성, `Todo.md` 참고) 진행 중. 계획된 4단계: ① 기초 문법 → ② 순수 로직 테스트 →
> ③ Mockito로 의존성 모킹 → ④ 진짜 DB 동시성 통합테스트. ①~③은 `SeatService` 대상으로 완료, ④는 착수함.

---

## 작성된 테스트

### `SeatServiceTest` — Mockito 유닛 테스트 (3단계)

`src/test/java/com/toy/cinema/seat/SeatServiceTest.java` — `SeatMapper`를 Mockito로 대체해 `SeatService`의
동시성 제어 분기(`AGENT.md` §4)를 DB 없이 검증.

| 테스트 | 대상 메서드 | 시나리오 | 검증 방식 |
|---|---|---|---|
| `이미_HELD인_좌석에_holdPessimistic을_호출하면_예외가_터진다` | `holdPessimistic` | `findForUpdate`가 HELD 좌석 리턴 | `assertThrows(SeatNotAvailableException)` |
| `AVAILABLE_좌석에_holdPessimistic을_호출하면_updateStatus가_호출된다` | `holdPessimistic` | `findForUpdate`가 AVAILABLE 좌석 리턴 | `verify(mockMapper).updateStatus(...)` |
| `holdOptimistic_버전충돌시_SeatConflictException이_터진다` | `holdOptimistic` | 조회 시엔 AVAILABLE이나 `updateStatusWithVersion`이 영향행 0 리턴 (경합 패배 흉내) | `assertThrows(SeatConflictException)` |
| `holdOptimistic_충돌없으면_updateStatusWithVersion이_호출된다` | `holdOptimistic` | 조회 AVAILABLE + `updateStatusWithVersion`이 1 리턴 | `verify(mockMapper).updateStatusWithVersion(...)` |

### `SeatServiceConcurrencyTest` — 진짜 DB 통합테스트 (4단계, 2026-07-21 착수)

`src/test/java/com/toy/cinema/seat/SeatServiceConcurrencyTest.java` — mock 없이 `@SpringBootTest`로 전체
컨테이너를 띄워, 실제 `seat_db`에 스레드 2개가 동시에 요청을 보낸다. `ExecutorService`+`CountDownLatch`로
두 스레드를 동시에 출발시키고, `AtomicInteger`로 결과를 집계.

| 테스트 | 시나리오 | 검증 방식 |
|---|---|---|
| `대기형_락에_동시_2명이_요청하면_1명만_성공한다` | 같은 좌석에 `holdPessimistic` 동시 2건 | 정확히 1명 성공(`successCount`) + 1명 `SeatNotAvailableException`(`conflictCount`) |

**부수 발견(2026-07-21)**: 이 테스트를 처음 돌렸을 때 2명 다 성공해버렸다 — `T-09`(DB 스키마 분리)로 `DataSource`가
4개로 늘면서 `PlatformTransactionManager` 빈이 하나도 자동 생성되지 않아 `@Transactional`이 전부 무시되고 있던
버그였다. `config` 패키지에 트랜잭션 매니저 빈 추가 + `@Transactional(transactionManager = "...")` 명시로 수정.
순차 요청으로는 절대 드러나지 않고 진짜 동시성 테스트로만 잡을 수 있었던 버그 — 상세 → `Todo.md` T-10.

**아직 테스트로 다루지 않는 것**:
- 충돌감지형(낙관적) 락 쪽 동시성 테스트 — "기다리지 않고 즉시 충돌 예외"라는 반대 실패 모드는 아직 미검증
- `SeatService`의 `confirm()`/`release()`/`getSeatGrid()`, `SeatFacadeImpl`, 그 외 모든 도메인(`booking`/`payment`/
  `notification`/`screening`)은 아직 테스트 코드 없음 — 지금까지 전부 curl/브라우저 수동 검증으로만 확인됨.
- `BookingTimeoutBatch`(T-04, 2026-07-24 구현)는 **테스트 코드는 물론 curl/브라우저 수동 검증조차 아직 안 함** —
  다른 도메인보다 한 단계 더 미검증 상태. `POST /admin/batch/reconcile-pending-bookings`로 먼저 수동 검증부터
  필요. 상세 → [backend/backend.md §4-4](backend/backend.md#4-4-held-타임아웃-회수-배치-t-04-2026-07-24).

---

## 다음에 할 것

- 충돌감지형(낙관적) 락 동시성 테스트 추가 (같은 패턴, 스레드 2개)
- `SeatService`의 `confirm()`/`release()`/`getSeatGrid()` 테스트 추가
- 다른 도메인(`PaymentService` 등)으로 Mockito 테스트 범위 확장
- `BookingTimeoutBatch.reconcilePendingBookings()` 검증 — 먼저 curl로 수동 확인, 이후 Mockito(`BookingService`/
  `BookingOrchestrator`/`SeatFacade` 모킹)로 "SUCCESS면 confirm 구제" / "아니면 release+cancel" 두 분기 테스트 추가
