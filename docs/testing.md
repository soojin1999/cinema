# Testing 현황

> **최종 수정**: 2026-07-21
> `T-10`(JUnit 테스트 작성, `Todo.md` 참고) 진행 중. 계획된 4단계: ① 기초 문법 → ② 순수 로직 테스트 →
> ③ Mockito로 의존성 모킹 → ④ 진짜 DB 동시성 통합테스트. 지금까지 ①~③이 `SeatService`를 대상으로 진행됨.

---

## 작성된 테스트

`src/test/java/com/toy/cinema/seat/SeatServiceTest.java` — `SeatMapper`를 Mockito로 대체해 `SeatService`의
동시성 제어 분기(`AGENT.md` §4)를 DB 없이 검증.

| 테스트 | 대상 메서드 | 시나리오 | 검증 방식 |
|---|---|---|---|
| `이미_HELD인_좌석에_holdPessimistic을_호출하면_예외가_터진다` | `holdPessimistic` | `findForUpdate`가 HELD 좌석 리턴 | `assertThrows(SeatNotAvailableException)` |
| `AVAILABLE_좌석에_holdPessimistic을_호출하면_updateStatus가_호출된다` | `holdPessimistic` | `findForUpdate`가 AVAILABLE 좌석 리턴 | `verify(mockMapper).updateStatus(...)` |
| `holdOptimistic_버전충돌시_SeatConflictException이_터진다` | `holdOptimistic` | 조회 시엔 AVAILABLE이나 `updateStatusWithVersion`이 영향행 0 리턴 (경합 패배 흉내) | `assertThrows(SeatConflictException)` |
| `holdOptimistic_충돌없으면_updateStatusWithVersion이_호출된다` | `holdOptimistic` | 조회 AVAILABLE + `updateStatusWithVersion`이 1 리턴 | `verify(mockMapper).updateStatusWithVersion(...)` |

**아직 테스트로 다루지 않는 것**: `updateStatusWithVersion`이 0을 리턴하는 상황은 실제 스레드 경합 없이 mock으로
강제한 것 — "대기형 락이 진짜로 다른 요청을 기다리게 하는가", "낙관적 락이 진짜 동시 요청에서 실제로 충돌을
내는가" 같은 진짜 동시성 동작 자체는 검증되지 않음. 계획된 4단계(진짜 DB + 여러 스레드)의 몫.

`SeatService`의 `confirm()`/`release()`/`getSeatGrid()`, `SeatFacadeImpl`, 그 외 모든 도메인(`booking`/`payment`/
`notification`/`screening`)은 아직 테스트 코드 없음 — 지금까지 전부 curl/브라우저 수동 검증으로만 확인됨.

---

## 다음에 할 것

- `SeatService`의 `confirm()`/`release()`/`getSeatGrid()` 테스트 추가
- 다른 도메인(`PaymentService` 등)으로 Mockito 테스트 범위 확장
- 3단계가 어느 정도 커버되면 4단계(진짜 DB 동시성 통합테스트) 착수 여부 논의 — `T-09`(DB 스키마 분리) 선행 필요
