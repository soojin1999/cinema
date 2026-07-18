# 🎬 cinema — 영화관 좌석 예매 토이 프로젝트

> **[DECISIONS.md](DECISIONS.md)**에 이 프로젝트를 진행하며 직접 판단하고 결정한
> 주요 지점들을 정리해뒀다.

## 프로젝트 목표

기능 완성도·배포·확장성이 목표가 아니다. **세 가지 아키텍처 개념을 직접 구현하며 몸에 익히는 것**이 학습 페이즈의 전부다.

| # | 개념 | 배우려는 것 |
|---|------|-----------|
| ① | **경계 설계** (Bounded Context / 모듈러 모놀리스) | 도메인 단위로 코드를 나누고, 도메인이 서로의 내부를 직접 건드리지 못하도록 강제하는 구조 설계 |
| ② | **Saga 패턴** (오케스트레이션 방식) | 단일 DB 트랜잭션으로 묶을 수 없는 비즈니스 흐름을, 실패 시 보상(compensation)까지 포함해 안전하게 조율하는 방법 |
| ③ | **테스트 코드 작성** (JUnit) | 지금까지 구현된 로직(특히 동시성 제어)을 자동화된 테스트로 검증하는 방법. 사용자가 JUnit 최초 학습이라 단계별로 진행 |

### 최종 목표 (운영 시나리오) — 2026-07-18 기록

학습 페이즈의 베이스는 여전히 **MSA 구조 학습**(경계 설계 + Saga)이지만, 이 프로젝트가 최종적으로 도달할 스코프는
**실제로 운영 가능한 영화관 예매 앱**이다. 구체적으로:

- 극장(theater) 등록/관리
- 영화(movie) 등록/관리
- 상영 스케줄(schedule) 등록/관리
- 좌석 배치 커스터마이징 (예: 상영관마다 3열×5행처럼 행/열 구성을 다르게 지정)

지금은 이 넷이 전부 시드 데이터로 고정돼 있고 조회만 가능한 상태(`screening` 패키지)다. 관리자용 CRUD로
확장하는 건 학습 페이즈(경계·Saga·테스트) 완료 후 순서로 진행한다 — 순서·세부 설계는 아직 미정, 착수 시점에
`Todo.md`에서 논의.

---

## 범위 밖 (하지 않는 것)

- 진짜 MSA (서비스 물리 분리 — 별도 배포 단위, 별도 서버)
- 메시지 브로커 (Kafka, RabbitMQ)
- 서비스 디스커버리, K8s
- 실제 결제 PG 연동

→ **단일 앱**은 유지한다. DB를 도메인별로 스키마 분리할지(단일 DB 유지 vs 스키마 분리)는 별도 논제로
`Todo.md`에서 논의 중 — 이 줄은 그 결정이 나면 갱신한다.

---

## 기술 스택

| 영역 | 선택 | 채택 이유 |
|------|------|---------|
| Language | Java 17 | 사용자 실무 언어 |
| Framework | Spring Boot 4.0.7 | 사용자 실무 스택. 새 프레임워크를 얹으면 "Saga 문제 vs 프레임워크 문제"가 섞여 학습이 흐려짐 |
| ORM | MyBatis 4.0.1 | 사용자 실무 스택. JPA 학습 비용 없이 SQL 직접 제어 |
| DB | MySQL 8.0 | 사용자 실무 스택. 동시성·복합키·채번에서 강점 활용 |
| 인프라 | **Docker** | 연구 환경에서의 장점: 호스트 PC에 MySQL을 직접 설치하지 않아 청결함 유지, 컨테이너 삭제 시 흔적 없이 제거, 다른 프로젝트와 DB 버전 충돌 없이 공존, `docker-compose.yml` 한 파일로 환경 재현 가능 |
| Frontend | 정적 HTML + Vanilla JS (REST API 호출) | 초기엔 Thymeleaf(SSR)로 시작했으나, 백엔드를 `@RestController` + JSON API로 통일하기로 하면서 뷰 렌더링도 클라이언트로 옮김. `static/`의 HTML이 fetch로 API를 호출해서 직접 DOM을 그림 (2026-07-18 결정, Todo.md 기록) |
| 메시지 브로커 | 없음 | 오케스트레이터가 메서드를 순서대로 호출하면 충분. 브로커는 인프라 세팅에 시간을 뺏김 |
| Build | Gradle (Groovy DSL) | 프로젝트 초기 설정 시 선택됨 |

> Spring Boot 4.0.7은 2025-11-20 GA, 2026-06-10 패치. 안정 버전 확인 완료.

---

## 프로젝트 구조

```
com.toy.cinema
├── booking      ← 예매 도메인 (Saga 오케스트레이터 상주)
├── seat         ← 좌석 도메인
├── payment      ← 결제 도메인 (Mock 게이트웨이)
├── notification ← 알림 도메인
├── screening    ← 스케줄 조회 전용 (movie/theater/schedule, Saga 미참여라 Service만 생략, Facade는 다른 도메인과 동일하게 있음)
└── common       ← 공통 (예외, ENUM 등)
```

화면은 `src/main/resources/static/`의 정적 HTML(+인라인 JS)이 위 REST API를 fetch로 호출해서 그린다.

상세 설계 문서:
- [docs/db/db.md](docs/db/db.md) — DB 테이블 설계
- [docs/db/diagram.md](docs/db/diagram.md) — ERD·상태 흐름 다이어그램
- [docs/backend.md](docs/backend.md) — 아키텍처·Saga 흐름
- [docs/frontend.md](docs/frontend.md) — 화면 설계

AI 개발 규칙 명세: [AGENT.md](AGENT.md)
현재 개발 상태·세션 기록: [STATUS.md](STATUS.md)

---

## 로컬 실행

### 1. Docker MySQL 시작

```bash
# 연구용 Docker MySQL 컨테이너 시작 (-d: 백그라운드)
# Dockerfile(docker/mysql/)로 이미지를 로컬 빌드하므로 최초 실행 시 --build 권장
docker compose up -d --build
```

> 컨테이너 최초 실행 시 `01-schema.sql` → `02-data.sql`을 **자동으로 실행**한다 (파일명 앞 번호로 실행 순서 강제 — `docker-entrypoint-initdb.d`는 파일명 알파벳순 실행이라 번호 없인 `data.sql`이 먼저 돌아 테이블 생성 전에 실패함).
> `docker/mysql/charset.cnf`가 이미지에 구워져 있어 `mysql` 클라이언트 기본 charset이 `utf8mb4`로 강제된다 (Windows에서 bind mount로 이 파일을 넣으면 world-writable로 보여 MySQL이 무시해버리는 문제 때문에 이미지 빌드 방식으로 변경함 — 한글 등 멀티바이트 문자 이중 인코딩 방지).
> 이미 컨테이너가 떠 있다면 이 단계 건너뜀.

### 2. 비밀번호 확인 (최초 1회만)

DB 비밀번호는 `.env` 파일(git에 안 올라감, `.gitignore` 대상) 하나로 관리한다. `docker-compose.yml`의
`MYSQL_ROOT_PASSWORD`와 `application.yaml`의 `DB_PASSWORD`가 전부 이 파일의 `DB_PASSWORD` 값을 보므로,
**한 곳만 바꾸면 둘 다 같이 바뀐다** (예전엔 두 파일을 따로 손으로 맞춰야 했던 문제였음).

```bash
cp .env.example .env
# .env 열어서 DB_PASSWORD를 원하는 값으로 수정 (안 바꾸면 기본값 password로 그대로 동작)
```

- `docker compose`는 `.env`를 자동으로 읽는다 (Docker Compose 내장 기능)
- `./gradlew bootRun`은 `build.gradle`이 `.env`를 직접 읽어서 프로세스 환경변수로 넘겨준다

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

접속: `http://localhost:8080`

### Docker 유용한 명령어

```bash
docker compose up -d --build   # MySQL 컨테이너 시작 (Dockerfile 변경 시 --build로 재빌드)
docker compose down            # 컨테이너 정지 (데이터 유지)
docker compose down -v         # 컨테이너 + 볼륨 삭제 (데이터 초기화, 다음 up 때 시드 재실행)
docker compose logs mysql      # MySQL 로그 확인
```

---

## 트러블슈팅: 한글 데이터 깨짐 (이중 인코딩)

로컬에서 Docker MySQL을 처음 구축할 때 겪은 문제와 해결 과정 기록.

### 증상

`02-data.sql` 시드 데이터가 정상 실행됐는데도, 저장된 한글이 깨져서 나옴 (예: `인터스텔라` → 알 수 없는 문자열).

### 원인 — mysql 클라이언트의 기본 charset이 `latin1`

`docker-entrypoint-initdb.d`가 `02-data.sql`을 실행할 때 내부적으로 `mysql` 커맨드라인 클라이언트를 쓰는데, 이 클라이언트는 서버 설정(`character-set-server=utf8mb4`)과 무관하게 **자체 기본값인 `latin1`으로 접속**한다. 그 결과:

```
character_set_server     = utf8mb4  ← 서버는 정상
character_set_client     = latin1   ← 문제
character_set_connection = latin1   ← 문제
```

UTF-8로 인코딩된 한글 바이트(`인` = `EC 9D B8`)를 MySQL이 "latin1 문자들"로 잘못 해석한 뒤, 그걸 다시 utf8mb4로 재인코딩해서 저장 — 즉 **이중 인코딩(mojibake)**이 발생한다. `HEX(title)`로 저장값을 까보면 이 변환 흔적이 그대로 남아있어 확인 가능하다.

### 1차 시도 (실패) — `my.cnf`를 bind mount

```yaml
volumes:
  - ./docker/mysql/charset.cnf:/etc/mysql/conf.d/charset.cnf
```

`[client] default-character-set=utf8mb4`를 넣은 설정 파일을 마운트해서 클라이언트 기본 charset을 강제하려 했으나 실패:

```
mysql: [Warning] World-writable config file '...' is ignored.
```

Windows + Docker Desktop에서 호스트 파일을 bind mount하면, NTFS엔 리눅스식 권한 개념이 없어서 컨테이너 안에서 파일이 "누구나 쓸 수 있는(world-writable)" 권한으로 보이는 경우가 많다. MySQL은 보안상 이런 설정 파일을 **읽지 않고 무시**해버린다.

### 최종 해결 — 이미지 빌드 시 파일을 구워넣기

`image: mysql:8.0`으로 원본 이미지를 그대로 쓰는 대신, `docker/mysql/Dockerfile`로 커스텀 이미지를 빌드해서 `COPY`로 설정 파일을 넣는다. 이미지 빌드 과정에서 생성되는 파일은 리눅스 표준 권한(644)을 가지므로 world-writable 문제가 발생하지 않는다.

```dockerfile
# docker/mysql/Dockerfile
FROM mysql:8.0
COPY charset.cnf /etc/mysql/conf.d/charset.cnf
```

```yaml
# docker-compose.yml
services:
  mysql:
    build:
      context: ./docker/mysql
    # image: mysql:8.0  ← 대신 build 사용
```

적용(재빌드) 명령어:

```bash
docker compose down -v          # 이미 깨진 데이터가 볼륨에 남아있으므로 반드시 볼륨까지 삭제
docker compose up -d --build    # --build 없으면 예전 mysql:8.0 원본 이미지를 그대로 써서 반영 안 됨
```
