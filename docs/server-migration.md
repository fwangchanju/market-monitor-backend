# 서버 이관 계획과 체크리스트

> 임시 문서다. 이관이 끝나고 안정화되면 `operations.md`를 새 구성으로 고쳐 쓰고 이 파일은 삭제한다.
> 결정 사항 중 오래 남길 것은 그때 `decisions.md`로 회수한다.

운영을 오라클 클라우드 서버 두 대에서 집에 있는 맥미니(M4, RAM 24GB, SSD 512GB) 한 대로 옮긴다.
맥미니는 운영 서버이면서 개발 환경이기도 하다. 비개발자가 러스트데스크로 맥미니에 붙어 에이전트에게
코드를 짜게 하고 로컬에서 화면을 확인한 뒤 배포까지 에이전트가 한다. 그래서 이 계획의 대부분은
"개발 쪽 에이전트가 실수로도 운영을 건드리지 못하게 하는 것"과 "그래도 사고가 나면 되돌릴 수 있게
하는 것"에 쓰인다.

## 진행 현황과 다음 작업 (2026-10-05 기준)

이어서 작업하는 세션은 이 절과 아래 체크리스트부터 본다. 운영 서버·운영 Docker·맥미니 `deploy` 영역은 에이전트가 직접
만지지 않는다. 사용자가 실행할 명령을 안내하고, 레포 변경은 PR로 한다.

- 1단계(도메인·터널), 2단계(이름 변경)는 끝났다. 남은 건 Google 브랜딩, 도메인 자동 연장 확인
- 3단계 코드는 배포 워크플로만 남았다. 맥미니 구성 파일과 게이트·백업 스크립트는 `infra/macmini/`에 있다
- 4단계 맥미니 기반은 계정 분리, Colima 부팅 기동, SSH, Tailscale 데몬, 에이전트 제한, GitHub 토큰까지 됐다.
  남은 건 `infra/macmini/README.md` 순서의 설치, Tailscale 태그·ACL, 외부 감시, 개발 환경 정리
- 5단계(키움 프록시)는 시작 전이다

다음 순서:

1. 서버 2 공인 IP가 예약(Reserved)인지 오라클 콘솔에서 확인, 아니면 전환 → 키움 허용 IP에 추가
2. 서버 2에 Tailscale(`tag:proxy`)과 tinyproxy 설치, Tailscale ACL과 태그(`tag:macmini`, `tag:proxy`, `tag:ci`) 적용, 맥미니에서 `curl -x`로 통로 확인
3. 배포 워크플로 PR: Actions가 Tailscale(`tag:ci`, 임시 노드)로 붙어 `ssh deploy@macmini "deploy <target> <tag>"`만 보낸다.
   `:deployed`/`:previous` 포인터 갱신과 원복은 지금 `release.yml`처럼 워크플로가 맡는다. 이관 기간에는 오라클 배포 경로도 남긴다.
   Tailscale OAuth 클라이언트와 CI용 SSH 키는 사용자가 만들어 시크릿에 넣는다
4. 맥미니 설치(`infra/macmini/README.md`), 앱은 띄우지 않는다
5. 이관 당일(6단계)

소유자가 웹에서 할 일: `Migration guard` 필수 체크 등록과 bypass 비우기, Google 브랜딩 수정, 가비아 자동 연장 확인.

---

## 목표 구성

### 계정

| 계정 | 종류 | 쓰임 |
|---|---|---|
| `chanju` | 일반 | 사람과 에이전트가 모두 여기서 작업한다. 러스트데스크 세션도 이 계정이다 |
| `deploy` | 일반 | 운영 컨테이너, 운영 DB 볼륨, 환경변수, 백업을 소유한다. GitHub Actions가 SSH로 들어오는 계정 |
| `admin` | 관리자 | 비밀번호는 소유자만 안다. 로그인하지 않고 `su - admin`이나 인증 창에만 쓴다 |

`chanju`를 관리자로 두고 sudo 비밀번호로만 막는 안도 검토했다. 에이전트가 인증 창을 띄우면 비개발자가
비밀번호를 넣을 수 있어서 접었다. 경계는 사람의 행동이 아니라 OS 권한이어야 한다.

`deploy` 홈은 `700`이다. `chanju`에서는 운영 파일을 읽을 수도 운영 Docker에 붙을 수도 없다.

### 컨테이너 런타임

운영은 `deploy` 계정의 Colima(vz, CPU 4, 메모리 6GB, 디스크 100GB)로 돌리고 부팅 시 LaunchDaemon으로
뜨게 한다. 아무도 로그인하지 않아도 운영이 올라와야 해서다. OrbStack은 GUI 로그인이 필요하고 여러 계정이
동시에 쓸 수 없어 운영에는 못 쓴다.

개발은 `chanju` 계정의 OrbStack(메모리 상한 8GB)이다. 쓰지 않는 메모리를 macOS에 돌려줘서 개발용으로는
이쪽이 낫다.

운영 Colima 안에는 PostgreSQL, 애플리케이션, nginx, 렌더러, cloudflared가 같은 네트워크로 뜬다.
호스트에 여는 포트는 없다. 개발 쪽 포트(DB `15432`, 백엔드 `18081`, 프론트 `5173`)와 부딪힐 일이 없다.

#### 맥미니에 실제로 구성한 것 (2026-10-04)

`deploy`는 개발 계정 소유인 Homebrew(`/opt/homebrew`)를 쓰지 않는다. 개발 계정이 그 실행 파일을 바꿔치기하면
`deploy` 권한으로 실행되기 때문이다. 그래서 Colima, Lima, Docker CLI, Docker Compose를 `deploy` 홈에 직접 받았다.

```
/Users/deploy/                       (700)
├─ Optional/bin/                     colima, docker
├─ Optional/lima/                    Lima 배포본(bin, libexec, share)
├─ Optional/dl/                      내려받기 임시
├─ .docker/cli-plugins/              docker-compose
├─ Projects/marketry/marketry-backend/   레포(읽기 전용 Deploy key로 clone)
├─ env/                              비밀값
├─ backups/                          운영 DB 백업
└─ Library/Logs/colima.log           Colima 로그
```

- 내려받기는 `/tmp`가 아니라 `deploy` 홈 안에서 한다. `/tmp`는 다른 계정도 쓰는 곳이라 같은 이름 파일을 미리 만들어 둘 수 있다
- `~/.zshenv`에 `PATH`(`~/Optional/bin`, `~/Optional/lima/bin`, 시스템 경로만)와
  `DOCKER_HOST=unix:///Users/deploy/.colima/default/docker.sock`을 둔다. `.zprofile`은 SSH로 명령만 보낼 때 읽히지 않는다.
  `DOCKER_HOST`가 없으면 `docker`가 `/var/run/docker.sock`(개발 계정의 OrbStack)으로 붙으려 한다
- git은 `/usr/bin/git`을 쓴다
- Colima 프로필은 `default`(vz, CPU 4, 메모리 6GB, 디스크 100GB). 설정은 `~/.colima/default/colima.yaml`.
  CPU와 메모리는 다시 띄우면 바꿀 수 있고 디스크는 늘리기만 된다
- 부팅 기동: `/Library/LaunchDaemons/kr.co.marketry.colima.plist`(root:wheel, 644). `UserName deploy`,
  `colima start --foreground`, `RunAtLoad`, `KeepAlive`, `HOME`과 `PATH`를 직접 지정, 로그는 `~/Library/Logs/colima.log`.
  등록은 `sudo launchctl bootstrap system <plist>`
- 개발 계정 자동 로그인을 켠 채 재부팅해 LaunchDaemon이 Colima를 띄우는 것을 확인했다. 원격 접속(RustDesk)이 개발 계정
  로그인에 기대고 있어 자동 로그인은 끄지 않는다

#### 맥미니 관리 접속과 에이전트 제한 (2026-10-05)

- **SSH:** `/etc/ssh/sshd_config.d/010-marketry.conf`에 `PasswordAuthentication no`, `KbdInteractiveAuthentication no`,
  `PermitRootLogin no`. macOS 기본 파일(`100-macos.conf`)보다 먼저 읽히게 `010`으로 시작한다. 원격 로그인 허용 계정은
  `admin`, `deploy`. 원격 사용자 디스크 전체 접근은 끈다. `admin`은 소유자 PC의 키로만 들어온다
- **Tailscale:** 오픈소스 `tailscaled`만 로그인 전에 뜬다(App Store판과 Standalone판은 로그인 필요). `admin` 홈에 공식 Go를 받아
  `go install tailscale.com/cmd/tailscale{,d}@latest`로 빌드하고 `sudo tailscaled install-system-daemon`으로 설치했다.
  root로 도는 프로그램이라 개발 계정 소유 Homebrew의 Go나 tailscale을 쓰지 않는다. CLI도 `/usr/local/bin`에 root 소유로 둔다.
  기기 이름 `macmini`, 키 만료 끔. 맥미니 쪽 MagicDNS는 쓰지 않는다(프록시는 IP로 지정)
- **원격 화면:** RustDesk는 개발 계정 세션에서 쓴다. 설치 때 깔린 root 서비스(`com.carriez.RustDesk_service`)가 개발 계정 소유
  앱 파일을 root로 실행하는 구조라 `/Library/LaunchDaemons.disabled/`로 옮겨 껐다. 앱 파일 소유자 변경은 macOS 앱 관리 보호에
  막힌다. 로그인 화면에서의 원격 접속은 잃는데, 개발 계정 자동 로그인을 끄지 않는 한 문제없다. Tailscale IP로 직접 접속하려면
  RustDesk의 직접 IP 접속을 켠다
- **`admin`은 화면에 로그인하지 않는다.** `/Library/LaunchAgents`의 사용자 앱이 `admin` 권한으로 같이 뜨기 때문이다.
  터미널 작업은 SSH로, 화면 설정은 개발 계정 화면에서 관리자 인증 창으로 한다
- **에이전트 제한:** `/Library/Application Support/ClaudeCode/managed-settings.json`(root 소유)에 `sudo`, `su`, `login`, `dscl`,
  `dseditgroup`, `ssh`, `scp`, `sftp`, `osascript` 실행과 `deploy`·`admin` 홈 읽기를 deny로 두고 `disableBypassPermissionsMode`를 건다.
  Codex는 `/etc/codex/requirements.toml`에서 승인 정책을 `untrusted`, `on-request`로, 샌드박스를 `read-only`, `workspace-write`로 제한한다
- **에이전트 공통 지침(root 소유):** Claude Code는 관리형 `CLAUDE.md`(`/Library/Application Support/ClaudeCode/CLAUDE.md`), Codex는
  `/etc/codex/managed_config.toml`의 `developer_instructions`에 둔다. 내용은 "`.github/workflows/`는 고치지 않는다, 푸시가 workflow 권한으로
  거부되면 우회하지 말고 멈춘 뒤 사용자에게 개발자 확인이 필요하다고 알린다" 두 가지다. Claude Code 관리형 설정에는 워크플로 파일
  편집·쓰기, `gh auth` 로그인·전환·토큰 출력, `git remote set-url`/`add`도 deny로 더했다
- **GitHub 인증:** 개발 계정은 fine-grained 토큰(두 레포, Actions·Contents·Pull requests 쓰기, Commit statuses 읽기, 만료 없음)을
  `gh`에 넣고 `gh auth setup-git`으로 git도 쓰게 했다. 원격 주소는 HTTPS다. SSH 키는 권한 범위를 좁힐 수 없어서 맥미니 키를
  GitHub 계정에서 지웠다. 워크플로 파일 수정이 담긴 푸시는 GitHub가 거부한다

컨테이너별 메모리 제한은 렌더러에만 1GB로 건다. 6GB가 차면 리눅스가 아무 프로세스나 종료할 수 있는데,
메모리가 불어날 만한 건 Chromium을 띄우는 렌더러뿐이다. 제한이 있으면 넘쳤을 때 렌더러만 죽고 재시작한다.
지금 서버에서 평일 캡처를 거친 뒤 잰 최댓값(`memory.peak`)이 약 558MB였고 그 1.5~2배로 잡았다.
DB와 애플리케이션에는 걸지 않는다. 제한에 걸려 DB나 수집 중인 앱이 죽는 쪽이 더 나쁘다.

### 인바운드

새 도메인을 사서 Cloudflare DNS에 올리고 Cloudflare Tunnel로 받는다. 공유기 포트포워딩, DuckDNS IP 갱신,
Let's Encrypt는 쓰지 않는다. 맥미니가 바깥으로 연결을 여는 구조라 유동 IP 변경, 통신사 변경, 정전 후
재부팅, CGNAT의 영향을 받지 않는다. 집 IP도 드러나지 않는다.

포트포워딩과 DuckDNS를 쓰는 안도 검토했다. 집 IP가 공개되고, 공격을 받으면 집 인터넷 전체가 멈추고,
통신사 포트 차단과 NAT 루프백을 따로 확인해야 해서 접었다.

터널은 이관보다 먼저 지금 서버에 붙여 도메인 전환을 끝낸다. 그러면 이관 당일의 전환은 cloudflared를
어느 쪽에서 띄우느냐만 바꾸면 된다. 도메인 변경과 서버 변경이 한날에 겹치지 않는다.

렌더러는 nginx 컨테이너에 서비스 도메인을 네트워크 별칭으로 붙여 내부로 바로 접근한다. `CAPTURE_URL`은
`infra/renderer-docker-compose.yml`에 있으니 맥미니용 compose를 만들 때 내부 접근에 맞는지 같이 확인한다.

### 아웃바운드(키움)

키움 REST API는 허용 IP로만 호출된다. 맥미니는 유동 IP라서 키움 호출만 지금 렌더러가 도는 오라클 서버 2를
거친다. 서버 2에 tinyproxy를 두고 tailnet 주소에서만 받으며 `api.kiwoom.com:443`만 통과시킨다.

애플리케이션은 `kiwoomRestClient` 빈에만 프록시를 건다(`KIWOOM_PROXY_HOST`, `KIWOOM_PROXY_PORT`).
값이 비면 지금처럼 직접 연결하므로 이관 전에 배포해도 영향이 없다. 토큰 발급과 API 호출이 모두 이 빈을
쓴다.

서버 1이 아니라 서버 2를 프록시로 쓰는 건 이관 중 문제가 생기면 서버 1을 그대로 다시 올리기 위해서다.
오라클 유휴 회수는 수년간 겪지 않아 따로 대비하지 않는다. 회수되면 종량제로 바꾸거나 다른 곳의 최소
사양 서버에 tinyproxy와 Tailscale을 올리고 키움 허용 IP와 `KIWOOM_PROXY_HOST`만 바꾼다.

### Tailscale

| 기기 | 태그 | 허용 |
|---|---|---|
| 소유자 노트북 | 없음(소유자 계정) | 모든 기기 |
| 맥미니 | `tag:macmini` | `tag:proxy`의 8888만 |
| 오라클 서버 2 | `tag:proxy` | 없음 |
| GitHub Actions | `tag:ci` (임시 노드) | `tag:macmini`의 22만 |

Tailscale SSH는 켜지 않는다. 기기 인증만으로 SSH가 열리면 계정 분리가 무의미해진다. 서버 기기는
태그를 달아 키 만료를 끈다. 맥미니는 App Store 앱 대신 `tailscaled`를 부팅 데몬으로 설치한다.
로그인 없이 연결되고 `chanju`가 끌 수 없다.

### 배포

GitHub Actions가 배포할 때만 tailnet에 붙어 `deploy@맥미니`로 SSH 접속한다. 에이전트가 "배포해"
한마디로 병합과 배포를 하는 구조라 사람 승인 단계는 두지 않고 사고 뒤 되돌리는 장치를 둔다.

- `deploy`의 `authorized_keys`에 강제 명령을 걸어 SSH 접속이 항상 `deploy` 홈의 게이트 스크립트를
  거치게 한다. 게이트는 정해진 동작(배포, 헬스체크, 롤백)만 받고, 배포 동작이면 먼저 `pg_dump`를 뜬 뒤
  레포의 배포 스크립트를 실행한다. 덤프는 최근 5개만 남긴다. 게이트는 레포 밖에 있어서 에이전트가 배포
  절차를 바꿔도 직전 DB는 남는다
- 강제 명령을 쓰면 지금처럼 워크플로가 임의 스크립트를 SSH로 보낼 수 없다. 워크플로는 동작 이름과
  이미지 태그만 넘기도록 바꾼다
- 새 마이그레이션 SQL에 `DROP`, `TRUNCATE`, `DELETE`, 컬럼 삭제가 있으면 CI가 실패한다. 라벨 같은
  우회 수단은 두지 않고 브랜치 규칙의 bypass도 비운다. 파괴적 변경은 소유자가 `admin`이나 `deploy`로
  직접 DB에서 한다
- 맥미니의 GitHub 인증은 fine-grained PAT로 바꾼다. 두 레포에 Contents, Pull requests, Actions 쓰기만
  주고 Workflows, Administration, Secrets는 주지 않는다. 워크플로 파일 수정, 브랜치 규칙 변경, 시크릿
  접근이 막힌다

인프라 파일 변경 PR을 CI로 막는 안과 배포 알림도 검토했다. 인프라를 바꿀 때마다 소유자가 규칙을
껐다 켜야 해서 번거로움에 비해 얻는 게 적다고 보고 접었다.

에이전트가 계정 전환을 시도하지 않게 Claude Code 관리형 설정
(`/Library/Application Support/ClaudeCode/managed-settings.json`)에 `sudo`, `su`, `login`, `dscl`,
`ssh deploy@*` 등을 deny로 건다. Codex는 `/etc/codex/requirements.toml`로 샌드박스와 승인 정책을
강제한다. 둘 다 root 소유라 `chanju`가 고칠 수 없다. 실제 경계는 OS 권한이고 이건 한 겹 더 두는 장치다.

### 가용성과 백업

시스템 잠자기를 끄고(`pmset -a sleep 0`) 정전 후 자동 부팅을 켠다(`pmset -a autorestart 1`).
FileVault는 끈다. 켜져 있으면 재부팅 뒤 잠금 해제 화면에서 멈춰 운영, Tailscale, 터널이 모두 안 뜬다.
`chanju`로 자동 로그인하고, macOS 업데이트는 자동 다운로드만 하고 주말에 손으로 설치한다.

맥미니나 집 인터넷이 죽으면 앱의 텔레그램 알림도 같이 죽는다. 그래서 외부 감시 서비스(UptimeRobot 등)가
5분마다 헬스체크를 부르게 한다.

`deploy`의 예약 작업이 매일 04:30에 `pg_dump`를 뜬다. 맥미니에 7일, Cloudflare R2에 30일을 남긴다.
보관 일수는 이관 때 DB 크기를 보고 다시 정한다.

### 로컬 개발

DB만 OrbStack 컨테이너로 띄우고 백엔드와 프론트는 손으로 띄운다. 백엔드는 `local` 프로파일에
`auth.dev-login.enabled=true`, `scheduling.enabled=false`로 뜬다. 텔레그램과 키움 키는 개발 환경에 두지
않는다.

`deploy`가 매일 백업 직후 개발용 스냅샷을 만들어 `/Users/Shared/marketry-dev-snapshot/`에 둔다. `chanju`는
읽기만 한다. 스냅샷은 `users` 이메일을 가리고 `user_refresh_token`은 `--exclude-table-data`로 행만 뺀다.
테이블까지 빼면 엔티티 검증과 dev-login이 깨진다. `users`를 통째로 빼는 안은 커스텀 테이블의 외래키가
깨져서 접었다.

`chanju`의 예약 작업이 05:00에 로컬 DB를 지우고 스냅샷으로 다시 만든다. 같은 일을 하는 수동 명령도 둔다.
브랜치에만 있는 마이그레이션은 다음 `bootRun` 때 Flyway가 적용한다.

### 이름

이관하면서 프로젝트 이름을 `marketry`로 통일한다. 서버 경로, 컨테이너, compose, DB, 이미지는 어차피 맥미니에서
새로 만들기 때문에 처음부터 새 이름으로 만들면 추가 비용이 거의 없다. 이관을 끝낸 뒤에 바꾸면 방금 만든 것을
운영 중에 다시 고쳐야 해서 더 번거롭다.

| 대상 | 지금 | 바꿀 이름 |
|---|---|---|
| 레포 | `market-monitor-backend`, `market-monitor-frontend` | `marketry-backend`, `marketry-frontend` |
| 이미지 | `market-monitor`, `-nginx`, `-renderer`, `-assets` | `marketry`, `marketry-nginx`, `marketry-renderer`, `marketry-assets` |
| 컨테이너 | `market-monitor-*` | `marketry-*` |
| DB | `market_monitor_db` | `marketry_db` |
| Docker 네트워크 | `proxy` | `marketry-network` (키움 프록시와 헷갈리지 않게) |
| Java 패키지 | `dev.eolmae.marketmonitor` | `dev.eolmae.marketry` |
| 설정 접두사 | `market-monitor.*` | `marketry.*` |

레포 이름을 바꾸면 GitHub가 옛 주소를 새 주소로 연결해 줘서 기존 clone과 서버의 `git fetch`는 깨지지 않는다.
프론트 배포 워크플로가 백엔드 레포 이름으로 nginx 재빌드를 부르는 곳(`repository: ...`)만 직접 고친다.

오라클 쪽은 옛 이름 그대로 둔다. 이관을 되돌릴 때 오라클에 남은 옛 이미지와 컨테이너를 그대로 다시 올린다.

---

## 체크리스트

### 1. 도메인과 터널 (지금 서버에서 먼저)

- [x] 도메인 구매. `.kr` 계열은 국내 업체에서 사고 네임서버를 Cloudflare로 바꾼다. DNSSEC는 바꾸기 전에 끈다
- [x] Cloudflare Tunnel 생성, 지금 서버에 cloudflared 기동
- [x] Google Auth Platform 리디렉션 URI를 새 도메인으로
- [ ] Google Auth Platform 브랜딩(앱 이름, 홈페이지, 개인정보처리방침, 승인된 도메인)을 새 도메인으로
- [x] `market-monitor.base-url` 변경 후 배포
- [x] 옛 DuckDNS 주소에서 새 도메인으로 보내는 리디렉트
- [x] 새 도메인으로 로그인, 화면 확인. 텔레그램 캡처는 이관 뒤 6단계에서 확인한다
- [ ] 도메인 자동 연장 확인(가비아)

### 2. 이름 변경 (맥미니 설정 전에)

- [x] GitHub에서 두 레포 이름 변경
- [x] 프론트 배포 워크플로의 백엔드 레포 참조 수정
- [x] Java 패키지와 설정 접두사 변경. 동작이 바뀌지 않는 기계적 변경이라 별도 PR로 한다
- [x] 문서, `CLAUDE.md`, `AGENTS.md`, `spring.application.name`, 프론트 `package.json`, 이미지 라벨의 이름 참조
- [ ] 이미지, 컨테이너, DB, 서버 경로 이름은 3단계 코드 변경에서 맥미니용으로 새로 만들 때 반영한다

### 3. 코드 변경 (이관 전에 병합하고 지금 서버에 배포해 둔다)

- [x] 애플리케이션 이미지 amd64/arm64 멀티 빌드. 빌드 스테이지에 `--platform=$BUILDPLATFORM`
- [x] nginx 이미지, 렌더러 이미지 멀티 빌드. 프론트 assets는 정적 파일이라 nginx 빌드에서 빌드 머신 플랫폼으로 받으므로
      멀티 빌드가 필요 없다. 최종 스테이지에 `RUN`이 없어 QEMU도 쓰지 않는다
- [x] `kiwoomRestClient` 프록시 설정(`KIWOOM_PROXY_HOST`, `KIWOOM_PROXY_PORT`. 값이 비면 직접 연결)
- [x] 파괴적 마이그레이션 CI 검사(`ci.yml`의 `Migration guard` job). 로직은 PR에서 고칠 수 없게 워크플로 안에 둔다
- [ ] GitHub 브랜치 규칙에 `Migration guard`를 필수 체크로 등록하고 bypass 비움(소유자가 웹에서)
- [x] 맥미니 `marketry-network` 서브넷 고정 생성 스크립트(`infra/macmini/setup-network.sh`, `172.30.0.0/24`)
- [x] 맥미니용 compose(`infra/macmini/compose.yml`, `nginx.conf`, `env.template`): cloudflared, 호스트 포트 없음, 렌더러 같은 네트워크·메모리 1GB,
      nginx 별칭 `marketry.co.kr`로 렌더러 내부 접근. 이미지 이름은 이관 뒤 정리 때 바꾼다
- [ ] 배포 워크플로: Tailscale 액션(`tag:ci`, 임시 노드), 대상 호스트를 맥미니로, SSH는 동작 이름과 태그만 전달
- [x] 게이트 스크립트, 일일 백업, 개발 스냅샷, 로컬 DB 교체 스크립트의 원본을 레포에 둔다(`infra/macmini/`, `infra/local/restore-snapshot.sh`. 설치는 손으로)

### 4. 맥미니 기반

- [x] FileVault 끄기(복호화에 시간이 걸린다)
- [x] `admin` 생성 후 개발 계정을 일반 계정으로 내림. Homebrew는 개발 계정 소유로 두고 `deploy`는 쓰지 않는다
- [x] `deploy` 생성, 홈 `700`
- [x] `pmset` 잠자기 끔, 정전 후 자동 부팅, 개발 계정 자동 로그인, macOS 업데이트 자동 설치 끔
- [x] `deploy`에 Colima 설치(Homebrew 없이 `~/Optional`), LaunchDaemon 등록
- [x] `deploy` 레포 clone(읽기 전용 Deploy key)
- [x] 재부팅 뒤 LaunchDaemon이 `deploy`의 Colima를 띄우는지 확인(개발 계정 자동 로그인은 켠 채로)
- [x] `tailscaled` 데몬 설치(기존 Standalone 앱과 네트워크 확장 제거), 기기 이름 `macmini`, 키 만료 끔
- [ ] Tailscale `tag:macmini` 지정과 ACL 적용(5단계와 함께)
- [x] 원격 로그인은 `admin`, `deploy`만 허용. 비밀번호·root 로그인 끔, `admin`은 소유자 PC 키로 접속
- [x] `marketry-network` 만들기 전에 `172.30.0.0/24`가 Colima VM 경로·Docker 기본 브리지·집 공유기·Tailscale 대역과 겹치지 않는지 확인.
      겹치면 `setup-network.sh`와 `infra/macmini/nginx.conf`의 값을 함께 바꾼다
- [ ] `infra/macmini/README.md` 순서로 설치: 네트워크 생성, env 파일, GHCR 로그인, 게이트·백업 설치(`~/Optional/bin`),
      `deploy`의 `authorized_keys`에 CI 키를 강제 명령으로 등록, 백업 LaunchDaemon. **앱은 이관 당일 전까지 띄우지 않는다**(수집·텔레그램 중복)
- [ ] 일일 백업 예약 작업, R2 업로드, 개발 스냅샷, 공유 폴더 권한
- [ ] 외부 감시 등록
- [x] Claude Code 관리형 설정, Codex `requirements.toml`
- [x] GitHub 인증을 fine-grained PAT로 교체(`gh`와 git 자격증명 모두), 원격 주소 HTTPS, 맥미니 SSH 키는 GitHub에서 삭제
- [x] 개발 계정 소유 앱이 root 서비스로 도는 구멍 점검(RustDesk root 서비스 끔)
- [ ] 개발 환경: OrbStack, JDK 21, Node, 개발 DB, 로컬 실행 스크립트, 05:00 로컬 DB 교체 작업

### 5. 키움 프록시 (오라클 서버 2)

- [ ] 서버 2 공인 IP가 예약 IP인지 확인, 아니면 전환
- [ ] 서버 2 IP를 키움 허용 IP에 추가. 서버 1 IP는 이관이 끝날 때까지 둔다
- [ ] tinyproxy 설치: tailnet 주소에서만 listen, `api.kiwoom.com:443`만 허용
- [ ] Tailscale 설치, `tag:proxy`
- [ ] Tailscale ACL 적용(위 표)
- [ ] 4단계에서 맥미니 Tailscale을 정리할 때 같이 한다. 맥미니에서 `curl -x http://<서버 2 tailnet 주소>:8888 https://api.kiwoom.com`으로 통로만 먼저 확인한다
- [ ] 프록시를 거친 키움 토큰 발급은 개발 환경에 키움 키가 없어서 이관 당일 맥미니 운영 앱으로 확인한다(6단계)

### 6. 이관 당일 (주말)

금요일 장 마감 뒤부터 일요일 사이에 한다. 월요일 장 시작 전까지 못 고치면 되돌린다.

- [ ] 지금 서버 애플리케이션 중지
- [ ] DB 덤프, 맥미니 운영 DB에 적재. 원본은 `market_monitor_db`(사용자 `market_monitor`), 대상은 `marketry_db`(사용자 `marketry`)라
      `pg_dump -Fc` 후 `pg_restore --no-owner --role=marketry`로 넣는다. postgres와 cloudflared는 게이트가 아니라 손으로 처음 `up -d` 한다
- [ ] 맥미니 운영 기동(`KIWOOM_PROXY_HOST` 설정)
- [ ] 지금 서버 cloudflared 중지, 맥미니 cloudflared 기동
- [ ] 터널 경로의 서비스 주소를 맥미니 nginx 컨테이너 이름(`marketry-nginx:80`)으로 바꾼다. 되돌릴 때는 원래 값으로
- [ ] 웹 화면과 로그인 확인
- [ ] 텔레그램 캡처 수동 발송 확인. 실제 채팅방으로 나가니 필요하면 잠시 개발자 채팅방으로 돌린다
- [ ] 키움 토큰 발급이 프록시를 거쳐 되는지 로그로 확인
- [ ] 스케줄러와 수집 확인
- [ ] GitHub Actions로 맥미니에 배포 한 번 돌려 게이트, 덤프, 헬스체크 확인

### 7. 되돌리기

지금 서버는 컨테이너를 내리기만 하고 지우지 않는다. 되돌릴 때는 맥미니 cloudflared를 내리고 지금 서버의
애플리케이션과 cloudflared를 다시 올린다. 이관 뒤 맥미니에 쌓인 데이터는 버린다. 주말이라 거의 없다.

### 8. 안정화 뒤 정리

- [ ] 며칠 운영해 본 뒤 오라클 서버 1 정리
- [ ] 키움 허용 IP에서 서버 1 제거
- [ ] DuckDNS 리디렉트 종료 시점 결정
- [ ] 보관 일수 재조정(DB 크기 기준). 백업 로그로 실제 소요 시간을 보고 04:30 백업과 05:00 개발 DB 복원 간격이 충분한지 확인.
      겹쳐도 스냅샷은 임시 파일에 쓴 뒤 이름을 바꾸므로 복원은 전날 스냅샷을 읽을 뿐 깨지지 않는다
- [ ] nginx `set_real_ip_from`을 맥미니 `marketry-network` 네트워크 대역 하나로 좁힌다. 지금은 대역을 몰라 사설 대역 셋을 다 열어 두었다
- [ ] `operations.md`를 새 구성으로 고쳐 쓰고 이 파일 삭제
- [ ] 오라클 서버 env 파일에서 `OWNER_USER_ID`, `RENDERER_OWNER_CAPTURE_ENABLED`, `CAPTURE_URL` 삭제. 텔레그램 캡처를 확인하기 전까지는
      되돌리기용으로 남겨 둔다(이전 이미지는 이 값을 env에서 읽는다). 맥미니 env에는 처음부터 넣지 않는다
- [ ] 레포에 남은 옛 구성 정리: 오라클용 compose·배포 스크립트·워크플로 단계, `~/repo/market-monitor-backend` 경로,
      DuckDNS·Let's Encrypt nginx 블록과 인증서 설정, 렌더러 3000 포트 공개, `market-monitor-*` 컨테이너·DB 이름 기본값,
      `CLAUDE.md`와 `.claude/settings.json`의 옛 로컬 절대 경로
- [ ] 이미지 빌드에서 `linux/amd64` 제거 여부 결정. 맥미니 장애 때 클라우드 서버로 급히 옮길 여지를 남기려면 둔다.
      빌드 머신 플랫폼에서 빌드하는 구조(`--platform=$BUILDPLATFORM`)는 Actions 러너가 amd64라 그대로 둔다
- [ ] GitHub 계정 SSH keys에서 오라클 서버 키 삭제(계정 전체 쓰기 권한이 있는 키)
- [ ] 문서·지시서에 남은 실명 흔적 정리: 개발 계정 이름, 컴퓨터 이름이 찍힌 출력. 이후 글에서는 "개발 계정"으로 쓴다.
      GitHub 사용자 이름이 들어간 레포·이미지 주소는 바꿀 수 없으니 그대로 둔다
- [ ] 결정 사항을 `decisions.md`로 회수

---

## 열어둔 것

- 개발용 스냅샷은 이메일, Google `sub`, 닉네임, 프로필 이미지만 가린다. 사용자가 입력한 설정값(섹터 이름, `user_preference.payload`)은
  그대로 둔다. 개인 정보를 적을 수 있는 칸인지 소유자가 판단한다
- Codex는 명령 단위 금지가 없다. 샌드박스와 승인 정책(`requirements.toml`), 지침(`managed_config.toml`의 `developer_instructions`)으로 대신한다.
  지침이 적용되는 것은 확인했다
- 키움이 같은 앱키로 여러 곳에서 토큰을 받을 때 앞선 토큰을 무효화하는지는 모른다. 이관 방식이 지금
  서버를 먼저 내리는 쪽이라 당일에는 문제가 되지 않는다
