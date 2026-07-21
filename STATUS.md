# STATUS.md — 현재 개발 상태 및 세션 기록

> 코드 작성 전 반드시 [Todo.md](Todo.md)를 확인한다.
> 해당 구현 시점이 된 논제가 있으면 코드 작성 전에 먼저 사용자에게 꺼내 논의한다.
> 개발 규칙(불변)은 [AGENT.md](AGENT.md) 참고 — 이 파일은 "지금 어디까지 됐는가"만 다룬다.

---

## 🎯 다음 세션 시작 지점

**`T-10` JUnit 학습 1~3단계 진행 중.** 2026-07-21 세션에서 JUnit 기초 개념(`@Test`/assertion/`@BeforeEach`/mock/`verify`)을
개념 단위로 학습하고, `SeatServiceTest`(`src/test/java/com/toy/cinema/seat/SeatServiceTest.java`)에
`SeatService.holdPessimistic()`/`holdOptimistic()` 성공·실패 경로 4개를 Mockito로 작성 완료:
1. `holdPessimistic` — HELD 좌석 → `SeatNotAvailableException`
2. `holdPessimistic` — AVAILABLE 좌석 → `updateStatus` 호출 확인 (`verify`)
3. `holdOptimistic` — 버전 충돌(`updateStatusWithVersion` 0 리턴) → `SeatConflictException`
4. `holdOptimistic` — 충돌 없음 → `updateStatusWithVersion` 호출 확인 (`verify`)

다음 세션 우선순위:

1. **`T-10` 계속** — `SeatService`의 `confirm()`/`release()`/`getSeatGrid()`, 또는 다른 도메인(`PaymentService` 등)으로 Mockito 테스트 범위 확장. 3단계(Mockito)까지 어느 정도 커버되면 4단계(진짜 DB 동시성 통합테스트)로 넘어갈지 논의
2. **`T-09` — DB 스키마 분리 실행** (도메인별 스키마, 방향 확정됨). JUnit 1~3단계와는 무관하게 진행 가능하지만, JUnit 4단계(동시성 통합테스트) 착수 전까지는 끝내야 함 — `Todo.md` T-09 참고
3. `T-04` — HELD 타임아웃 배치 (그 다음 순위)
4. `T-06` — `SeatConflictException`(낙관적 락 충돌) 재시도 로직 (그 다음 순위)

## 완료된 것

```
✅ build.gradle           의존성 완료 (Thymeleaf 제거, webmvc+mybatis만 유지. UTF-8 인코딩 옵션 포함)
✅ application.yaml       DB 접속 설정 완료 (localhost:3306, Docker MySQL, 비밀번호 password로 통일)
✅ sql/01-schema.sql      테이블 DDL (schedule_seat.version 컬럼 포함). 파일명 번호로 data.sql보다 먼저 실행되도록 강제
✅ sql/02-data.sql        시드 데이터 (영화1, A관, 25석, 스케줄1)
✅ docs/                  설계 문서 완료
✅ common/enums           SeatStatus, BookingStatus, PaymentStatus
✅ common/exception       CinemaException, SeatNotAvailableException, SeatConflictException, PaymentFailedException,
                          GlobalExceptionHandler(+ErrorResponse) — 비즈니스 예외를 409/400 JSON으로 변환
✅ seat/dto               SeatHoldCommand, SeatConfirmCommand, SeatReleaseCommand, SeatGridItem(좌석 격자 조회용)
✅ seat/domain            ScheduleSeat
✅ seat/mapper            SeatMapper (+ XML), ScheduleSeatKey, UpdateStatusParams, UpdateVersionParams
✅ seat/SeatService        holdPessimistic, holdOptimistic, confirm, release, getSeatGrid
✅ seat/SeatFacade         인터페이스 + SeatFacadeImpl (seat 도메인 완성)
✅ screening 패키지        ScheduleController(@RestController) + ScreeningFacade+Impl + ScreeningMapper(+XML) + dto/ScheduleView — 스케줄 목록/조회 전용 (2026-07-18: catalog → screening 리네이밍)
✅ payment 도메인          domain/mapper/gateway(PaymentGateway+MockPaymentGateway)/Service/Facade+Impl 완성
✅ notification 도메인      NotificationFacade+Impl (로그만 출력), dto/NotificationRequest 완성
✅ booking 도메인          domain/mapper/Service/BookingOrchestrator(Saga+지연재조정)/Facade+Impl/Controller(@RestController) 완성
✅ 전체 Saga 흐름           POST /bookings → BookingOrchestrator.reserve() → seat/payment/notification 순회
✅ 실제 HTTP 테스트 완료     성공/보상/지연재조정/409충돌 경로 검증됨 (2026-07-17~18)
✅ 프론트(정적 HTML+JS)     static/index.html, seats.html(polling 포함), booking-result.html — REST API를 fetch로 호출.
                          브라우저로 목록→좌석선택→예매→결과 전체 플로우 실제 검증 완료 (2026-07-18)
✅ p6spy SQL 로깅          common/logging/SqlLogFormat + spy.properties — 파라미터가 치환된 완성 SQL을 콘솔에 출력 (2026-07-18)
✅ git 저장소 + .env 시크릿 분리   .gitignore에 .claude/, .env 추가. DB_PASSWORD를 .env(git 제외) 하나로 통일 —
                          docker-compose.yml/application.yaml 둘 다 이 값을 봄. .env.example은 커밋됨 (2026-07-18)
✅ T-10 1~3단계 착수         SeatServiceTest — holdPessimistic/holdOptimistic 성공·실패 경로 4개 (Mockito). 나머지 메서드·도메인은
                          아직 미작성. 커버리지 상세 → docs/testing.md (2026-07-21)

⬜ T-04 HELD 타임아웃 배치
⬜ T-06 SeatConflictException 재시도 로직
⬜ (사소, 우선순위 낮음) 로그 파일에 찍히는 한글 예외 메시지가 콘솔 출력 경로에서 일부 깨짐 — DB 저장값/HTTP JSON 응답엔 영향 없음, 순수 콘솔 표시 문제로 추정. 다시 볼 때 아래 "오늘 겪은 인프라 문제" 참고
```

## 오늘(2026-07-18) 아키텍처 변경 — Thymeleaf → REST API + 정적 HTML/JS

Thymeleaf SSR로 좌석 화면을 먼저 구현했다가, 프로젝트를 처음부터 `@RestController` + JSON 통신으로
통일하기로 방향을 바꿔서 전면 전환했다. 상세 이유·API 목록은 [docs/frontend.md](docs/frontend.md) 참고.
`spring-boot-starter-thymeleaf` 의존성 제거, `templates/` 삭제, 컨트롤러 2개(`BookingController`,
`ScheduleController`) 전부 `@RestController`로 전환, `GlobalExceptionHandler` 신설.

이어서 두 가지를 더 정리했다:
- `ScheduleController`가 `ScreeningMapper`(당시 이름 `CatalogMapper`)를 직접 호출하던 예외를 없애고 `ScreeningFacade`(+Impl) 신설 —
  다른 도메인과 동일하게 Facade를 거치도록 통일 (Service는 로직이 없어 생략)
- "SELECT 결과가 가공·참조 없이 그대로 클라이언트로 리턴되는" 순수 조회 응답 dto(`ScheduleView`,
  `SeatGridItem`)는 record 대신 Lombok `@Data`로 전환 — MyBatis `<constructor>` 매핑 생략. 판단 기준은
  AGENT.md §1 참고

## 오늘(2026-07-18) p6spy SQL 로그 파라미터 미치환 문제와 해결

**증상**: 개발 중 SQL을 보기 편하게 하려고 `p6spy`(`common/logging/SqlLogFormat.java` + `spy.properties`)를
도입했는데, 콘솔에 `WHERE schedule_id = ?`처럼 파라미터 자리가 실제 값으로 안 바뀌고 그대로 찍힘.

**원인**: p6spy(3.9.1) 내장 `CustomLineFormat`의 `%(effectiveSql)` 토큰(멀티라인 버전)이 **멀티라인 SQL에서
파라미터 치환에 실패하는 버그**가 있었음. `%(effectiveSqlSingleLine)`(싱글라인 버전)은 정상 동작 확인됨 —
즉 치환 로직 자체는 살아있는데, 멀티라인 텍스트를 다룰 때만 깨짐.

**진단 방법**: `MessageFormattingStrategy.formatMessage(...)`를 직접 구현해서 `prepared`(원본, `?`)와
`sql`(p6spy가 내부적으로 들고 있는 치환된 SQL) 두 파라미터를 그대로 로그에 찍어 비교 — `sql` 파라미터가
이미 정상적으로 치환된 값(그리고 MyBatis 매퍼 XML의 줄바꿈도 그대로 유지됨)을 담고 있는 걸 확인함.

**해결**: p6spy의 `%(effectiveSql)` 토큰 문자열 치환에 의존하지 않고, `formatMessage()`가 직접 받는
`sql` 파라미터를 그대로 써서 로그 문자열을 조립하는 `SqlLogFormat` 클래스를 만들어 우회 (`spy.properties`의
`logMessageFormat=com.toy.cinema.common.logging.SqlLogFormat`).

## 오늘(2026-07-17) 겪은 인프라 문제와 해결 — 재발 방지용 기록

새 세션에서 다시 만들거나 건드릴 필요 없음. 문제 생기면 여기부터 확인.

| 문제 | 원인 | 해결 |
|------|------|------|
| `data.sql`이 테이블 생성 전에 실행돼서 실패 | `docker-entrypoint-initdb.d`는 파일명 알파벳순 실행, `data.sql` < `schema.sql` | `01-schema.sql`/`02-data.sql`로 파일명 번호 부여 |
| 한글이 DB에 이중 인코딩(mojibake)으로 저장됨 | `mysql` 클라이언트가 서버 charset(utf8mb4)과 무관하게 자체 기본값 `latin1`로 접속 | 상세 원인·시도·최종 해결 과정 → [README.md 트러블슈팅](README.md#트러블슈팅-한글-데이터-깨짐-이중-인코딩). 요약: `docker/mysql/charset.cnf`를 `Dockerfile`로 이미지에 구워넣는 방식 유지 — bind mount는 Windows에서 world-writable로 보여 MySQL이 무시함 |
| `docker-compose.yml`/`application.yaml` 비밀번호 불일치 | 두 파일은 서로 자동 동기화 안 됨, 수동으로 맞춰야 함 | (2026-07-15) 둘 다 기본값 `password`로 통일. → **(2026-07-18) 근본 해결**: `.env` 파일(git 제외) 하나에 `DB_PASSWORD`만 두고 두 파일 다 그 값을 보게 통일 — 더 이상 수동 동기화 불필요, 상세 → README.md "비밀번호 확인" |
| `BookingFacade.reserve()` 호출 시 `Invalid bound statement` 에러 | `@MapperScan(basePackages = "com.toy.cinema")`가 매퍼 아닌 일반 인터페이스까지 매퍼로 착각 | `@MapperScan(..., annotationClass = Mapper.class)` + 모든 매퍼 인터페이스에 `@Mapper` 명시 (새 매퍼 추가 시 `@Mapper` 잊지 말 것 — AGENT.md §6 코딩 컨벤션에도 기록) |
| Java 소스의 한글 문자열 리터럴이 컴파일 시점에 깨짐 | Gradle이 OS 기본 인코딩(한국어 Windows: CP949)으로 `.java` 파일을 읽음 | `build.gradle`에 `tasks.withType(JavaCompile) { options.encoding = 'UTF-8' }` 추가 |
| 컴파일은 맞아도 콘솔/로그 출력에서 여전히 한글 깨짐 (부분적으로 남아있음) | JVM 런타임 출력 인코딩이 Java 17에선 OS 기본값을 따름 (Java 18의 JEP 400 UTF-8 기본값 미적용) | `build.gradle`의 `bootRun { jvmArgs = ['-Dfile.encoding=UTF-8', ...] }`로 완화 시도했으나 완전히 해결되진 않음 (위 "사소, 우선순위 낮음" 항목 참고) |

## 로컬 DB 환경

| 항목 | 값 |
|------|---|
| 호스트 | localhost |
| 포트 | 3306 |
| 구동 방식 | Docker 컨테이너 (`docker compose up -d --build` — Dockerfile 빌드 방식이라 `--build` 필요) |
| 비밀번호 | `password` (docker-compose.yml/application.yaml 둘 다 통일됨) |
| DB명 | cinema |
| 접속 URL | `jdbc:mysql://localhost:3306/cinema?serverTimezone=Asia/Seoul&characterEncoding=UTF-8` |
