# STATUS.md — 현재 개발 상태 및 세션 기록

> 코드 작성 전 반드시 [Todo.md](Todo.md)를 확인한다.
> 해당 구현 시점이 된 논제가 있으면 코드 작성 전에 먼저 사용자에게 꺼내 논의한다.
> 개발 규칙(불변)은 [AGENT.md](AGENT.md) 참고 — 이 파일은 "지금 어디까지 됐는가"만 다룬다.

---

## 🎯 다음 세션 시작 지점

**`T-11` 완료 (2026-07-26)**: AWS EC2(프리티어)에 MySQL+앱 실제 배포, 브라우저로 인터넷 통해 접속 확인까지 끝났다.
원래 정해둔 순서(`T-10` 마무리 → `T-11` → `T-12`)를 사용자 판단으로 뒤집어서 `T-10`이 안 끝난 채로 `T-11`을
먼저 진행함 — 상세 → 아래 "오늘(2026-07-26) T-11" 절.

**다음 순서 확정 (2026-07-26)**: `T-12`(CI/CD) → `T-10`/`T-04` 테스트 마무리 → `T-08`(관리자 CRUD). CI/CD를
`T-08`보다 먼저 하는 건 기존 결정 그대로 유지하되, `T-10`(낙관적 락 동시성 테스트 등, 이 프로젝트 핵심 학습
목표) / `T-04`(`BookingTimeoutBatch` JUnit)는 CRUD 전에 마무리하기로 함 — CRUD가 새 복잡도(FK 없는 상태에서의
애플리케이션 레벨 검증, `T-09` 결정 기록 참고)를 얹기 전에 테스트 기반을 먼저 다지는 게 낫다고 판단.

**`T-10` 4단계(진짜 DB 동시성 통합테스트) 착수.** 2026-07-21 세션에서 `SeatServiceConcurrencyTest`에
대기형(비관적) 락 동시성 테스트(스레드 2개, `ExecutorService`+`CountDownLatch`)를 작성하고 실제 `seat_db`로 검증했다.

**과정에서 실제 버그를 발견·수정**: 처음 테스트를 돌렸을 때 스레드 2개가 다 성공해버림 → `T-09`가 `DataSource`를
4개로 쪼개면서 `PlatformTransactionManager` 빈이 하나도 자동 생성되지 않아 `@Transactional`이 앱 전체(seat/payment/
booking)에서 조용히 무시되고 있었음. `config` 패키지 4개 클래스에 `PlatformTransactionManager` 빈 추가 +
`SeatService`/`PaymentService`/`BookingService`의 `@Transactional`에 `transactionManager` 명시로 수정, 재검증 완료.
상세 → `Todo.md` T-10, `docs/testing.md`.

`T-09`(DB 스키마 분리)는 지난 세션에 완료 — `screening_db`/`seat_db`/`booking_db`/`payment_db` 4개 스키마, 도메인 간
FK 4개 제거, `config` 패키지 도메인별 `DataSource`+`SqlSessionFactory`+`@MapperScan` 구성. 상세 → `docs/db/db.md`.

**T-06 결정 완료 (2026-07-24)**: 낙관적 락 충돌(`SeatConflictException`) 재시도 로직은 후보 B(재시도 없이 그대로 예외)로
확정. 좌석 hold는 배타적 자원이라 충돌 = 진짜 비즈니스 결과(이미 남이 가져감)라서 재시도해도 결국 `SeatNotAvailableException`으로
귀결됨 — 코드 변경 없음, 지금 구현이 그대로 최종 형태. 상세 → `Todo.md` 완료된 논제 표.

**T-04 구현 + 수동 검증 완료 (2026-07-24)**: `BookingTimeoutBatch` 신설 — 방치된 `PENDING` booking을 booking
도메인이 스캔해서 회수한다. `POST /admin/batch/reconcile-pending-bookings`로 두 분기(payment `SUCCESS`였던
케이스 → confirm 구제 / 아니었던 케이스 → release+cancel) 둘 다 DB에 테스트 데이터 직접 심어서 수동 검증 완료
(schedule_seat·booking `mysql` 컨테이너에 직접 INSERT/UPDATE 후 endpoint 호출 → 결과 확인). 상세 → 아래
"오늘(2026-07-24) T-04" 절, `Todo.md` 완료된 논제 표. **`@Scheduled`는 아직 주석 처리 상태** — 사용자가 직접
주석을 풀어서 자동 실행을 확인해볼 예정.

다음 세션 우선순위:

1. **`T-12` CI/CD 착수** — GitHub Actions(또는 self-hosted runner)로 push 훅 → 이미지 빌드 → 배포 서버 재배포 자동화. 지금까지 `docs/deploy/aws-ec2.md` §9(수동 재배포: `git pull` + `docker compose up -d --build`)로 손으로 하던 걸 자동화하는 게 목표. nginx blue/green 무중단 배포는 이 안에서 별도 단계로 다룸
2. **`T-10` 계속** — 충돌감지형(낙관적) 락 쪽 동시성 테스트("기다리지 않고 즉시 충돌") 추가. 그다음 `SeatService`의 `confirm()`/`release()`/`getSeatGrid()`, 다른 도메인(`PaymentService` 등)으로 Mockito 테스트 범위 확장
3. `T-04` 마무리 — `BookingTimeoutBatch`용 JUnit 테스트 추가(T-10 범위에 편입 가능). `@Scheduled` 주석 해제는 사용자가 직접 진행
4. 이후 `T-08`(관리자 CRUD) 착수

**순서 확정 (2026-07-24) → 순서 변경(2026-07-26, `T-11`을 `T-10` 전에 진행) → 최종 순서 재확정(2026-07-26)**:
`T-11` 완료 직후 다시 논의해서 **`T-12`(CI/CD) → `T-10`/`T-04` 테스트 마무리 → `T-08`(CRUD)** 순으로 확정.
CI/CD를 CRUD보다 먼저 하는 기존 취지(학습 우선순위)는 유지하되, 테스트가 이 프로젝트 핵심 학습 목표라 CRUD로
새 복잡도를 얹기 전에 먼저 다지기로 함. 상세 → `Todo.md` T-08/T-12 "순서 결정/변경" 기록.

## 완료된 것

```
✅ build.gradle           의존성 완료 (Thymeleaf 제거, webmvc+mybatis만 유지. UTF-8 인코딩 옵션 포함)
✅ application.yaml       DB 접속 설정 완료 (localhost:3306, Docker MySQL, 비밀번호 password로 통일)
✅ sql/01~04-*-schema.sql 도메인별 스키마 DDL (screening_db/seat_db/booking_db/payment_db, schedule_seat.version 컬럼 포함).
                          파일명 번호로 실행 순서 강제 (T-09, 2026-07-21에 단일 01-schema.sql에서 분리)
✅ sql/05-data.sql        시드 데이터 (영화1, A관, 25석, 스케줄1) — 스키마 전환하며 시딩
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
✅ T-09 DB 스키마 분리       screening_db/seat_db/booking_db/payment_db 4개로 분리, 도메인 간 FK 4개 제거, config 패키지에
                          도메인별 DataSource+SqlSessionFactory+@MapperScan(sqlSessionFactoryRef) 구성. docker-compose.yml/
                          application.yaml 갱신, 전체 Saga 흐름 실제 HTTP 검증 완료 (2026-07-21)
✅ T-10 4단계 착수 + 버그 수정  SeatServiceConcurrencyTest — 대기형(비관적) 락 동시성 테스트(스레드 2개). 처음 돌렸을 때
                          PlatformTransactionManager 빈 부재로 @Transactional 전체가 무시되던 버그 발견·수정(config
                          패키지 4개 + Seat/Payment/BookingService). 충돌감지형 락 쪽은 아직 미작성 (2026-07-21)
✅ T-04 HELD 타임아웃 배치   BookingTimeoutBatch(+ 수동 트리거용 BookingTimeoutBatchController) 신설. booking 도메인이
                          스캔 주도(findStalePending), BookingOrchestrator.tryConfirmIfPaid 재사용. 두 분기(confirm
                          구제/release+cancel) 다 DB에 테스트 데이터 심어서 수동 검증 완료. 상세 → 아래
                          "오늘(2026-07-24) T-04" 절. @Scheduled는 주석 상태(사용자가 직접 해제 예정), JUnit은 다음 세션 (2026-07-24)
✅ T-11 실 서버 배포        AWS EC2(프리티어, Amazon Linux 2023)에 MySQL+앱 컨테이너 실제 배포. 보안 그룹(SSH는 내 IP만,
                          8080 전체 공개, 3306 비공개), git clone(master) → .env 운영용 DB_PASSWORD 신규 생성 →
                          docker compose up -d --build. 브라우저로 퍼블릭 IP:8080 접속해 실제 인터넷 통한 배포 확인
                          완료. 상세 → 아래 "오늘(2026-07-26) T-11" 절 (2026-07-26)

⬜ (사소, 우선순위 낮음) 로그 파일에 찍히는 한글 예외 메시지가 콘솔 출력 경로에서 일부 깨짐 — DB 저장값/HTTP JSON 응답엔 영향 없음, 순수 콘솔 표시 문제로 추정. 다시 볼 때 아래 "오늘 겪은 인프라 문제" 참고
```

## 오늘(2026-07-26) T-11 실 서버 배포

> 다른 사람이 같은 사양(AWS EC2 프리티어)으로 이 프로젝트를 처음부터 배포할 때는 이 절 대신
> **[docs/deploy/aws-ec2.md](docs/deploy/aws-ec2.md)** 런북을 그대로 따라가면 된다 — 여기 아래 내용은
> 그 문서의 근거가 된 이번 세션의 진행 기록.

`T-10`이 안 끝난 상태였지만 사용자 판단으로 순서를 바꿔서 `T-11`(MySQL을 실제 서버로 배포)부터 먼저 진행했다.

**호스팅/DB 선택**: AWS EC2 프리티어(`t2.micro`/`t3.micro`, 리전 `ap-southeast-2` 시드니, AMI `Amazon Linux 2023`)로
결정. DB는 AWS RDS(관리형)와 EC2 안 MySQL 컨테이너 중 논의했는데, RDS는 `docker-entrypoint-initdb.d` 같은 자동
init 스크립트 지원이 없어 스키마 4개(`screening_db` 등) 세팅을 수동으로 다시 해야 해서, 기존 `docker-compose.yml`
+ `sql/01~05-*.sql`을 그대로 재사용할 수 있는 **EC2 안 MySQL 컨테이너 쪽으로 확정** — 추가 학습 비용 없음.

**보안 그룹**: SSH(22)는 "내 IP"로만 제한, 사용자 지정 TCP 8080은 `0.0.0.0/0`(브라우저 테스트용, 나중에 `T-12`에서
nginx 붙이면 80/443만 열고 8080은 닫을 예정), **3306(MySQL)은 아예 안 엶** — 앱 컨테이너만 도커 내부 네트워크로
접근.

**배포 과정에서 겪은 문제들**:
1. **`icacls` 권한 설정 실수** — Windows에서 `.pem` 키 권한을 `"$env:USERNAME:R"`처럼 큰따옴표 안에 `$env:` 변수를
   붙여 썼더니 PowerShell이 `USERNAME:R`을 통째로 환경변수 이름으로 착각해 빈 값이 되는 문법 함정에 걸림 →
   `"${env:USERNAME}:R"`처럼 중괄호로 변수 경계를 명확히 해서 해결
2. **SSH `Permission denied`** — 권한 설정 명령이 (1번 문제로) 실제로는 적용이 안 됐던 상태라 `.pem` 파일에
   `Authenticated Users`/`BUILTIN\Users` 그룹 권한이 그대로 남아있었음. `icacls ... /remove "Authenticated Users"`,
   `/remove "BUILTIN\Users"`로 제거해서 해결 (OpenSSH는 소유자 본인 외 `Administrators`/`SYSTEM`까지는 허용하지만
   그 외 그룹이 남아있으면 "너무 열려있다"고 거부함)
3. **Amazon Linux 2023 `dnf`의 `docker` 패키지엔 Compose/Buildx가 기본 포함 안 됨** — 둘 다 GitHub 릴리스에서
   바이너리를 받아 `/usr/local/lib/docker/cli-plugins/`에 수동 설치해야 했음 (`docker-compose`, `docker-buildx`
   각각). `docker compose up --build` 실행 시 `compose build requires buildx 0.17.0 or later` 에러로 처음 발견함
4. **첫 빌드 시도가 통째로 날아감** — SSH 세션이 빌드 도중 끊기면서(`docker compose up -d --build`가 `-d`에도
   불구하고 이미지 빌드 자체는 포그라운드로 진행됨) 컨테이너가 하나도 안 만들어진 채 프로세스가 죽음
   (`docker ps -a` 완전히 비어있음으로 확인). **`tmux` 세션 안에서 재실행**하는 방식으로 전환해 SSH 끊김에
   영향받지 않게 해결
5. **로컬 `git`이 `master`보다 `local_branch`가 3커밋 앞서 있던 상태** — fast-forward 머지 가능한 상황이라
   GUI(Fork)에서 `master` 체크아웃 → `local_branch` merge → 양쪽 다 push로 정리. 서버는 `master` 브랜치 기준으로
   `git clone`

**환경변수 분리**: 로컬 PC의 `.env`(개발용 `DB_PASSWORD`)와 EC2 서버 안의 `.env`(운영용, `openssl rand -base64 24`로
새로 생성)는 완전히 별개의 파일 — `docker-compose.yml` 코드는 양쪽에 동일하게 두고, `docker compose up`을 실행하는
컴퓨터가 어디냐에 따라 그 컴퓨터의 로컬 `.env`를 읽는 방식이라 IP나 다른 조건으로 자동 분기하는 로직은 없음.

**로컬 SSH 편의 설정**: `~/.ssh/config`(Windows: `C:\Users\water\.ssh\config`)에 `Host cinema` 별칭 등록해서
매번 `ssh -i "D:\aws\cinema-key.pem" ec2-user@<퍼블릭IP>` 대신 `ssh cinema`로 접속 가능하게 함. 메모장으로 처음
저장했을 때 확장자가 자동으로 붙어 `config.txt`로 저장되는 바람에 ssh가 못 찾는 문제가 있었음 — 파일명에서
`.txt` 제거로 해결 (config 파일은 정확히 `config`라는 이름이어야 ssh가 자동으로 읽음).

**검증**: `docker compose ps`로 `mysql`/`app` 컨테이너 둘 다 정상 기동 확인 → 로컬 브라우저에서
`http://<퍼블릭IP>:8080` 접속해서 정적 HTML 메인 화면이 뜨는 것까지 확인 완료. **전체 예매 플로우(스케줄 선택 →
좌석 선택 → 예매)까지의 실제 통합 테스트는 아직 안 함** — 다음에 필요하면 진행.

**컨테이너 내리기/올리기**: 학습 세션이 끝나면 EC2 인스턴스는 그대로 켜둔 채 `docker compose down`으로 컨테이너만
내리기로 함 (프리티어 750시간/월이 인스턴스 1대 24시간 운영을 이미 커버해서 인스턴스까지 끌 필요는 없다고 판단;
인스턴스를 중지했다 재시작하면 Elastic IP를 안 붙여놔서 퍼블릭 IP가 바뀌는 것도 고려한 결정).

## 오늘(2026-07-24) T-04 HELD 타임아웃 배치 구현

`BookingOrchestrator.reserve()`가 `catch(Exception e)`(예측 외 기술 오류)로 빠지면 좌석은 `HELD`, booking은
`PENDING`으로 잔류하는데, 지금까지는 이걸 회수할 방법이 없었다. 이번 세션에 T-04를 확정하고 실제로 구현했다.

**구현 순서**:
1. `catch(Exception e)` 안에서 `paymentFacade.findStatusByBookingId()`로 즉시 재조정을 시도하는 코드를 먼저 짜봤다가
   (사용자가 직접 IDE에서 수정), 코드 리뷰 과정에서 진짜 성공 케이스인데도 `throw e`까지 흘러가서 클라이언트에
   에러 응답이 가는 버그, 그리고 재조정 시도 자체가 실패하면 원래 예외가 사라지는 문제를 잡아 수정 — `return bookingId`로
   성공 분기를 끊고, 재조정 시도를 `try/catch`로 감싸 실패해도 원래 예외가 보존되게 함
2. `getResult()`(지연 재조정)와 로직이 겹쳐서 `tryConfirmIfPaid(bookingId, scheduleId, seatId)` private 메서드로 추출.
   이 과정에서 `&&` 피연산자 순서 버그(단축 평가로 `booking.status()==PENDING` 체크보다 `tryConfirmIfPaid` 부수효과가
   먼저 실행돼서, 이미 `CONFIRMED`/`CANCELLED`된 booking도 GET할 때마다 불필요하게 재확정 시도하던 문제)도 잡아 수정
3. T-04 본체: `BookingMapper.findStalePending(cutoff)` 신설(`status='PENDING' AND created_at < cutoff`, 새 컬럼 없이
   기존 `created_at` 재사용) → `BookingService.findStalePending()` → `BookingTimeoutBatch.reconcilePendingBookings()`가
   각 stale booking마다 `tryConfirmIfPaid()`를 먼저 시도(payment가 실제로는 SUCCESS인데 confirm만 못한 경우 구제) →
   그래도 안 되면 `seatFacade.release()` + `bookingService.cancel()`
4. `tryConfirmIfPaid`를 `private` → package-private으로 완화해서 `BookingTimeoutBatch`(같은 `booking` 패키지)가 재사용 —
   `public`으로 완전히 열면 다른 도메인이 `BookingOrchestrator`를 직접 주입받아 호출할 길이 생겨 AGENT.md §1 "경계는
   Facade만" 규칙이 깨질 수 있어서 package-private 유지로 결정

**설계 결정**: 스캔은 booking 도메인이 주도한다 — `schedule_seat`엔 `booking_id`가 없어서(T-09, 도메인 간 FK 제거)
seat 쪽만 봐서는 "어느 booking과 연결된 HELD인지" 알 수 없기 때문. `TIMEOUT_MINUTES=1`(분)로 테스트하기 편한 값을 선택
(실무라면 더 길게 잡아야 하지만, `MockPaymentGateway`가 3초 지연이라 1분이면 정상 결제와 절대 안 겹침).

**배치 주기는 아직 자동화 안 함**: `@Scheduled(fixedDelay = 30_000)`은 코드에 주석으로만 남겨뒀다 — 실제로 주기
실행시키기 전에 먼저 수동으로 동작을 확인하고 싶어서. 대신 `POST /admin/batch/reconcile-pending-bookings`
(`BookingTimeoutBatchController`)로 원할 때 직접 호출. `CinemaApplication`엔 `@EnableScheduling`만 미리 켜둠 —
나중에 `@Scheduled` 주석 풀 때 이거 빠뜨리는 실수 방지용. **사용자가 직접 주석을 풀어서 자동 실행을 확인해볼 예정.**

**수동 검증 완료**: `mysql` 컨테이너에 직접 접속해서(`docker compose exec mysql mysql -uroot -p...`) `schedule_seat`을
`HELD`로, `booking`을 `created_at`이 5분 전인 `PENDING`으로 심어놓고 endpoint를 호출해 두 분기를 각각 확인했다 —
① `payment` 레코드를 아예 안 넣은 케이스(→ `release`+`cancel`), ② `payment.status='SUCCESS'`로 넣은 케이스
(→ `tryConfirmIfPaid`가 confirm으로 구제). 둘 다 기대한 대로 동작. **JUnit 테스트는 아직 없음** — 다음 세션 과제
(T-10 4단계 범위에 자연스럽게 편입 가능).

## 오늘(2026-07-24) Spring Boot 앱 도커라이즈 (T-12 선행 조건)

MySQL만 컨테이너였던 걸 앱도 도커라이즈했다 — `docker/app/Dockerfile`(멀티스테이지: `eclipse-temurin:17-jdk`에서
프로젝트에 커밋된 Gradle Wrapper로 `bootJar` 빌드 → `eclipse-temurin:17-jre`로 jar만 복사해 실행), `.dockerignore`
신설, `docker-compose.yml`에 `app` 서비스 추가. `mysql` 서비스엔 `healthcheck`(`mysqladmin ping`)를 추가해서
`app`이 `depends_on: condition: service_healthy`로 스키마 초기화까지 끝난 뒤에만 뜨도록 함.
`application.yaml`이 이미 `${DB_HOST:localhost}`로 환경변수화돼 있어서 코드 변경은 필요 없었고, `app` 서비스에
`DB_HOST: mysql`만 얹었다.

**로컬 개발 워크플로는 안 바뀜**: 컨테이너 재빌드가 인텔리제이의 즉시 컴파일보다 훨씬 느려서, 평소 개발은 여전히
인텔리제이/`./gradlew bootRun` + `docker compose up -d mysql`(DB만) 조합을 쓴다. `docker-compose.yml`(앱+DB
전부)은 "나중에 배포 서버에 그대로 들고 갈 설정"으로만 씀 — 로컬용/배포용 compose 파일을 지금 나눌지도 논의했는데,
지금은 prod에서만 달라야 할 설정이 없어서 나누지 않기로 함(`T-11` 착수 시점에 재검토, `Todo.md` 참고).

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
| 비밀번호 | `password` (docker-compose.yml/application.yaml의 4개 `app.datasource.*` 블록 전부 `.env`의 `DB_PASSWORD` 하나로 통일) |
| DB명 | `screening_db`/`seat_db`/`booking_db`/`payment_db` 4개 (T-09, 2026-07-21 — 예전엔 `cinema` 단일 스키마였음) |
| 접속 URL 예 | `jdbc:mysql://localhost:3306/seat_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8` (나머지 3개도 DB명만 다름) |

## 실 서버(T-11, AWS EC2) 환경

| 항목 | 값 |
|------|---|
| 호스팅 | AWS EC2 프리티어, 리전 `ap-southeast-2`(시드니), AMI `Amazon Linux 2023` |
| 퍼블릭 IP | `54.153.149.155` (Elastic IP 미사용 — 인스턴스를 중지했다 재시작하면 바뀔 수 있음) |
| SSH 접속 | `ssh cinema` (로컬 `~/.ssh/config`에 별칭 등록됨, 키 파일 `D:\aws\cinema-key.pem`) |
| 앱 접속 | `http://54.153.149.155:8080` |
| 배포 방식 | SSH 접속 → `~/cinema`(git clone된 `master` 브랜치) → `docker compose up -d --build` (빌드는 `tmux` 세션 안에서 실행 — SSH 끊김 방지) |
| 컨테이너 내리기 | `~/cinema`에서 `docker compose down` (인스턴스 자체는 계속 켜둠) |
| `.env` 위치 | `~/cinema/.env` (서버 로컬 파일, git 미포함, 운영용 `DB_PASSWORD` 별도 생성값) |
| 보안 그룹 | SSH(22) 내 IP만, 8080 전체 공개, 3306 비공개 |
