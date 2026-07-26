# AWS EC2 배포 가이드 (T-11)

> 이 문서 하나만 보고 이 프로젝트를 AWS EC2 프리티어 서버에 처음부터 배포할 수 있도록 정리한 런북이다.
> 2026-07-26 세션에서 실제로 배포하면서 겪은 시행착오를 전부 반영했다 — 같은 문제를 반복하지 않는 게 목적.
> 아키텍처·Saga 등 코드 자체에 대한 설명은 [docs/backend/backend.md](../backend/backend.md) 참고.

---

## 0. 준비물

- AWS 계정 (프리티어 사용 가능 계정)
- 이 레포 접근 권한 (GitHub)
- Windows 로컬 PC (SSH 클라이언트는 Windows 11 기본 내장 OpenSSH 사용)

---

## 1. EC2 인스턴스 생성

**AWS 콘솔 → EC2 → 인스턴스 시작**

| 항목 | 값 | 비고 |
|------|-----|------|
| 이름 | `cinema-app` | 자유롭게 |
| AMI | `Amazon Linux 2023` | 프리티어 라벨 확인 |
| 인스턴스 유형 | `t2.micro` 또는 `t3.micro` | 콘솔에 프리티어 표시된 것으로. 계정에 따라 둘 중 하나만 대상일 수 있음 |
| 키 페어 | 새로 생성 | 이름 예: `cinema-key`, `.pem` 파일 다운로드 후 안전한 곳에 보관 (분실 시 재발급 불가) |
| 스토리지 | 기본값 (8~30GB gp3) | 충분함 |

**네트워크 설정에서 "보안 그룹 생성" 선택** (기존 보안 그룹 선택 아님 — 이 프로젝트 전용으로 새로 만듦), 인바운드 규칙:

| 유형 | 포트 | 소스 | 이유 |
|------|------|------|------|
| SSH | 22 | 내 IP (`x.x.x.x/32`) | 관리자가 터미널로 서버에 들어가는 용도. 전체 공개(`0.0.0.0/0`)로 열면 무차별 로그인 시도에 노출되므로 반드시 본인 IP로 제한 |
| 사용자 지정 TCP | 8080 | `0.0.0.0/0` (Anywhere) | Spring Boot 앱이 이 포트로 뜸. 브라우저로 접속해야 하니 전체 공개. 나중에 nginx(T-12)를 붙이면 80/443만 열고 이 포트는 닫을 예정 |
| — | 3306 | **열지 않음** | MySQL은 컨테이너 안에서만 쓰고 외부 노출 안 함. `app` 컨테이너가 도커 내부 네트워크로만 접근 |

> 인스턴스 생성 후 콘솔에서 **퍼블릭 IPv4 주소**를 확인해둔다. Elastic IP를 안 붙이면 인스턴스를 중지했다가 다시 시작할 때 이 IP가 바뀐다는 점을 유의 — 지금은 컨테이너만 내리고 인스턴스는 계속 켜두는 방식으로 운영해서 문제 없음(§8 참고).

---

## 2. `.pem` 키 파일 권한 설정 (Windows)

OpenSSH는 개인 키 파일이 소유자 본인 외 다른 계정도 읽을 수 있으면 접속을 거부한다. Windows에서 다운로드한 `.pem` 파일은 기본적으로 여러 그룹에 권한이 걸려있어서 반드시 제한해야 한다.

```powershell
icacls "D:\aws\cinema-key.pem" /inheritance:r
icacls "D:\aws\cinema-key.pem" /grant:r "${env:USERNAME}:R"
```

⚠️ **함정 1**: `"$env:USERNAME:R"`처럼 큰따옴표 안에서 콜론을 바로 붙이면 PowerShell이 `USERNAME:R` 전체를 환경변수 이름으로 착각해서 빈 문자열이 된다 (`icacls`가 `/grant:r` 매개변수가 잘못됐다고 에러). 반드시 `"${env:USERNAME}:R"`처럼 중괄호로 변수 경계를 명확히 할 것.

**설정이 제대로 됐는지 확인**:
```powershell
icacls "D:\aws\cinema-key.pem"
```
결과에 `<사용자>:(R)`, `BUILTIN\Administrators:(F)`, `NT AUTHORITY\SYSTEM:(F)` 정도만 남아야 한다. 만약 `BUILTIN\Users`나 `NT AUTHORITY\Authenticated Users`가 남아있으면 (파일을 다른 폴더에서 옮겨왔을 때 특히 자주 발생) 아래처럼 제거한다:

```powershell
icacls "D:\aws\cinema-key.pem" /remove "Authenticated Users"
icacls "D:\aws\cinema-key.pem" /remove "BUILTIN\Users"
```

⚠️ **함정 2**: 위 두 그룹 권한이 남아있으면 SSH 접속 시 `Permission denied (publickey,gssapi-keyex,gssapi-with-mic)`가 아니라, 그 전 단계에서 클라이언트가 아예 `Load key "...": bad permissions` / `Permissions ... are too open`라고 거부한다. `-v`(verbose) 옵션으로 접속하면 정확한 원인이 보인다: `ssh -v -i "D:\aws\cinema-key.pem" ec2-user@<IP>`.

---

## 3. SSH 접속

```powershell
ssh -i "D:\aws\cinema-key.pem" ec2-user@<퍼블릭IP>
```

`Amazon Linux 2023`의 기본 사용자명은 `ec2-user`. Amazon Linux 2023 로고와 `[ec2-user@ip-... ~]$` 프롬프트가 뜨면 성공.

### 3-1. 매번 치기 귀찮으면: SSH config 별칭 등록 (선택, 추천)

로컬 PC의 `C:\Users\<사용자>\.ssh\config` 파일에 등록해두면 `ssh cinema`처럼 짧게 접속 가능:

```powershell
if (!(Test-Path "$env:USERPROFILE\.ssh")) { New-Item -ItemType Directory "$env:USERPROFILE\.ssh" }
notepad "$env:USERPROFILE\.ssh\config"
```

메모장에 아래 내용을 입력하고 저장:
```
Host cinema
    HostName <퍼블릭IP>
    User ec2-user
    IdentityFile D:\aws\cinema-key.pem
```

⚠️ **함정 3**: 메모장으로 새 파일을 저장하면 확장자가 자동으로 붙어서 `config.txt`로 저장되는 경우가 있다. `ssh`는 정확히 `config`라는 파일명만 자동으로 읽으므로, 저장 후 탐색기나 아래 명령으로 확장자가 안 붙었는지 꼭 확인:
```powershell
Get-ChildItem "$env:USERPROFILE\.ssh"
```
`config.txt`로 돼 있으면 이름을 `config`로 바꿔야 한다.

이후로는 그냥:
```powershell
ssh cinema
```

이 파일에 `Host` 블록을 여러 개 추가하면 서버가 늘어나도 같은 방식으로 관리할 수 있다.

---

## 4. Docker / Git 설치 (서버 안, SSH 접속 후)

Amazon Linux 2023은 `dnf` 패키지 매니저를 쓴다.

```bash
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
```

`usermod`로 그룹을 추가해도 **현재 세션엔 바로 적용 안 된다** — 한 번 로그아웃(`exit`) 후 재접속해야 한다.

재접속 후 확인:
```bash
docker ps
```
`sudo` 없이 에러 없이 빈 목록이 뜨면 성공.

### 4-1. Docker Compose 플러그인 설치

⚠️ **함정 4**: Amazon Linux 2023의 `dnf install docker`로 깔리는 패키지엔 **Compose 플러그인이 기본 포함돼 있지 않다.** 직접 받아서 설치해야 한다:

```bash
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
docker compose version
```

### 4-2. Docker Buildx 플러그인 설치

⚠️ **함정 5**: 이 프로젝트는 `docker compose up --build`로 이미지를 직접 빌드하는데, Compose v2는 빌드에 **Buildx**를 필요로 한다. 이것도 기본 미포함이라 별도 설치 필요. 안 하면 `compose build requires buildx 0.17.0 or later` 에러가 난다:

```bash
sudo curl -SL https://github.com/docker/buildx/releases/download/v0.17.1/buildx-v0.17.1.linux-amd64 -o /usr/local/lib/docker/cli-plugins/docker-buildx
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx
docker buildx version
```

(버전 문자열이 뜨면 성공. 위 URL의 `v0.17.1`은 이 시점 기준 최신 안정 버전이었던 값 — 나중에 더 최신 버전이 나왔으면 [Buildx 릴리스 페이지](https://github.com/docker/buildx/releases)에서 파일명 확인 후 버전만 바꿔서 받으면 된다.)

---

## 5. 코드 가져오기 (git clone)

```bash
cd ~
git clone -b master https://github.com/soojin1999/cinema.git
cd cinema
```

`master` 브랜치가 안정 버전이므로 이 브랜치 기준으로 배포한다. 로컬에서 작업 중인 브랜치(`local_branch` 등)에 변경사항이 있다면, 배포 전에 로컬에서 `master`로 fast-forward 머지 후 `git push origin master`까지 끝내둘 것.

---

## 6. 운영용 `.env` 생성

로컬 개발용 비밀번호를 그대로 쓰지 말고, 이 서버 전용 비밀번호를 새로 생성한다.

```bash
openssl rand -base64 24
```

출력된 값을 복사해서:
```bash
echo "DB_PASSWORD=여기에_방금_나온_값" > .env
```

이 `.env`는 `.gitignore`에 포함돼 있어 git에는 절대 올라가지 않고, 이 서버에만 로컬 파일로 존재한다. `docker-compose.yml`은 이 `.env`를 자동으로 읽어서 `mysql`(root 비밀번호)과 `app`(DB 접속 비밀번호) 양쪽에 같은 값을 적용한다.

> **중요**: 로컬 PC의 `.env`와 서버 안의 `.env`는 완전히 별개의 파일이다. 같은 `docker-compose.yml` 코드를 그대로 양쪽에서 쓰지만, `docker compose up`을 실행하는 컴퓨터가 자기 로컬 디렉터리의 `.env`를 읽는 방식이라, "어디서 실행하느냐"가 어떤 값이 적용될지를 결정한다. IP나 다른 조건으로 자동 분기하는 로직은 없다.

비밀번호를 나중에 다시 확인하려면:
```bash
cat ~/cinema/.env
```

---

## 7. 배포 (`docker compose up`)

⚠️ **함정 6**: `docker compose up -d --build`는 `-d`(백그라운드) 옵션이 있어도 **이미지 빌드 자체는 포그라운드로 진행**된다. 빌드 도중(특히 프리티어 사양이라 몇 분 걸릴 수 있음) SSH 연결이 끊기거나 터미널을 닫으면 빌드 프로세스가 그대로 죽어버리고, 컨테이너가 하나도 안 만들어진 채로 끝난다 (`docker ps -a`가 완전히 비어있는 상태로 확인됨).

**반드시 `tmux` 세션 안에서 실행할 것** — SSH가 끊겨도 서버 안의 세션은 계속 살아있다:

```bash
sudo dnf install -y tmux
tmux new -s deploy
```

(하단에 초록색 상태 바가 생기면 tmux 세션 안에 들어온 것)

그 안에서:
```bash
docker compose up -d --build
```

만약 SSH가 끊기면, 다시 접속해서 이렇게 하면 그 세션에 다시 들어갈 수 있다:
```bash
tmux attach -t deploy
```

**tmux 세션에서 안전하게 빠져나오는 법**: 터미널 창을 그냥 닫지 말고, `Ctrl+B`를 누른 뒤 손을 떼고 `D`를 누른다 (detach). 이러면 세션은 서버 안에서 계속 살아있고, SSH 접속만 끊는 것. 나중에 `tmux attach -t deploy`로 다시 들어가면 그대로 이어진다.

⚠️ **함정 7**: 이미 tmux 세션 **안에** 있는 상태에서 `tmux attach -t deploy`를 또 치면 `sessions should be nested with care, unset $TMUX to force`라는 경고가 뜨면서 접속이 안 된다 (tmux 안에서 tmux를 또 열려는 시도로 인식돼서 막힘). 지금 이미 tmux 세션 안인지 헷갈리면, 화면 맨 아래에 초록색 상태 바가 떠 있는지부터 확인할 것 — 떠 있으면 이미 들어와 있는 것이므로 `attach`가 아니라 그냥 원하는 명령어(`docker compose up -d --build` 등)를 바로 치면 된다.

**현재 떠 있는 tmux 세션 목록 확인**:
```bash
tmux ls
```

**빌드 완료 후 상태 확인**:
```bash
docker compose ps
```
`mysql`, `app` 둘 다 `Up`/`healthy` 상태로 나오면 성공.

---

## 8. 검증 및 운영

### 8-1. 브라우저 접속 확인
로컬 PC 브라우저에서:
```
http://<퍼블릭IP>:8080
```
정적 HTML 메인 화면이 뜨면 배포 성공.

### 8-2. 컨테이너 내리기 / 올리기
공부·테스트가 끝나면 인스턴스는 켜둔 채 컨테이너만 내리면 된다 (프리티어 750시간/월이 인스턴스 1대 24시간 운영을 이미 커버하므로 인스턴스까지 끌 필요 없음):

```bash
cd ~/cinema
docker compose down
```

다시 쓸 때:
```bash
cd ~/cinema
docker compose up -d
```

**`--build`는 언제 붙이고 언제 빼는지**:

| 상황 | 명령어 | 이유 |
|------|--------|------|
| 코드는 그대로, 껐다 다시 켤 때 (지금 이 경우) | `docker compose up -d` | 이전에 빌드해둔 이미지가 그대로 남아있어 재사용하면 됨. `--build`를 붙여도 에러는 안 나지만, 똑같은 걸 다시 빌드하느라 시간만 더 걸림 |
| `git pull`로 새 코드를 받아온 뒤 (§9 참고) | `docker compose up -d --build` | 이미지를 새로 빌드해야 바뀐 코드가 반영됨. 안 붙이면 예전 이미지 그대로 떠서 코드 변경이 무시됨 |

### 8-3. 인스턴스 자체를 끄고 싶다면
AWS 콘솔에서 **"중지"**(→ 나중에 다시 켤 수 있음, "종료"는 삭제라 주의). Elastic IP를 안 붙여놨기 때문에 **재시작 시 퍼블릭 IP가 바뀐다** — SSH config(§3-1)의 `HostName` 값도 다시 확인해서 갱신해야 한다.

---

## 9. 코드 변경 후 수동 재배포 (CI/CD 자동화 전까지)

`T-12`(CI/CD)로 이 과정을 자동화하기 전까지는, 코드를 고칠 때마다 이 순서를 손으로 반복한다.

```bash
ssh cinema
cd ~/cinema
git pull origin master
docker compose up -d --build
```

- **로컬에서 먼저 `master`에 merge + push까지 끝나 있어야 한다** — 서버는 `origin/master`에 실제로 올라온 커밋만 받아온다.
- **`--build`를 반드시 붙인다** — 안 붙이면 기존 이미지를 그대로 재사용해서 코드 변경이 반영 안 된다.
- 이번에도 빌드 도중 SSH가 끊기면 안 되므로 `tmux new -s deploy` 세션 안에서 실행하는 걸 권장한다 (§7 참고).
- 이 방식은 `app` 컨테이너를 잠깐 내렸다 새로 올리는 거라 **몇 초간 다운타임이 생긴다** — `T-12`의 nginx blue/green이 이 문제를 없애는 게 목표.
- **DB 스키마(`sql/*.sql`) 변경은 이 방법으로 반영되지 않는다** — 아래 §10 참고.

---

## 10. DB 스키마 변경 시 반영 방법 (실데이터 보존)

`sql/01~05-*.sql`의 init 스크립트는 MySQL 데이터 볼륨이 **처음 생성될 때 딱 한 번만** 실행된다. 이미 볼륨이 만들어진
뒤에 이 파일들을 고쳐서 재배포해도 반영되지 않는다. 스키마를 바꿔야 하는 상황에서 취할 수 있는 방법은 세 가지였고,
이 프로젝트는 **로컬에서 SSH 터널로 원격 MySQL에 직접 붙어 수동으로 `ALTER`를 실행하는 방식(B)**으로 결정했다
(데이터 보존 우선 — 완전 재초기화(`docker compose down -v`)는 쌓인 데이터가 다 날아가서 제외, Flyway/Liquibase 같은
정식 마이그레이션 툴 도입(C)은 지금 스코프 밖이라 보류, 실사용자 데이터가 쌓이는 `T-08` 시점에 재검토 가능).
상세 결정 기록 → `Todo.md`.

**절차** (3306을 인터넷에 안 열어놨으므로, SSH 터널로 그 포트를 로컬까지 끌어온다):

```powershell
ssh -L 3306:localhost:3306 cinema
```

이 명령어로 접속한 SSH 세션을 **터널 용도로 열어둔 채 그대로 둔다** (이 세션에서 다른 작업은 안 해도 됨 — 끊으면
터널도 끊김). 이제 로컬 PC에서 `localhost:3306`으로 접속하면 실제로는 원격 서버의 MySQL 컨테이너로 연결된다.

로컬에 MySQL 클라이언트가 있다면:
```bash
mysql -h 127.0.0.1 -P 3306 -u root -p
```
비밀번호는 서버의 `.env`에 있는 값 (`cat ~/cinema/.env`로 확인, §6 참고). 접속되면 필요한 스키마(`screening_db` 등)를
`USE`한 뒤 `ALTER TABLE` 등을 직접 실행한다.

> ⚠️ 아직 실제로 이 방식으로 스키마를 바꿔본 적은 없다 — 절차 자체는 정해뒀지만 실행 검증은 안 된 상태 (2026-07-26 기준).

---

## 요약 체크리스트

- [ ] EC2 인스턴스 생성 (Amazon Linux 2023, 프리티어)
- [ ] 보안 그룹: SSH(22, 내 IP만) / 8080(전체 공개) / 3306(안 엶)
- [ ] `.pem` 파일 권한 설정 (`icacls`, Authenticated Users/Users 그룹 제거)
- [ ] SSH 접속 확인 (`ec2-user`)
- [ ] Docker + Git 설치, `usermod`로 그룹 추가 후 재접속
- [ ] Docker Compose 플러그인 설치
- [ ] Docker Buildx 플러그인 설치
- [ ] `git clone -b master`
- [ ] `.env` 운영용 `DB_PASSWORD` 생성
- [ ] `tmux` 세션 안에서 `docker compose up -d --build`
- [ ] `docker compose ps`로 상태 확인
- [ ] 브라우저로 `http://<퍼블릭IP>:8080` 접속 확인
