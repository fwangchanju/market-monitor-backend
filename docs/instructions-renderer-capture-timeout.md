# 지시서 — 렌더러 캡처 대기 20초, 실패 로그에 경로 남기기

작은 작업이다. `containers/renderer/server.js` 하나만 고친다.

---

## 1. 무엇이 문제인가

### 1-1. 15초가 경계에 너무 붙어 있다

텔레그램 캡처가 `waitForSelector` 타임아웃으로 죽는 일이 있었다.

```
2026-09-21   섹터 발송 26틱 중 11건 실패 (42%)
2026-09-22   같은 코드로 오전 6틱 전부 통과 (0%)
```

**배포된 것이 없는데 하루 만에 42%에서 0%가 됐다.** 매번 나는 결함이 아니라 15초 경계 바로 옆에서
조건이 나쁠 때만 넘어가는 종류다.

렌더러 서버에서 잰 실측이다(2026-09-22 장중, 수집 tick 사이).

```
/api/sector?market=ALL_STOCK   7.11  7.26  8.29 초
/api/sector?market=KOSPI       3.27  2.90  2.67 초
/api/map?market=ALL_STOCK      2.89  2.29  2.99 초
```

**경합이 없는 상태에서도 조회 하나가 8초를 쓴다.** 수집 배치가 DB를 쓰는 순간과 겹치면 15초를 넘는다.

근본 원인(프론트가 캡처마다 쓸데없는 `ALL_STOCK` 조회를 한 번 더 한다)은 프론트 레포에서 따로 고친다
(`market-monitor-frontend`의 `docs/instructions-route-market-first-render.md`). **이 작업은 그것과
독립이고, 어느 쪽이 먼저 나가도 된다.**

**여기서 하는 것은 완충이지 해결이 아니다.** 캡처 한 건이 18초 걸려도 발송이 성공하는 쪽이, 15초에서
잘려 그 tick의 텔레그램이 통째로 빠지는 것보다 낫다.

### 1-2. 실패 로그에 어느 경로였는지가 없다

```js
console.error('[renderer] 캡처 오류:', err.message)
```

어느 마켓을 찍다 실패했는지 알 수 없다. 이번 진단에서 그 때문에 "렌더러 로그 → 앱 로그 → 시각
대조"를 한참 돌아야 했다. `path`가 찍혀 있었으면 한 줄로 끝났다.

---

## 2. 확정된 결정

### 결정 1 — `waitForSelector`를 20초로

```js
// containers/renderer/server.js:121
await page.waitForSelector(`${selector}[data-capture-ready="true"]`, { timeout: 15000 })
```

15000 → **20000**.

**30초가 아니라 20초다**(사용자 결정). 실측 기준 한가할 때 캡처 한 건이 8초 안팎이고, 프론트 수정이
들어가면 3초 수준으로 내려간다. 20초면 경합으로 두 배가 되어도 덮는다. 더 늘리면 실제로 고장난 경우에
발송 지연만 길어진다.

**파일 맨 위 상수로 뺀다.** 이 파일은 `CLEANUP_TIMEOUT_MS`·`MAX_QUEUE_WAIT_MS`·`SHUTDOWN_GRACE_MS`를
전부 상수로 두고 있다. 같은 모양을 따른다.

### 결정 2 — 실패 로그에 `path`를 남긴다

`path`는 같은 클로저 안에 있어서 그대로 쓸 수 있다(`app.post('/capture', ...)`의 구조분해).

**두 실패 경로에 다 넣는다** — `isQueueTimeout`(503)과 일반 실패(500). 대기열 초과도 "어느 요청이
밀렸나"를 알아야 한다.

`selector`는 넣지 않는다. `path`만으로 어느 페이지·어느 마켓인지 특정된다.

### 결정 3 — `goto`의 30초와 `MAX_QUEUE_WAIT_MS`는 건드리지 않는다

숫자 셋이 서로 물려 있어서 범위를 좁힌다.

```
goto(networkidle)   30000     ← 그대로
waitForSelector     15000 → 20000
MAX_QUEUE_WAIT_MS   30000     ← 그대로
앱 쪽 read timeout  90초      ← ApplicationConfig.rendererRestClient. 그대로
```

캡처 한 건의 최대는 `30 + 20 = 50초`가 되고, 앱 read timeout 90초 안이라 안전하다.

`MAX_QUEUE_WAIT_MS`(30초)보다 캡처 최대치가 길다는 것은 **이번 변경 이전에도 그랬다.** 앱이 마켓을
하나씩 순차로 요청해서 대기열에 둘 이상 쌓이는 일이 사실상 없기 때문에 지금까지 문제가 안 됐다.
**그 전제가 유지되는 동안은 그대로 둔다.**

---

## 3. 범위

### 할 것

- `waitForSelector` 타임아웃 20초, 상수로 분리
- 캡처 실패 로그 두 곳에 `path` 추가

### 안 할 것

- **`goto` 타임아웃·`MAX_QUEUE_WAIT_MS`를 건드리지 않는다** (결정 3)
- **재시작·컨텍스트 재활용 로직을 건드리지 않는다.** `MAX_CONSECUTIVE_FAILURES`,
  `RECYCLE_AFTER_CAPTURES`, `onBrowserFailure` 전부 그대로다.
  `waitForSelector` 타임아웃을 실패 카운트에 넣지 않는 현재 동작도 그대로다 — 프론트·요청 문제지
  브라우저가 죽은 게 아니다(파일 안 주석에 그 근거가 적혀 있다)
- **Spring 쪽과 프론트를 건드리지 않는다.** 이 작업은 `server.js` 하나로 끝난다

---

## 4. 완료 기준

1. `waitForSelector` 타임아웃이 20초고, 파일 맨 위 상수로 선언돼 있다
2. 캡처 실패 로그(500·503 두 경로)에 `path`가 찍힌다
3. `goto` 타임아웃과 `MAX_QUEUE_WAIT_MS`는 그대로다
4. 이 지시서 파일(`docs/instructions-renderer-capture-timeout.md`)을 마지막 커밋에서 삭제한다

---

## 5. 배포

**`target=renderer` 배포**가 필요하다. 백엔드·nginx와 무관하다.

`docs/operations.md`의 「`application`은 렌더러를 건드리지 않는다」를 본다 — `application`만 돌리면
렌더러는 옛 이미지 그대로다.

배포 후 다음 발송 tick(15분 이내)에 텔레그램이 정상적으로 오는지 확인한다. 그리고 실패가 나면
**경로가 같이 찍히는지** 본다.

```bash
docker logs -t market-monitor-renderer 2>&1 | grep '캡처 오류' | tail
```

**실패가 0건이어도 고쳐졌다는 증거는 아니다.** 1-1에 적은 대로 9/22는 아무것도 안 고친 상태로 0건이
나왔다. 이 변경의 성과는 "실패가 멎는 것"이 아니라 **"경계가 5초 넓어지는 것"**이다. 판정은 여러 날로
본다.
