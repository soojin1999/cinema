# Frontend 설계 문서

> **최종 수정**: 2026-07-18
> **원칙**: 사용자가 프론트에 약하므로 AI가 주도해서 작성.
> 동작 원리는 짧게 곁들여 설명한다.

---

## 1. 스택 선택 (2026-07-18 변경)

| 항목 | 선택 | 이유 |
|------|------|------|
| 렌더링 방식 | **정적 HTML + Vanilla JS (클라이언트 렌더링)** | 백엔드를 `@RestController` + JSON API로 통일하기로 하면서, 뷰도 서버가 아닌 클라이언트가 그리는 구조로 변경 |
| CSS | Vanilla CSS | 별도 라이브러리 없이 직접 작성 |
| JS | Vanilla JS (fetch만) | 별도 빌드 도구·프레임워크 없이 `fetch()`로 REST API를 호출해서 DOM을 직접 구성 |

> 원래는 Thymeleaf(SSR)로 시작했으나(별도 프론트 서버·빌드·CORS가 없다는 장점), 백엔드를 어차피 JSON API
> 서버로 만들기로 하면서 뷰 템플릿 엔진 자체가 불필요해졌다. `spring-boot-starter-thymeleaf` 의존성 제거함.
> React·Vue 같은 SPA 프레임워크는 여전히 도입하지 않는다 — 이 프로젝트 목표(Saga·경계 설계)와 무관하고,
> 화면이 3개뿐이라 프레임워크 없이 vanilla JS로 충분하기 때문.

---

## 2. 동작 원리 (짧게)

```
브라우저가 static/*.html 로드 (Spring Boot가 정적 리소스로 그대로 서빙)
    ↓
페이지의 <script>가 fetch()로 REST API(JSON) 호출
    ↓
받은 JSON을 JS가 DOM에 직접 렌더링 (textContent 위주로 조립, XSS 방지)
```

- 서버는 View를 만들지 않는다. 모든 컨트롤러가 `@RestController`로 JSON만 응답한다.
- 좌석 격자 polling도 전체 페이지 새로고침이 아니라, 좌석 상태 JSON만 다시 받아 좌석 칸만 갱신한다
  (입력 중인 사용자ID·락 방식이 날아가지 않음).
- 비즈니스 예외(`SeatNotAvailableException`, `SeatConflictException`)는 `GlobalExceptionHandler`
  (`common/exception/GlobalExceptionHandler.java`)가 409 CONFLICT + `{"message": "..."}` 형태로 변환해준다.

---

## 3. 화면·API 목록

| 화면 (정적 파일) | 호출하는 API | 역할 |
|------|-----|------|
| `static/index.html` (`GET /`) | `GET /schedules` | 상영 스케줄 목록. 각 행에서 좌석 선택 화면으로 이동 |
| `static/seats.html?scheduleId={id}` | `GET /schedules/{id}` (헤더), `GET /schedules/{id}/seats` (좌석 격자, polling 재사용) | 좌석 격자 표시(AVAILABLE/HELD/BOOKED 색상 구분) + 예매 폼. 제출 시 `POST /bookings` |
| `static/booking-result.html?bookingId={id}` | `GET /bookings/{id}` | 예매 결과(CONFIRMED/CANCELLED/PENDING) 표시 |

### REST API 요약 (컨트롤러 기준)

| 메서드/경로 | 컨트롤러 | 응답 |
|------|------|------|
| `GET /schedules` | `ScheduleController` (screening) | `List<ScheduleView>` |
| `GET /schedules/{scheduleId}` | `ScheduleController` | `ScheduleView` (없으면 404) |
| `GET /schedules/{scheduleId}/seats` | `ScheduleController` | `List<SeatGridItem>` (SeatFacade 경유) |
| `POST /bookings` | `BookingController` | 201 + `BookingCreated(bookingId)` |
| `GET /bookings/{bookingId}` | `BookingController` | `BookingResult(bookingId, status)` |

---

## 4. 좌석 격자 UI

```
[A관 — 인터스텔라 14:00]

     1     2     3     4     5
A  [  ]  [  ]  [XX]  [  ]  [  ]
B  [  ]  [HH]  [  ]  [  ]  [  ]
C  [  ]  [  ]  [  ]  [XX]  [  ]
D  [  ]  [  ]  [  ]  [  ]  [  ]
E  [  ]  [  ]  [  ]  [  ]  [  ]

범례: [  ] AVAILABLE  [HH] HELD  [XX] BOOKED
```

- 좌석(라디오 버튼) 선택 + 사용자ID + 락 방식(`lockType`: `PESSIMISTIC`/`OPTIMISTIC`) 입력 후 "예매하기" 클릭
  → JS가 `POST /bookings`를 JSON으로 호출 → 성공 시 `booking-result.html?bookingId={id}`로 이동, 실패(409) 시 에러 메시지 표시
- **동시성 시연용 polling**: `seats.html`이 4초마다 `GET /schedules/{id}/seats`를 다시 호출해서 좌석 칸의
  색상/비활성 상태만 갱신한다 (다른 브라우저 탭에서 좌석 상태가 바뀌는 걸 눈으로 확인하기 위한 용도)

---

## 5. 개발 진행 상태

| 항목 | 상태 |
|------|------|
| 상영 스케줄 목록 (`index.html`) | ✅ 완료 |
| 좌석 선택 화면 (`seats.html`, polling 포함) | ✅ 완료 |
| 예매 결과 화면 (`booking-result.html`) | ✅ 완료 |
| CSS (상태별 좌석 색상) | ✅ 완료 (`static/css/style.css`) |
| 공통 예외 → JSON 에러 응답 | ✅ 완료 (`GlobalExceptionHandler`) |
