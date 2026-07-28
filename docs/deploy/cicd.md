# CI/CD 파이프라인 (T-12)

> `master`에 push하면 자동으로 이미지 빌드 → GHCR 업로드 → EC2 배포까지 이어지는 파이프라인 문서.
> 서버를 처음부터 세팅하는 절차는 [aws-ec2.md](aws-ec2.md) 참고 — 이 문서는 "이미 떠 있는 서버에
> 코드 변경을 자동으로 반영하는 방법"만 다룬다. `aws-ec2.md` §9(수동 재배포 절차)를 대체한다.

---

## 1. 전체 흐름

```
git push (master)
  │
  ▼
[GitHub Actions] build-and-push job          ← GitHub 소유 러너에서 실행
  ① 코드 checkout
  ② GHCR 로그인
  ③ 이미지 태그 계산 (latest, sha-xxxxxxx)
  ④ Docker 이미지 빌드 + GHCR push
  │
  ▼ (needs: 성공해야 다음 job 시작)
[GitHub Actions] deploy job                   ← GitHub 소유 러너에서 실행, SSH로 EC2에 명령만 보냄
  SSH로 EC2 접속:
    cd ~/cinema
    git pull origin master
    docker compose -f docker-compose.yml -f docker-compose.prod.yml pull app
    docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
  │
  ▼
[EC2 서버] app 컨테이너가 새 이미지로 재기동됨 (mysql은 안 건드림)
```

**서버는 더 이상 이미지를 직접 빌드하지 않는다** — GitHub Actions 러너가 빌드해서 GHCR(GitHub Container
Registry)에 올려두면, 서버는 `pull`로 완성된 이미지를 받기만 한다. 프리티어(`t2.micro`/`t3.micro`, RAM 1GB)
서버에 빌드 부하를 주지 않기 위한 결정.

---

## 2. 아키텍처 결정과 이유

| 결정 | 선택 | 이유 |
|------|------|------|
| CI 도구 | GitHub Actions | Jenkins도 검토했으나, 이 프로젝트 EC2가 이미 프리티어 750시간을 다 쓰고 있어 Jenkins용 인스턴스를 추가하면 비용 발생, 같은 인스턴스에 얹으면 RAM 1GB로 OOM 위험. Jenkins 자체를 배우고 싶은 니즈는 확인했으나 **이번 스코프에서는 제외**, 별도로 학습하기로 함 |
| 이미지 빌드 위치 | GitHub Actions 러너 (서버 아님) | 서버에서 직접 빌드하면 `docker compose up --build`가 몇 분씩 걸리고 SSH 끊김에도 취약함(§7 "함정 6", `aws-ec2.md` 참고). 러너에서 빌드 후 레지스트리에 push하는 게 실무 표준 패턴이자 서버 부하 회피 |
| 레지스트리 | GHCR, **public** | 이미지에 소스코드나 시크릿이 들어가지 않음(설정은 환경변수로 주입) — public으로 열면 서버가 별도 로그인 없이 `pull` 가능해서 구조가 단순해짐. private로 가면 서버에 GitHub PAT을 추가로 관리해야 함 |
| 배포 방식 | SSH로 EC2 접속해 `git pull` + `docker compose pull/up` | 지금까지 손으로 하던 `aws-ec2.md` §9 절차를 그대로 자동화. `docker-compose.prod.yml`이 `app` 서비스만 GHCR 이미지로 override — 로컬 개발용 `docker-compose.yml`은 그대로 build 방식 유지 |
| CI(자동 테스트) | **이번 스코프에서 제외** | `T-10`/`T-04`의 JUnit 테스트가 아직 다 안 채워짐. 테스트가 충분해지면 `build-and-push`보다 먼저 도는 `test` job(`./gradlew test`)을 추가해서 실패 시 배포를 막을 예정 — 상세 → `Todo.md` T-12 |
| 무중단 배포 (nginx blue/green) | **이번 스코프에서 제외** | 지금은 `app` 컨테이너를 내렸다 새로 올리는 방식이라 몇 초 다운타임 발생. 별도 단계로 다음에 진행 |

---

## 3. 구성 파일

### `.github/workflows/deploy.yml`

```yaml
name: Deploy

on:
  push:
    branches: [master]
  workflow_dispatch: {}

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - id: meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository }}
          tags: |
            type=raw,value=latest
            type=sha,format=short
      - uses: docker/build-push-action@v6
        with:
          context: .
          file: docker/app/Dockerfile
          push: true
          tags: ${{ steps.meta.outputs.tags }}

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ec2-user
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            cd ~/cinema
            git pull origin master
            docker compose -f docker-compose.yml -f docker-compose.prod.yml pull app
            docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

- `on.push.branches: [master]` — `master`에 push될 때만 트리거. `workflow_dispatch`는 Actions 화면에서
  "Run workflow" 버튼으로 수동 재실행할 수 있게 해주는 트리거(코드 변경 없이 재시도하고 싶을 때 사용)
- `permissions.packages: write` — 자동 발급되는 `secrets.GITHUB_TOKEN`이 GHCR에 push할 권한을 갖게 함
  (기본은 read-only라 명시 안 하면 push가 막힘)
- `docker/metadata-action`은 빌드를 하지 않고 태그 문자열(`latest`, `sha-xxxxxxx`)만 계산해서 다음 스텝에
  `steps.meta.outputs.tags`로 넘겨줌
- `docker/build-push-action`이 빌드와 push를 한 스텝에서 처리
- `deploy` job은 `needs: build-and-push`로 순서를 강제 — 이미지가 실제로 레지스트리에 올라간 뒤에만 시작
- `appleboy/ssh-action`(서드파티 액션)이 SSH 접속+명령 실행을 감싸줌

### `docker-compose.prod.yml`

```yaml
services:
  app:
    image: ghcr.io/soojin1999/cinema:latest
```

- `docker-compose.yml`의 override 파일 — `-f docker-compose.yml -f docker-compose.prod.yml`로 겹쳐서 씀
- base의 `app.build:` 설정은 그대로 두고 `image:`만 추가. `pull app`을 먼저 실행해서 해당 태그 이미지를
  로컬에 받아두면, 이어지는 `up -d`가 이미 있는 이미지를 그대로 쓰고 재빌드하지 않음
- `mysql` 서비스는 이 파일에서 안 건드림 (기존 build 방식 그대로)
- 로컬 개발 PC에서는 이 파일을 안 끼워 쓰므로(`-f` 옵션 없이 `docker compose up -d mysql`만 사용) 로컬
  워크플로엔 영향 없음

---

## 3-1. 헷갈리기 쉬운 부분 — 파일 4개의 역할 구분

세팅하면서 실제로 헷갈렸던 지점이라 정리해둔다.

**① GHCR에 올라가는 이미지를 실제로 만드는 지침서는 `docker/app/Dockerfile` 하나뿐이다.**

```dockerfile
FROM eclipse-temurin:17-jdk AS build
COPY . .
RUN ./gradlew bootJar --no-daemon -x test
FROM eclipse-temurin:17-jre
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

"소스 복사 → Gradle로 `bootJar` 컴파일 → 그 jar만 가벼운 JRE 이미지에 담기" 이 내용 전체가 여기 있다.
`deploy.yml`의 `build-and-push` job이든, 로컬의 `docker compose up --build`든, **누가 빌드를 걸든 결국
이 파일에 적힌 대로 이미지가 만들어진다** — 이미지의 "재료 목록이자 조리법"은 이거 하나뿐이라는 뜻.

**② `deploy.yml`은 `docker-compose.yml`을 전혀 참조하지 않고 이 `Dockerfile`을 직접 빌드한다.**

```yaml
- uses: docker/build-push-action@v6
  with:
    context: .
    file: docker/app/Dockerfile   # ← docker-compose.yml과 무관하게 직접 지정
```

`docker-compose.yml`의 `app.build.dockerfile`도 마침 같은 경로(`docker/app/Dockerfile`)를 가리키고 있어서
"같은 이미지"가 나오는 것처럼 보이지만, 이건 **의도적으로 경로를 맞춰둔 것**이지 한쪽이 한쪽을 트리거하는
관계가 아니다. `build-and-push` job은 `docker compose`라는 명령어 자체를 실행하지 않는다.

**③ 이미지를 "빌드해서 GHCR에 올려두는 것"과 "그 이미지로 컨테이너를 실행하는 것"은 완전히 다른 작업이다.**

`build-and-push` job이 끝나도 그 시점엔 서버에서 아무 컨테이너도 안 뜬다 — GHCR이라는 창고에 이미지가
하나 저장돼 있는 상태일 뿐. 실제로 컨테이너를 띄우는 건 `deploy` job의 마지막 두 줄이고, 그때 "어떤 이름으로,
어떤 포트로, 어떤 환경변수로 띄울지"는 `docker-compose.yml`의 `app:` 서비스 블록(`container_name`, `ports`,
`environment`, `depends_on`)이 정의한다. **만약 이 블록이 없다면**, `pull app`은 대상 서비스 자체가 없어
실패하거나 `up -d`가 `mysql`만 띄우고 끝난다 — 이미지가 GHCR에 멀쩡히 있어도 스프링 서버는 안 올라온다.

즉 역할을 나누면: **`Dockerfile`**(이미지 안에 뭐가 들어가는지) → **`deploy.yml`**(그 이미지를 언제, 어떻게
빌드해서 GHCR에 올리고 서버에 배포 명령을 내릴지) → **`docker-compose.yml`의 `app:`**(그 이미지를 실제로
어떤 설정의 컨테이너로 실행할지) → **`docker-compose.prod.yml`**(서버에서만 그 이미지의 출처를 GHCR로
바꿔치기) 이렇게 4개 파일이 각자 다른 층위를 담당한다.

---

## 4. GitHub Secrets

저장소 **Settings → Secrets and variables → Actions**에 등록:

| Secret | 값 |
|--------|-----|
| `EC2_HOST` | EC2 퍼블릭 IP (`54.153.149.155`) |
| `EC2_SSH_KEY` | `.pem` 파일 내용 전체 (`-----BEGIN...`부터 `-----END...`까지) |

`GITHUB_TOKEN`은 등록 불필요 — 워크플로 실행마다 GitHub이 자동으로 발급하는 임시 토큰.

GHCR 패키지는 첫 push 이후 저장소 옆 **Packages** 메뉴에서 **Public**으로 visibility를 변경해줘야 함
(패키지가 생기기 전엔 설정 화면 자체가 없어서, 최초 1회 워크플로 실행 후 수동으로 바꿔야 함).

---

## 5. 겪은 문제 — SSH 인바운드 규칙

**증상**: 첫 실행에서 `build-and-push`는 성공했지만 `deploy`가 38초 만에 실패. 로그:
```
dial tcp ***:22: i/o timeout
```

**원인**: `aws-ec2.md`에 정리된 보안 그룹 설정이 SSH(22)를 "내 IP만" 허용하고 있었음. GitHub Actions의
`deploy` job은 매 실행마다 다른(예측 불가능한) IP의 GitHub 소유 러너에서 도는데, 그 IP가 허용 목록에
없어서 EC2에 패킷이 아예 도달하지 못하고 타임아웃.

**해결**: 보안 그룹의 SSH 인바운드 규칙 소스를 `0.0.0.0/0`(Anywhere)으로 변경. GitHub이 공개하는 IP
대역만 허용하는 방법도 검토했으나, 수백 개 CIDR이 주기적으로 바뀌어 유지보수가 비현실적이라 제외.
`0.0.0.0/0`으로 열어도 서버가 `PasswordAuthentication no`(비밀번호 로그인 자체가 꺼져있음, Amazon Linux
2023 AMI 기본값 — `/etc/cloud/cloud.cfg`의 `ssh_pwauth: false`로 확인)라 `.pem` 키 없이는 인증 자체가
불가능 — 포트 스캔 노출은 늘지만 실질적 침투 위험은 낮다고 판단.

수정 후 재실행 → `build-and-push`, `deploy` 둘 다 성공 확인 완료 (2026-07-28).

---

## 6. 남은 일

- **CI(자동 테스트) 추가**: `T-10`/`T-04` JUnit 테스트가 충분히 채워지면 `build-and-push` 앞에 `test` job
  추가 예정. 상세 → `Todo.md` T-12
- **nginx blue/green 무중단 배포**: 지금은 `app` 컨테이너 재기동 시 몇 초 다운타임 발생. 별도 단계로 진행 예정
