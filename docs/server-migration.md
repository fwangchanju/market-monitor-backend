# 서버 이관 계획과 체크리스트

> 임시 문서다. 이관이 끝나고 안정화되면 `operations.md`를 새 구성으로 고쳐 쓰고 이 파일은 삭제한다.
> 결정 사항 중 오래 남길 것은 그때 `decisions.md`로 회수한다.

운영을 오라클 클라우드 서버 두 대에서 집에 있는 맥미니(M4, RAM 24GB, SSD 512GB) 한 대로 옮긴다.
맥미니는 운영 서버이면서 개발 환경이기도 하다. 비개발자가 러스트데스크로 맥미니에 붙어 에이전트에게
코드를 짜게 하고 로컬에서 화면을 확인한 뒤 배포까지 에이전트가 한다. 그래서 이 계획의 대부분은
"개발 쪽 에이전트가 실수로도 운영을 건드리지 못하게 하는 것"과 "그래도 사고가 나면 되돌릴 수 있게
하는 것"에 쓰인다.

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
바꾸지 않는다.

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
| Docker 네트워크 | `proxy` | `marketry-net` (키움 프록시와 헷갈리지 않게) |
| Java 패키지 | `dev.eolmae.marketmonitor` | `dev.eolmae.marketry` |
| 설정 접두사 | `market-monitor.*` | `marketry.*` |

레포 이름을 바꾸면 GitHub가 옛 주소를 새 주소로 연결해 줘서 기존 clone과 서버의 `git fetch`는 깨지지 않는다.
프론트 배포 워크플로가 백엔드 레포 이름으로 nginx 재빌드를 부르는 곳(`repository: ...`)만 직접 고친다.

오라클 쪽은 옛 이름 그대로 둔다. 이관을 되돌릴 때 오라클에 남은 옛 이미지와 컨테이너를 그대로 다시 올린다.

---

## 체크리스트

### 1. 도메인과 터널 (지금 서버에서 먼저)

- [ ] 도메인 구매. `.kr` 계열은 국내 업체에서 사고 네임서버를 Cloudflare로 바꾼다. DNSSEC는 바꾸기 전에 끈다
- [ ] Cloudflare Tunnel 생성, 지금 서버에 cloudflared 기동
- [ ] Google Auth Platform의 리디렉션 URI와 브랜딩 링크를 새 도메인으로
- [ ] `market-monitor.base-url` 변경 후 배포
- [ ] 옛 DuckDNS 주소에서 새 도메인으로 보내는 리디렉트
- [ ] 새 도메인으로 로그인, 화면, 텔레그램 캡처 확인

### 2. 이름 변경 (맥미니 설정 전에)

- [ ] GitHub에서 두 레포 이름 변경
- [ ] 프론트 배포 워크플로의 백엔드 레포 참조 수정
- [ ] Java 패키지와 설정 접두사 변경. 동작이 바뀌지 않는 기계적 변경이라 별도 PR로 한다
- [ ] 문서, `CLAUDE.md`, `AGENTS.md`, `spring.application.name`, 프론트 `package.json`, 이미지 라벨의 이름 참조
- [ ] 이미지, 컨테이너, DB, 서버 경로 이름은 3단계 코드 변경에서 맥미니용으로 새로 만들 때 반영한다

### 3. 코드 변경 (이관 전에 병합하고 지금 서버에 배포해 둔다)

- [ ] 애플리케이션 이미지 amd64/arm64 멀티 빌드. 빌드 스테이지에 `--platform=$BUILDPLATFORM`
- [ ] 프론트 assets 이미지, nginx 이미지, 렌더러 이미지 멀티 빌드
- [ ] `kiwoomRestClient` 프록시 설정(값이 비면 직접 연결)
- [ ] 파괴적 마이그레이션 CI 검사, 필수 체크로 등록하고 bypass 비움
- [ ] 맥미니 `marketry-net` 네트워크는 서브넷을 지정해서 만든다(`docker network create --subnet ...`). 다시 만들어도 대역이 바뀌지 않게 한다
- [ ] 맥미니용 compose: cloudflared 추가, 호스트 포트 제거, 렌더러 같은 네트워크, nginx 네트워크 별칭, 렌더러 메모리 제한 1GB
- [ ] 배포 워크플로: Tailscale 액션(`tag:ci`, 임시 노드), 대상 호스트를 맥미니로, SSH는 동작 이름과 태그만 전달
- [ ] 게이트 스크립트, 일일 백업, 개발 스냅샷, 로컬 DB 교체 스크립트의 원본을 레포에 둔다(설치는 손으로)

### 4. 맥미니 기반

- [ ] FileVault 끄기(복호화에 시간이 걸린다)
- [ ] `admin` 생성 후 `chanju`를 일반 계정으로 내림. Homebrew 소유권 정리
- [ ] `deploy` 생성, 홈 `700`
- [ ] `pmset` 잠자기 끔, 정전 후 자동 부팅, `chanju` 자동 로그인, 업데이트 자동 설치 끔
- [ ] `deploy`에 Colima 설치, LaunchDaemon 등록
- [ ] 아무도 로그인하지 않은 채 재부팅하고 `ssh deploy@맥미니 docker ps`가 되는지 확인.
      안 되면 운영을 UTM 같은 리눅스 VM에 넣고 `deploy`가 소유하게 한다
- [ ] `tailscaled` 데몬 설치(App Store 앱 제거), `tag:macmini`
- [ ] 원격 로그인은 `admin`, `deploy`만 허용. `deploy`는 키 로그인만, 강제 명령 설정
- [ ] 게이트 스크립트 설치, 배포 SSH 키 등록
- [ ] 일일 백업 예약 작업, R2 업로드, 개발 스냅샷, 공유 폴더 권한
- [ ] 외부 감시 등록
- [ ] Claude Code 관리형 설정, Codex `requirements.toml`
- [ ] GitHub 인증을 fine-grained PAT로 교체(`gh`와 git 자격증명 모두)
- [ ] 개발 환경: OrbStack, JDK 21, Node, 개발 DB, 로컬 실행 스크립트, 05:00 로컬 DB 교체 작업

### 5. 키움 프록시 (오라클 서버 2)

- [ ] 서버 2 공인 IP가 예약 IP인지 확인, 아니면 전환
- [ ] 서버 2 IP를 키움 허용 IP에 추가. 서버 1 IP는 이관이 끝날 때까지 둔다
- [ ] tinyproxy 설치: tailnet 주소에서만 listen, `api.kiwoom.com:443`만 허용
- [ ] Tailscale 설치, `tag:proxy`
- [ ] Tailscale ACL 적용(위 표)
- [ ] 맥미니에서 프록시를 거쳐 키움 토큰 발급 확인

### 6. 이관 당일 (주말)

금요일 장 마감 뒤부터 일요일 사이에 한다. 월요일 장 시작 전까지 못 고치면 되돌린다.

- [ ] 지금 서버 애플리케이션 중지
- [ ] DB 덤프, 맥미니 운영 DB에 적재
- [ ] 맥미니 운영 기동(`KIWOOM_PROXY_HOST` 설정)
- [ ] 지금 서버 cloudflared 중지, 맥미니 cloudflared 기동
- [ ] 터널 경로의 서비스 주소를 맥미니 nginx 컨테이너 이름(`marketry-nginx:80`)으로 바꾼다. 되돌릴 때는 원래 값으로
- [ ] 웹 화면과 로그인 확인
- [ ] 텔레그램 캡처 수동 발송 확인. 실제 채팅방으로 나가니 필요하면 잠시 개발자 채팅방으로 돌린다
- [ ] 스케줄러와 수집 확인
- [ ] GitHub Actions로 맥미니에 배포 한 번 돌려 게이트, 덤프, 헬스체크 확인

### 7. 되돌리기

지금 서버는 컨테이너를 내리기만 하고 지우지 않는다. 되돌릴 때는 맥미니 cloudflared를 내리고 지금 서버의
애플리케이션과 cloudflared를 다시 올린다. 이관 뒤 맥미니에 쌓인 데이터는 버린다. 주말이라 거의 없다.

### 8. 안정화 뒤 정리

- [ ] 며칠 운영해 본 뒤 오라클 서버 1 정리
- [ ] 키움 허용 IP에서 서버 1 제거
- [ ] DuckDNS 리디렉트 종료 시점 결정
- [ ] 보관 일수 재조정(DB 크기 기준)
- [ ] nginx `set_real_ip_from`을 맥미니 `marketry-net` 네트워크 대역 하나로 좁힌다. 지금은 대역을 몰라 사설 대역 셋을 다 열어 두었다
- [ ] `operations.md`를 새 구성으로 고쳐 쓰고 이 파일 삭제
- [ ] 결정 사항을 `decisions.md`로 회수

---

## 열어둔 것

- Colima를 `deploy` 계정에서 부팅 시 띄우는 게 실제로 되는지는 맥미니에서 시험해 봐야 안다.
  일반 계정에서 기동이 멈춘다는 보고가 있다(abiosoft/colima#1463)
- Codex의 `requirements.toml`이 특정 명령 단위 금지까지 되는지는 설치할 때 확인한다
- 키움이 같은 앱키로 여러 곳에서 토큰을 받을 때 앞선 토큰을 무효화하는지는 모른다. 이관 방식이 지금
  서버를 먼저 내리는 쪽이라 당일에는 문제가 되지 않는다
