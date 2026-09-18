# 지시서 — nginx에 요청 제한(limit_req)을 넣는다

이 파일 하나만 읽고 작업할 수 있게 썼다. 근거가 필요하면 인용한 설정을 직접 열어 확인하면 된다.

---

## 1. 무엇이 문제인가

**`infra/nginx.conf`에 요청 제한이 한 줄도 없다.** `limit_req`도 `limit_conn`도 0건이다.

그리고 이 구조에서 그게 특히 나쁘다. **nginx가 요청을 자기 선에서 쳐내지 않는다.**

```nginx
location / {
    auth_request /internal/access-check-general;   →  proxy_pass http://market-monitor:8081/...
    root /usr/share/nginx/html;
}
```

```
낯선 IP가 GET /
  → nginx가 Spring의 /internal/access-check/general 을 호출   ← 여기서 이미 앱이 일한다
  → false
  → nginx가 403
```

정적 파일까지 포함해 **모든 요청이 Spring까지 간다.** IP 화이트리스트는 데이터를 지키지만 가용성은
안 지킨다. 초당 수백 번 때리면 403을 성실하게 뱉으면서 톰캣 스레드를 다 먹는다.

그리고 화이트리스트조차 안 거치는 자리가 하나 있다.

```nginx
location = /internal/register-ip {          # internal 없음, auth_request 없음
    proxy_pass http://market-monitor:8081/internal/register-ip;
}
```

**아무 IP에서나 이 주소를 치면 Spring이 쓰기 트랜잭션을 연다.** 토큰이 맞으면 호출자 IP가 관리자
화이트리스트에 들어간다. 초당 수천 번 돌리면 부하이자 토큰 대입이다. 자세한 것은 결정 1-1.

**로그인 작업을 기다릴 이유가 없다.** 지금 이미 뚫려 있는 구멍이다.

---

## 2. 확정된 결정

### 결정 1 — `limit_req`는 `auth_request`보다 먼저 돈다. 그래서 효과가 있다

nginx 요청 처리 단계가 이렇다.

```
... → preaccess (limit_req, limit_conn) → access (auth_request) → content
```

**제한에 걸린 요청은 서브요청이 나가기 전에 거절된다.** 즉 `location /`과 `location /api/`에 거는
것만으로 Spring의 access-check까지 보호된다.

그리고 `auth_request` 서브요청은 제한에 **걸리지 않는다.** 두 모듈 모두 핸들러 첫 줄에서
`r->main->limit_req_status`(`limit_conn`은 `limit_conn_status`)를 보고, 값이 있으면 그대로 빠져나간다.
서브요청은 `r->main`을 부모와 공유하므로 카운트되지도 거절되지도 않는다. **그래서 `internal;`이 붙은
두 위치에 제한을 걸어봐야 무의미하다** — 위험한 게 아니라 아무 일도 안 한다.

### 결정 1-1 — 그러나 `/internal/register-ip`는 반드시 건다 ★

`/internal/` 아래라고 다 서브요청이 아니다. 셋 중 하나는 **브라우저가 직접 여는 주소다.**

```nginx
location = /internal/access-check-general { internal; ... }   # 21행  서브요청 전용
location = /internal/access-check-admin   { internal; ... }   # 29행  서브요청 전용
location = /internal/register-ip {                            # 37행
    proxy_pass http://market-monitor:8081/internal/register-ip;
}                                                             # ← internal 없음, auth_request 없음
```

`internal;`도 `auth_request`도 없다. **아무 IP에서나 바로 Spring에 닿는다.** 그 끝은
`AdminTokenController.registerIp`이고, `AdminTokenService.registerIp`는 요청마다 **쓰기 트랜잭션**을
열어 `adminTokenRepository.findById(token)`을 친다. 토큰이 맞으면 호출자 IP를 관리자 화이트리스트에
넣는다.

이 설정 전체에서 **인증 없이 DB를 때리고 토큰 추측까지 가능한 유일한 자리다.** 여기에 제한이 없으면
나머지에 거는 의미가 반감된다.

비용은 0이다. 정상 사용자는 관리자 IP를 등록할 때 이 주소를 **한 번** 친다.

### 결정 2 — 넉넉하게 건다. 트래픽 셰이핑이 목적이 아니다

목적은 **봇 스캔과 curl 루프를 끊는 것**이지 정상 사용을 다듬는 것이 아니다. 공격은 초당 수백 건이고
사람은 한 번에 수십 건이다. 그 사이 어디에 선을 그어도 목적을 달성한다.

**낮게 걸면 잃는 것이 더 크다.** 특히 렌더러가 캡처할 때 SPA 껍데기 + 자산 파일 + API를 한꺼번에
요청하는데, 그게 막히면 텔레그램 발송이 통째로 실패한다.

### 결정 3 — 렌더러를 IP로 예외 처리하지 않는다

`geo`/`map`으로 렌더러 서버 IP를 제외하는 방법이 있지만 택하지 않는다. 그 IP는
`RENDERER_SERVER_HOST`로 관리되는 값이라 레포의 정적 설정 파일에 박을 수 없고, `envsubst` 같은
치환 단계를 도입하면 이 작업이 훨씬 커진다.

**결정 2대로 넉넉히 걸면 예외가 필요 없다.** 대신 아래 5-2의 확인을 반드시 한다.

### 결정 3-1 — `infra/nginx-guest.conf`에도 똑같이 넣는다 ★

`infra/scripts/guest-access-on.sh`가 이 파일을 `infra/nginx.conf`로 **복사한다.**

```bash
cp "$REPO_DIR/infra/nginx-guest.conf" "$REPO_DIR/infra/nginx.conf"
```

`nginx.conf`만 고치면 게스트 모드를 한 번 켜는 순간 제한이 통째로 증발한다. 그것도 **IP 화이트리스트가
없어서 제한이 가장 필요한 모드에서** 그렇게 된다.

`nginx-guest.conf`는 `location` 구성이 다르다(`auth_request`가 거의 없고 `/api/admin/allowed-ips`가
따로 있다). **그 파일의 실제 location에 맞춰 넣어라.** `nginx.conf`를 그대로 복사하지 마라.

### 결정 4 — 429로 응답하고 로그에 남긴다

기본값은 503인데, 503은 "서버가 아픔"이고 429는 "네가 너무 많이 보냄"이다. 원인 파악이 갈린다.
그리고 제한에 실제로 걸리는지 나중에 확인할 수 있어야 하므로 로그 레벨을 낮추지 않는다.

---

## 3. 범위

### 할 것

- `infra/nginx.conf`와 `infra/nginx-guest.conf` **둘 다**에 `limit_req_zone` 정의와 `limit_req` 적용
- `limit_conn`으로 동시 연결 제한
- `limit_req_status` / `limit_conn_status`를 429로

### 안 할 것

- **`internal;`이 붙은 두 서브요청 위치에는 제한을 걸지 않는다** (결정 1 — 걸어도 동작하지 않는다)
- **렌더러 IP 예외를 만들지 않는다** (결정 3)
- **인증·로그인 관련 변경을 하지 않는다.** 가입·로그인 엔드포인트 전용 제한은 로그인 작업의 몫이다
- **Spring 쪽에 아무것도 추가하지 않는다.** 이 작업은 nginx 설정 파일 둘로 끝난다

---

## 4. 어떻게 고치나

`infra/nginx.conf`는 `containers/nginx/Dockerfile`이 `/etc/nginx/conf.d/default.conf`로 복사한다.
그 파일들은 메인 `nginx.conf`의 `http {}` 안에서 include되므로 **`limit_req_zone`을 파일 맨 위에
둘 수 있다**(이 지시어는 `http` 컨텍스트 전용이다).

```nginx
# 파일 맨 위, server 블록 바깥
limit_req_zone  $binary_remote_addr  zone=general:10m  rate=20r/s;
limit_conn_zone $binary_remote_addr  zone=conn:10m;

limit_req_status  429;
limit_conn_status 429;

# limit_conn은 HTTP/2·HTTP/3에서 "동시 연결"이 아니라 "동시 요청" 상한이 된다.
# 지금은 listen 443 ssl (HTTP/1.1)이라 안전하다. http2를 켜면 이 값을 다시 계산해야 한다.
```

적용은 **443 server 블록의 아래 네 곳**이다.

```nginx
location = /internal/register-ip {          # ★ 결정 1-1. 빠뜨리기 쉽다
    limit_req  zone=general burst=50 nodelay;
    limit_conn conn 100;
    ...
}

location /api/admin/ { ... }                # location /api/ 와 별개 블록이다
location /api/      { ... }
location /          { ... }
```

네 곳 모두 같은 두 줄이 들어간다.

```nginx
limit_req  zone=general burst=50 nodelay;
limit_conn conn 100;
```

`nginx-guest.conf`는 location 구성이 달라 `/internal/register-ip`, `/api/admin/allowed-ips`,
`/api/`, `/`의 네 곳이다(결정 3-1).

**`nodelay`를 반드시 넣어라.** 빼면 nginx가 초과분을 지연시켜 버스트를 평탄화하는데, SPA 첫 로드처럼
파일 수십 개를 한꺼번에 받는 경우 체감 속도가 눈에 띄게 느려진다. `nodelay`는 버스트 안이면 즉시
통과시키고 버스트를 넘을 때만 거절한다.

### 숫자의 근거

| 값 | 의미 |
|---|---|
| `rate=20r/s` | 지속 기준. 사람이 화면을 보면서 이 속도를 유지할 수 없다 |
| `burst=50` | 순간 허용. 콜드 로드가 15요청 안팎이라 3배 이상 여유 |
| `limit_conn 100` | 동시 연결. 느린 연결을 다수 붙들어두는 방식을 막는다 |
| `10m` 존 | IP 하나당 약 64바이트, 10MB면 16만 IP 분량. 넉넉하다 |

`burst=50`이 실제로 얼마나 여유인지: 프론트 빌드에 코드 스플리팅이 없어서 `dist/index.html`이 참조하는
것은 JS 하나 + CSS 하나 + favicon + 폰트 몇 개다. **콜드 로드가 5~20요청**이다. 버스트 50에 초당 20씩
리필되므로 정상 사용자가 429를 보려면 **1초 안에 하드 리로드를 네 번 이상** 해야 한다.

`limit_conn`을 30이 아니라 100으로 잡는 이유는 `$binary_remote_addr`가 **진짜 클라이언트 IP**라서다
(앞에 프록시가 없다). 사무실이나 이동통신 CGNAT 뒤에서는 여러 사람이 이 한도를 통째로 나눠 쓴다.
HTTP/1.1 브라우저가 호스트당 6커넥션을 여니 30이면 다섯 명만 동시에 들어와도 찬다.

숫자를 이보다 낮추지 마라. 낮춰서 얻는 것이 없다(결정 2).

### HTTP(80) 서버 블록은 그대로 둔다

거기는 `return 301`과 certbot 챌린지뿐이라 Spring을 호출하지 않는다. 제한을 걸 이유가 없다.

---

## 5. 함정

### 5-1. `limit_req_zone`을 server 블록 안에 넣으면 기동이 안 된다 ★

`http` 컨텍스트 전용 지시어다. `server {}` 안에 두면 nginx가 설정 파싱에 실패하고 컨테이너가 크래시
루프에 빠진다. `infra/scripts/nginx-health-check.sh`가 재시작 3회를 감지해 배포를 실패시키지만,
그 전에 **로컬에서 문법 검사를 하고 올려라.**

맨 `docker run`으로는 **깨끗한 통과를 볼 수 없다.** 두 가지가 걸린다 — 인증서 경로가 없고,
`proxy_pass http://market-monitor:8081`의 호스트명을 nginx가 **설정 시점에 해석**하기 때문이다
(`resolver`가 없다). 그리고 nginx는 첫 `[emerg]`에서 **중단하고 종료하므로**, 오류 목록을 보고
"이건 인증서 탓, 저건 문법 탓"으로 골라낼 수 없다.

둘 다 미리 채워주면 진짜 통과 신호를 볼 수 있다.

```bash
docker run --rm -v "$PWD/infra/nginx.conf:/etc/nginx/conf.d/default.conf:ro" nginx:alpine sh -c '
  mkdir -p /etc/letsencrypt/live/eolmae.duckdns.org &&
  openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=t \
    -keyout /etc/letsencrypt/live/eolmae.duckdns.org/privkey.pem \
    -out    /etc/letsencrypt/live/eolmae.duckdns.org/fullchain.pem 2>/dev/null &&
  echo "127.0.0.1 market-monitor" >> /etc/hosts &&
  nginx -t'
```

`syntax is ok` / `test is successful` 두 줄이 나오면 통과다. `nginx-guest.conf`도 같은 방법으로 본다
(마운트 경로만 바꾼다).

**이 명령은 검증되지 않았다** — 지시서를 쓴 환경에 Docker가 없었다. 다른 `[emerg]`가 남으면 그 메시지가
`limit_req`/`limit_conn`을 가리키는지만 확인하고, 아니면 그 원인도 같은 방식으로 채워라. 목표는
"오류를 해석하는 것"이 아니라 **오류가 하나도 없는 상태를 보는 것**이다.

### 5-2. 렌더러가 제한에 걸리는지 확인해야 한다 ★★

`CAPTURE_URL`이 공개 도메인을 가리키면 **렌더러의 캡처 요청이 이 제한을 통과해야 한다.** 그 값은
서버 환경변수라 레포에서 확인할 수 없다.

코드상으로는 위험이 낮다. `containers/renderer/server.js`가 모든 캡처를 `queueTail`로 직렬화하고
persistent context 하나를 재사용하므로 동시 `page.goto`는 최대 하나다. `limit_conn`과 부딪힐 일이
없다. **그래도 확인은 한다** — 캡처 한 번이 여는 요청 수는 코드로 셀 수 없다.

배포 후 **다음 발송 tick(15분 이내)**에 텔레그램이 정상적으로 오는지 확인하고, nginx 로그에 429가
찍히는지 본다.

```bash
docker logs market-monitor-nginx 2>&1 | grep ' 429 '
docker logs market-monitor-nginx 2>&1 | grep 'limiting requests'
```

**`/var/log/nginx/error.log`를 `grep`하지 마라.** 공식 nginx 이미지는 그 경로를 `/dev/stderr`로
심볼릭 링크해둬서, 파일이 아니라 파이프를 읽게 되고 **제한에 걸렸든 아니든 아무것도 안 나온다.**
"조용하니 이상 없다"로 오독하기 딱 좋다. `limit_req_log_level` 기본값이 `error`이고 error.log는
`docker logs`로 나오므로 위의 둘째 줄이 같은 일을 제대로 한다.

**429가 렌더러 IP에서 나오면 즉시 `burst`를 올려라.** 캡처가 막히면 텔레그램이 통째로 멈춘다.
이 작업 때문에 발송이 죽는 것은 얻는 것보다 잃는 것이 크다.

### 5-3. `$binary_remote_addr`를 쓴다

`$remote_addr`(문자열)이 아니라 `$binary_remote_addr`를 쓴다. 메모리를 덜 쓰고 IPv6까지 같은
방식으로 다룬다. 이 앱 앞에 다른 프록시가 없으므로 `X-Forwarded-For`를 신뢰해서는 안 된다 —
그 헤더는 클라이언트가 위조할 수 있다.

### 5-4. `docs/`는 수정하지 않는다

작업 중 알게 된 것은 PR 설명에 남긴다. 아래 완료 기준의 마지막 항목만 예외다.

---

## 6. 완료 기준

1. `nginx.conf`·`nginx-guest.conf` 둘 다 `nginx -t` 통과 (5-1)
2. 두 파일 모두 `limit_req_zone`·`limit_conn_zone`이 server 블록 **바깥**에 있다
3. `nginx.conf`의 `/internal/register-ip`, `/api/admin/`, `/api/`, `/` **네 곳**에 `limit_req`와
   `limit_conn`이 있다
4. `nginx-guest.conf`의 `/internal/register-ip`, `/api/admin/allowed-ips`, `/api/`, `/` 네 곳도 같다
5. 모든 `limit_req`에 `nodelay`가 있다
6. `internal;`이 붙은 두 위치(`access-check-general`·`access-check-admin`)에는 제한이 **없다**
7. 80 포트 server 블록의 `location`에는 `limit_req`가 없다 (`limit_req_status`가 http 레벨이라 그
   블록에도 상속되지만 그대로 둔다 — 거기 `limit_req`가 없어 아무 영향이 없다)
8. 이 지시서 파일(`docs/instructions-nginx-rate-limit.md`)을 마지막 커밋에서 삭제한다

---

## 7. 배포

**`target=nginx` 배포**가 필요하다. 백엔드·렌더러와 무관하다.

근거를 정확히 적어둔다. `containers/nginx/Dockerfile`이 `infra/nginx.conf`를 이미지에 복사하기는
하지만, `infra/nginx-docker-compose.yml`이 같은 경로를 `./nginx.conf`로 **덮어 마운트하므로 이미지
사본은 가려진다.** 실제로 쓰이는 것은 서버의 git 체크아웃이고, 그건 배포 때마다
`git reset --hard origin/main`으로 갱신된다(`docs/operations.md`).

그러니 `target=nginx`가 주는 것은 파일 갱신이 아니라 **재적재**(`deploy-nginx.sh`의 `down` → `up -d`)다.
`target=application`만 돌리면 **파일은 조용히 바뀌고 적용은 안 된 상태**가 된다. 그게 이 배포가 필요한
이유다.

배포 직후 5-2를 확인한다. 그것이 이 작업의 유일한 실전 검증이다.
