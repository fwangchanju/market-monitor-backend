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

**로그인 작업을 기다릴 이유가 없다.** 지금 이미 뚫려 있는 구멍이다.

---

## 2. 확정된 결정

### 결정 1 — `limit_req`는 `auth_request`보다 먼저 돈다. 그래서 효과가 있다

nginx 요청 처리 단계가 이렇다.

```
... → preaccess (limit_req, limit_conn) → access (auth_request) → content
```

**제한에 걸린 요청은 서브요청이 나가기 전에 거절된다.** 즉 `location /`과 `location /api/`에 거는
것만으로 Spring의 access-check까지 보호된다. `/internal/...` 위치에 따로 걸 필요가 없고, 걸어서도
안 된다 — 정상 사용자의 서브요청까지 막게 된다.

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

### 결정 4 — 429로 응답하고 로그에 남긴다

기본값은 503인데, 503은 "서버가 아픔"이고 429는 "네가 너무 많이 보냄"이다. 원인 파악이 갈린다.
그리고 제한에 실제로 걸리는지 나중에 확인할 수 있어야 하므로 로그 레벨을 낮추지 않는다.

---

## 3. 범위

### 할 것

- `infra/nginx.conf`에 `limit_req_zone` 정의와 `limit_req` 적용
- `limit_conn`으로 동시 연결 제한
- `limit_req_status` / `limit_conn_status`를 429로

### 안 할 것

- **`/internal/...` 위치에 제한을 걸지 않는다** (결정 1)
- **렌더러 IP 예외를 만들지 않는다** (결정 3)
- **인증·로그인 관련 변경을 하지 않는다.** 가입·로그인 엔드포인트 전용 제한은 로그인 작업의 몫이다
  (`docs/backlog.md`의 「화이트리스트가 없어질 때 같이 봐야 하는 것」)
- **Spring 쪽에 아무것도 추가하지 않는다.** 이 작업은 nginx 설정 파일 하나로 끝난다

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
```

적용은 **443 server 블록의 `location /`과 `location /api/`** 두 곳이다.

```nginx
location /api/ {
    limit_req  zone=general burst=50 nodelay;
    limit_conn conn 30;
    auth_request /internal/access-check-general;
    ...
}

location / {
    limit_req  zone=general burst=50 nodelay;
    limit_conn conn 30;
    auth_request /internal/access-check-general;
    ...
}
```

`/api/admin/`은 `location /api/`와 별개 블록이므로 거기에도 같이 넣는다. 세 곳이다.

**`nodelay`를 반드시 넣어라.** 빼면 nginx가 초과분을 지연시켜 버스트를 평탄화하는데, SPA 첫 로드처럼
파일 수십 개를 한꺼번에 받는 경우 체감 속도가 눈에 띄게 느려진다. `nodelay`는 버스트 안이면 즉시
통과시키고 버스트를 넘을 때만 거절한다.

### 숫자의 근거

| 값 | 의미 |
|---|---|
| `rate=20r/s` | 지속 기준. 사람이 화면을 보면서 이 속도를 유지할 수 없다 |
| `burst=50` | 순간 허용. SPA 첫 로드가 자산 수십 개를 동시에 받는 것을 덮는다 |
| `limit_conn 30` | 동시 연결. 느린 연결을 다수 붙들어두는 방식을 막는다 |
| `10m` 존 | IP 하나당 약 64바이트, 10MB면 16만 IP 분량. 넉넉하다 |

숫자를 이보다 낮추지 마라. 낮춰서 얻는 것이 없다(결정 2).

### HTTP(80) 서버 블록은 그대로 둔다

거기는 `return 301`과 certbot 챌린지뿐이라 Spring을 호출하지 않는다. 제한을 걸 이유가 없다.

---

## 5. 함정

### 5-1. `limit_req_zone`을 server 블록 안에 넣으면 기동이 안 된다 ★

`http` 컨텍스트 전용 지시어다. `server {}` 안에 두면 nginx가 설정 파싱에 실패하고 컨테이너가 크래시
루프에 빠진다. `infra/scripts/nginx-health-check.sh`가 재시작 3회를 감지해 배포를 실패시키지만,
그 전에 **로컬에서 문법 검사를 하고 올려라.**

```bash
docker run --rm -v "$PWD/infra/nginx.conf:/etc/nginx/conf.d/default.conf:ro" nginx:alpine nginx -t
```

SSL 인증서 경로가 없어서 그 부분에서 실패할 수 있는데, **`limit_req` 관련 문법 오류인지 인증서
문제인지 메시지로 구분된다.** 인증서 오류만 남으면 통과로 본다.

### 5-2. 렌더러가 제한에 걸리는지 확인해야 한다 ★★

`CAPTURE_URL`이 공개 도메인을 가리키면 **렌더러의 캡처 요청이 이 제한을 통과해야 한다.** 그 값은
서버 환경변수라 레포에서 확인할 수 없다.

배포 후 **다음 발송 tick(15분 이내)**에 텔레그램이 정상적으로 오는지 확인하고, nginx 로그에 429가
찍히는지 본다.

```bash
docker logs market-monitor-nginx 2>&1 | grep ' 429 '
docker exec market-monitor-nginx sh -c 'grep "limiting requests" /var/log/nginx/error.log | tail'
```

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

1. `nginx -t` 문법 검사 통과 (5-1)
2. `limit_req_zone`·`limit_conn_zone`이 server 블록 **바깥**에 있다
3. `location /`, `location /api/`, `location /api/admin/` 세 곳에 `limit_req`와 `limit_conn`이 있다
4. 모든 `limit_req`에 `nodelay`가 있다
5. `/internal/...` 위치에는 제한이 **없다**
6. 80 포트 server 블록은 그대로다
7. 이 지시서 파일(`docs/instructions-nginx-rate-limit.md`)을 마지막 커밋에서 삭제한다

---

## 7. 배포

`infra/nginx.conf`는 nginx 이미지에 들어가므로 **`target=nginx` 배포**가 필요하다. 백엔드·렌더러와
무관하다.

배포 직후 5-2를 확인한다. 그것이 이 작업의 유일한 실전 검증이다.
