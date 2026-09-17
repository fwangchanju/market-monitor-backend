# 지시서 — 렌더러 프로세스 누수 복구 + 정리 배치 보존 규칙 전환

> **임시 문서다. 이 작업이 끝나면 같은 PR의 마지막 커밋에서 이 파일을 삭제한다.**
>
> `docs/backlog.md`와 `docs/decisions.md`는 평소대로 읽어도 된다. 다만 **이 작업의 근거 일부는
> 아직 병합되지 않은 PR #108에만 있어서 main의 backlog에는 없다.** 그래서 이 지시서가 필요한
> 내용을 전부 담고 있다. 두 문서에서 이 작업의 근거를 더 찾지 않아도 된다.
>
> **`docs/` 아래 어떤 파일도 수정하지 않는다**(이 파일의 삭제만 예외). 작업 중 알게 된 것은
> PR 설명에 적는다.

## 두 작업을 한 PR에 담는 이유

1부(렌더러)와 2부(정리 배치)는 **되돌릴 단위가 다르다.** 렌더러는 서버 2에 `target=renderer`로,
백엔드는 서버 1에 `target=application`으로 따로 나간다. 이미지도 별개다. `docs/rules/process.md`의
"성격이 다른 작업을 한 PR에 섞지 않는다"에 어긋난다.

**그럼에도 한 PR로 묶는 것은 사용자 결정이다.** 둘 다 같은 시점에 내보내기로 했고, 리뷰와 병합을
두 번 하지 않기 위해서다. **규칙 충돌 때문에 멈추지 마라.** 대신 **커밋은 반드시 나눈다** — 1부와
2부를 같은 커밋에 섞지 않는다. 나중에 한쪽만 revert 할 수 있어야 한다.

---

# 1부 — 렌더러

## 무슨 일이 있었나

2026-09-15부터 9/17까지 **텔레그램이 한 건도 나가지 않았다.** 48시간 동안 캡처 92건이 전부
실패했다. 원인을 찾았다.

```
pids.current  1072
pids.max      1073        ← systemd 기본값(kernel.threads-max 7156 × 15%)
cgroup.threads   56       ← 실제 task 수
```

**컨테이너의 PID 한도가 포화돼서 `fork`가 실패했다.** Chromium은 자식 프로세스를 여럿 띄우므로
새 프로세스를 못 만들면 뜨자마자 죽는다. 그래서 `browserType.launch`가 `SIGTRAP`으로 실패했다.

```
<launched> pid=27452
[pid=27452] <process did exit: exitCode=null, signal=SIGTRAP>
```

**주의할 점이 둘 있다.**

**1,072 중 1,016은 실체가 없었다.** `cgroup.threads`로 센 실제 task는 56개인데 커널 카운터는
1,072를 가리켰다. 죽은 task가 반납되지 않고 쌓인 것이다. **애플리케이션 코드로 고칠 수 없다.**

**좀비는 0개였다.** `docker top | grep -c defunct` = 0. PID 1이 `node`라서 고아를 못 거둔다는
가설은 틀렸다.

그래서 이 작업의 목표는 **원인을 고치는 것이 아니라, 빨리 알아채고 스스로 낫게 만드는 것**이다.

## 하나 더 — 9/15에 띄운 브라우저가 이틀째 살아 있었다

```
node server.js                     Sep09
chrome-headless-shell (browser)    Sep15   ← 안 죽음
  ├ zygote / zygote / gpu-process / utility(NetworkService)
```

`finally { if (browser) await browser.close() }`가 있는데도 남았다. **Chromium이 응답을 멈추면
`close()`가 무기한 대기한다.** 타임아웃이 없다.

## 고칠 것 — `containers/renderer/server.js`

지금은 `/capture` 요청마다 `chromium.launch()`로 브라우저를 새로 띄우고 끝나면 닫는다. 하루
약 104번이다(섹터 49회 발송 × 2마켓 = 98, 맵 3회 × 2마켓 = 6). 이걸 아래처럼 바꾼다.

**아래 값은 전부 확정이다. 다른 값이 나아 보여도 바꾸지 마라.**

### ① 브라우저를 한 번만 띄우고 재사용한다

`chromium.launchPersistentContext(userDataDir, {...})`로 **모듈 레벨에 컨텍스트 하나**를 두고,
요청마다 `newPage()`로 페이지만 새로 연다. 페이지는 `finally`에서 반드시 닫는다.

`browser.newContext()`가 아니라 `launchPersistentContext`인 이유는 **HTTP 캐시가 요청 간에
공유되기 때문**이다. `newContext()`는 컨텍스트마다 캐시가 따로라, 프로세스 생성은 없애도 폰트를
매번 다시 받는다. 프론트 폰트는 Pretendard woff2 3개(regular/medium/bold)로 약 2.2MB다.

격리는 확인해뒀다. **프론트는 `localStorage`를 한 곳도 쓰지 않고 `sessionStorage`만 쓰는데 그건
탭 단위라 페이지마다 격리된다.** 쿠키·IndexedDB·서비스워커도 쓰지 않는다. 요청 간에 샐 상태가 없다.

확정 사항:

- `userDataDir`는 **`/tmp/renderer-profile`** 고정
- **기동 직후 `userDataDir`를 통째로 지우고 시작한다.** `process.exit(1)` + `restart: always`는
  컨테이너 파일시스템을 보존하므로, 죽은 Chromium이 남긴 `SingletonLock` 같은 것이 그대로 남는다.
  한 줄로 이 경로를 없앤다
- 최초 요청에서 지연 생성하되, **동시 요청이 두 번 띄우지 않도록** 생성 프로미스를 공유한다
- 매 요청 시작에 컨텍스트가 살아 있는지 확인한다. 죽었으면 버리고 다시 띄운다
- **`launchPersistentContext`는 빈 `about:blank` 페이지를 자동으로 하나 만든다.** 그 초기 페이지를
  닫든가 재사용하든가 하나를 골라 처리한다. 방치하면 계속 남는다
- **`viewport: { width: 1920, height: 1080 }`는 그대로 둔다.** 이미 확정된 값이고 바꾸면 캡처
  결과가 달라진다
- **`page.goto`의 30초, `waitForSelector`의 15초도 그대로 둔다.** 백엔드
  `ApplicationConfig.rendererRestClient`의 읽기 타임아웃 90초가 이 두 값을 근거로 정해져 있고
  주석이 이 파일을 명시 참조한다
- `args`(`--no-sandbox`, `--disable-dev-shm-usage`, `--disable-gpu`)와 `CAPTURE_USER`/`CAPTURE_PASS`
  기본 인증 헤더도 그대로 유지한다

### ② 정리에 타임아웃을 건다 — 10초

컨텍스트를 닫아야 하는 모든 자리(페이지 닫기, 컨텍스트 재생성, 주기적 재활용)에 **10초** 타임아웃을
건다. **10초 안에 안 닫히면 기다리지 않고 참조만 버린다.** 지금처럼 `await`이 영영 안 끝나서
프로세스가 남는 일이 없어야 한다.

### ③ 연속 실패가 3회면 스스로 종료한다 — **실패의 정의가 중요하다**

모듈 레벨에 연속 실패 카운터를 둔다.

```
캡처 성공                  → 카운터 0으로
브라우저·컨텍스트 계열 실패 → 카운터 +1
카운터 ≥ 3                 → 정리 시도 후 process.exit(1)
```

**카운터에 넣는 것은 브라우저·컨텍스트 계열 실패뿐이다.** 구체적으로:

| 실패 | 카운터 | 왜 |
|---|---|---|
| `launchPersistentContext` 실패 | **센다** | 이번 장애가 정확히 이 형태였다 |
| 컨텍스트 연결 끊김 / `newPage` 실패 | **센다** | 브라우저가 죽은 것이다 |
| `page.goto` 30초 타임아웃 | **세지 않는다** | 프론트·데이터 문제다. 재시작으로 안 고쳐진다 |
| `waitForSelector` 15초 타임아웃 | **세지 않는다** | 같음 |
| `path`/`selector` 누락 400 | **세지 않는다** | 브라우저를 건드리지도 않는다 |

**`goto` 타임아웃을 세면 안 되는 이유**: 프론트가 깨졌을 때 렌더러가 캡처 3번마다 자살한다.
섹터 발송만 하루 98회라 **하루 30회 넘게 재시작**하고, 발송 간격이 15분이라 도커의 재시작
백오프도 매번 초기화되어 감쇠가 안 걸린다. 프론트 장애가 렌더러 장애로 번진다.

`restart: always`가 컨테이너를 되살리고, **그때 cgroup이 새로 만들어지면서 PID 카운터가 0부터
시작한다.** 이게 이번 장애의 유일한 회복 경로다.

종료 직전에 **이유와 카운터 값을 로그로 남긴다.** 왜 죽었는지 모르면 다음에 또 헤맨다.

### ④ 요청을 직렬화한다 — 대기 상한 30초

램 956MB짜리 머신이다. 페이지를 동시에 여러 개 열면 압박이 크다. 백엔드는 지금 순차로 호출하지만
(`SectorTelegramReportSender`의 마켓 루프, `MarketMapAlbumReportSender.capture`의 순차 스트림)
보장된 계약은 아니므로 **렌더러 쪽에서 프로미스 체인으로 한 번에 하나만 처리**한다.

**대기가 30초를 넘으면 그 요청은 503으로 거절한다.** 한 요청의 최악은 `goto` 30초 +
`waitForSelector` 15초 = 45초인데, 상한이 없으면 대기 45 + 처리 45 = 90초가 되어 백엔드
읽기 타임아웃과 정확히 같아진다. 30초 상한이면 최악 75초로 여유가 생긴다.

### ⑤ 200회마다 재활용한다

캡처를 **200회** 하면 컨텍스트를 닫고 다시 띄운다. 하루 약 104회이므로 이틀에 한 번 꼴이다.
장기 실행 Chromium의 완만한 누수에 대한 보험이다. 200은 상수로 둔다.

### ⑥ `/health`가 상태를 말하게 한다

지금은 무조건 `{status:'ok'}`다. 아래를 같이 실어준다.

| 필드 | 값 |
|---|---|
| 컨텍스트 상태 | `not_created` / `alive` / `dead` **셋으로 구분한다** |
| 연속 실패 횟수 | 0~2 (3이면 프로세스가 죽으므로 관측되지 않는다) |
| 누적 캡처 횟수 | **기동 이후 누적.** ⑤의 재활용 카운터와 별개로 센다 |

**503을 주는 조건은 `dead` 하나뿐이다.** `not_created`는 정상이다 — 컨텍스트는 최초 요청에서
지연 생성하므로, 기동 직후와 장 마감 후 밤새 계속 `not_created`다. 이걸 503으로 주면 배포
직후부터 unhealthy로 표시된다.

## 고칠 것 — `infra/renderer-docker-compose.yml`

```yaml
services:
  market-monitor-renderer:
    image: ghcr.io/fwangchanju/market-monitor-renderer:${IMAGE_TAG}
    container_name: market-monitor-renderer
    restart: always
    init: true
    env_file:
      - ${HOME}/env/market-monitor.env
    ports:
      - "3000:3000"
    shm_size: '128m'
    healthcheck:
      test: ["CMD", "node", "-e", "require('http').get('http://localhost:3000/health',r=>process.exit(r.statusCode===200?0:1)).on('error',()=>process.exit(1))"]
      interval: 300s
      timeout: 10s
      retries: 3
      start_period: 30s
```

**`pids_limit`과 `mem_limit`은 이번에 넣지 않는다.** 위의 56은 **브라우저가 안 떠 있던 고장
상태에서 잰 값**이라, 브라우저를 상주시키는 ①번 변경 이후의 정상치를 대표하지 않는다. 한도를
실측 없이 조이면 정상 동작 중에 컨테이너가 죽는데 **렌더러는 롤백 수단이 없다.** 배포 후
실측하고 별도 PR에서 건다. 자기 복구(③)는 이 값들 없이도 동작한다.

`init: true`는 **이번 장애의 원인이 아니다**(좀비 0개). 브라우저가 오래 살게 되면서 생길 고아
프로세스에 대한 보험이고 Playwright 컨테이너의 표준 설정이다.

`interval: 300s`인 이유: 이 헬스체크는 60초마다면 하루 1,440번 `node` 프로세스를 새로 띄운다.
캡처가 하루 104회인데 그 14배다. **"원인 모를 프로세스 생성 누수"에 대응하면서 프로세스 생성
빈도를 올리는 것은 방향이 반대다.** 300초면 하루 288회로 줄어든다.

## 만들 것 — 렌더러 배포 후 헬스체크

**`release.yml`의 `renderer` job에는 배포 후 헬스체크가 없다.** `application`과 `nginx` job에는
있다(`infra/scripts/health-check.sh`, `nginx-health-check.sh`). 렌더러는 롤백 수단도 없으므로,
나쁜 이미지가 나갔을 때 알아챌 자동 수단이 지금 0이다.

- `infra/scripts/renderer-health-check.sh`를 만든다. **기존 두 스크립트의 구조·재시도 방식·
  로그 형식을 그대로 따른다.** `localhost:3000/health`가 200을 줄 때까지 재시도하고, 끝내 실패하면
  0이 아닌 코드로 끝난다
- `release.yml`의 `renderer` job에서 `deploy-renderer.sh` 다음에 이 스크립트를 실행하는 step을
  추가한다

**자동 원복은 넣지 않는다.** 렌더러는 `:previous` 포인터가 없어서 되돌릴 대상이 없다. 실패를
**알리는 것**까지가 이번 범위다.

## 검증 — 구현 세션은 할 수 없다. 체크리스트를 만들어라

**구현 세션 환경에는 docker 데몬이 없고 프론트도 안 떠 있다.** 그러니 아래 항목을 직접 실행하려고
시도하지 말고, **PR 설명에 "사용자가 서버 2에서 수행할 체크리스트"로 복붙 가능한 명령과 함께
적는다.** 배포는 main 병합 후에만 가능하므로 순서가 강제된다(병합 → 배포 → 확인 → 문제 시 revert PR).

체크리스트에 담을 것:

1. **재사용이 실제로 되는가** — `/capture`를 연속 5회 호출하고, 그 전후로
   `docker exec market-monitor-renderer cat /sys/fs/cgroup/pids.current`가 **늘지 않는지**
   (`docker stats`의 PIDS도 같은 값을 보여주지만, 프로세스가 아니라 task 수임을 명시할 것)
2. **정상 상태의 PID·메모리 실측값** — 위에서 `pids_limit`/`mem_limit`을 보류했으므로 이 값이
   다음 PR의 근거가 된다. 캡처가 **진행 중인 순간**의 값을 재게 할 것
3. **`/health`가 세 상태를 제대로 구분하는가** — 기동 직후 `not_created`, 캡처 후 `alive`
4. **자기 종료가 실제로 동작하는가** — 이 작업의 핵심이다. `launchPersistentContext`가 실패하는
   상황을 만들어(예: `userDataDir`를 읽기 전용으로) 연속 3회 뒤
   `docker inspect market-monitor-renderer --format '{{.RestartCount}}'`가 오르는지.
   **③에서 세지 않기로 한 실패(존재하지 않는 경로, 셀렉터 없음)로는 이게 안 일어나는 것도
   같이 확인**해야 한다. 그게 이번에 좁힌 정의가 실제로 적용됐다는 증거다

---

# 2부 — 정리 배치 보존 규칙

## 지금 무엇이 문제인가

정리 배치는 **장 종가 스냅샷만 남기고 오래된 것을 지운다**는 취지다. 그런데 남기는 기준이
`15:30` 고정이다.

```java
// SectorPriceSnapshotService:28, MarketMapCategoryChangeRateSnapshotService:46
private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(15, 30);
```

**15:30은 종가가 아니다.** 13영업일치를 대조한 결과 종가가 확정되는 것은 **15:35**다. 15:30은
종가 동시호가 결과가 아직 다 반영되지 않은 값이라 98.1~99.7%만 맞는다(15:35는 99.7~100%).
지수도 똑같아서 15:35 값이 20:00까지 한 번도 안 바뀐다.

지금은 드라이런이라 아무것도 안 지웠지만, **실삭제로 전환했으면 매일 진짜 종가를 지우고 있었다.**

## 바꿀 규칙

시각을 박지 않는다. **윈도우의 latest를 남긴다.**

```
남길 것 = 그 날짜, 그 마켓에서 snapshot_time이 [15:30, 15:40) 안인 시각 중
          가장 늦은 시각 하나를 고르고, 그 시각의 행을 전부 남긴다
```

**"행 하나"가 아니라 "그 시각의 행 전부"다.** 한 시각의 스냅샷은 종목별·카테고리×구간별로
수백~수천 행이다.

두 경계 모두 거래소 시간표에서 나온다.

```
15:20  NXT 메인마켓 마감
15:30  KRX 정규장 마감          ← 윈도우 시작
       이 10분은 KRX·NXT 둘 다 닫혀 있어 가격이 변하지 않는다
15:40  NXT 애프터마켓 개장      ← 윈도우 끝
```

시각 하나를 박는 것보다 나은 이유가 셋이다.

1. **수집 주기가 바뀌어도 안 깨진다.** `collect.interval-minutes`를 3분이나 10분으로 바꾸면
   15:35라는 시각 자체가 사라질 수 있다
2. **15:35가 없는 날이 폴백 코드 없이 풀린다.** 2026-08-28이 그랬는데, 윈도우의 latest가
   자연스럽게 15:30이 된다
3. 그 10분간 가격이 변하지 않으므로 어느 시각이 잡히든 같은 값이다

## 상수로 둔다 — 프로퍼티로 빼지 않는다

`docs/decisions.md`의 「삭제 범위는 상수로, 실행 스위치만 프로퍼티로 둔다」를 **그대로 지킨다.**

> 보존 기간과 보존 시각은 이 배치가 무엇을 남기고 무엇을 지우는지를 정하는 정책이고, 개발 환경과
> 운영 환경에서 달라야 할 이유가 없다. (…) 프로퍼티로 선언하면 환경변수 오버라이드 경로가
> 자동으로 열린다. **되돌릴 수 없는 작업에서는 그 경로를 굳이 열어둘 이유가 없다.**

그러므로 `MARKET_CLOSE_TIME` 상수 하나를 **윈도우 두 값 상수로 바꾸기만 한다.** 새 프로퍼티를
만들지 않는다.

**두 서비스가 각자 상수를 갖는 지금 구조를 유지한다.** 공용 자리로 합치지 않는다 —
`docs/rules/style.md`가 `common/` 하위 패키지를 열거해 두었는데 공유 상수의 자리가 없고, 지금도
`MARKET_CLOSE_TIME`이 두 곳에 중복이라 이번에 나빠지는 것이 없다.

상수 이름도 같이 바로잡는다. `MARKET_CLOSE_TIME`은 "장 마감 시각"으로 읽히는데 실제로 뜻하는
것은 "보존할 스냅샷 시각"이다. **그 어긋남이 이번 문제의 뿌리 중 하나다.**

## 놓치면 안 되는 것 셋

### ① 마켓별로 따로 판정한다

두 테이블 모두 마켓별로 따로 실패할 수 있다.

- `sector_price_snapshot`: `IndexContributionRankingCollector.collect`가 **마켓마다 별도
  트랜잭션**을 돌린다
- `market_map_category_change_rate_snapshot`: `CollectionScheduler.captureCategoryChangeRateSnapshots`가
  마켓 루프를 돌며 `captureSnapshot`을 마켓당 한 번씩 부르고, 그 메서드는 `tree.isEmpty()`면
  **조용히 return** 한다. 이쪽이 오히려 한쪽만 비기 더 쉽다

날짜 단위로만 latest를 잡으면 **그런 날 한쪽 마켓의 종가가 삭제된다.** `(날짜, 마켓)` 단위로
latest를 구해야 한다.

### ② 윈도우가 빈 날은 보존 대상이 0이다

2026-08-27이 그렇다. 15:20~15:55 수집이 통째로 비어서 15:30도 15:35도 없다. 그런 날은 그
날짜가 통째로 삭제 대상이 된다.

**윈도우를 16:00까지 늘려서 메우려 하지 마라.** 2026-09-14 KRX 애프터마켓 시행 이후 16:00은
이미 시간외 체결가라, 그걸 종가로 남기면 **틀린 값이 영구 보존된다.** 없는 날은 없는 채로 둔다.
대신 로그에 남긴다(아래 ③).

### ③ 로그가 "지울 것"이 아니라 "남길 것"을 말해야 한다

지금 로그는 삭제 대상만 찍는다.

```
[섹터가격스냅샷정리] 대상건수:{} | cutoff이전전체건수:{} | 최소시각:{} | 최대시각:{} | 표본시각:{}
```

이번 변경으로 확인하려는 것은 **날짜별로 어느 시각이 보존되는가**다. 그게 안 찍히면 드라이런으로
배포하는 의미가 없다. 기존 줄은 그대로 두고 아래 두 줄을 추가한다.

```
[섹터가격스냅샷정리] 보존시각 | 표본:{} | 보존날짜수:{}
[섹터가격스냅샷정리] 보존할행없음 | 날짜:{} | 건수:{}
```

- **`docs/rules/style.md` §12의 기존 규약(`[라벨] 키:{} | 키:{}`)을 따른다.** 위 포맷은 예시이고
  라벨·키 이름은 그 규약 안에서 정하면 된다
- 범위는 **cutoff 이전 날짜만**이다. 지금 `summarizeSnapshotsToDelete`와 같은 범위다
- 표본 상한은 기존 **`RETENTION_LOG_SAMPLE_SIZE = 5`를 재사용**한다. 날짜 내림차순으로 최근
  5개를 찍는다
- **「보존할행없음」은 자르지 않는다.** 그게 드문 일인지 상시인지가 다음 설계를 좌우한다.
  날짜가 많으면 건수와 함께 날짜 목록을 찍는다

## 구현 방식 — 2단계로 확정한다

**한 방 쿼리로 만들지 마라.** 이 레포에는 DB 테스트가 하나도 없다(`@DataJpaTest`·Testcontainers
없음, `@SpringBootTest` 계열은 `@Tag("manual")`이라 `test` 태스크에서 제외된다). `ci.yml`이
"DB·외부 API 없이 돌아야 한다"고 못박았다. **QueryDSL이 만드는 술어는 반대로 뒤집혀 있어도
컴파일과 단위 테스트가 전부 통과한다.**

그래서 이렇게 나눈다.

```
1단계  리포지토리: cutoff 이전 & 윈도우 안인 (마켓, snapshot_time)을 distinct로 가져온다
2단계  순수 자바 함수: 그 목록을 (날짜, 마켓)으로 묶어 각 그룹의 latest를 고른다   ← 테스트 대상
3단계  리포지토리: cutoff 이전이면서 2단계 결과에 없는 행을 삭제한다
```

**2단계를 리포지토리가 아니라 순수 자바 함수로 두어야 아래 테스트가 성립한다.** 윈도우 안
스냅샷 시각은 하루 두 개 수준이라 1단계 결과가 작다.

**3단계의 삭제 조건은 `(마켓, 시각)` 쌍으로 건다.** 시각만으로 걸면 마켓 구분이 사라진다.
결과는 "덜 지운다"는 안전한 방향이지만, ①에서 마켓별로 판정해놓고 삭제는 마켓을 안 보는
상태가 되어 실삭제 전환 때 다시 뒤집어야 한다.

## 바뀌는 자리

같은 변경을 두 테이블에 똑같이 적용한다.

| | 무엇 |
|---|---|
| `SectorPriceSnapshotService` | 상수, 호출부, 로그 |
| `MarketMapCategoryChangeRateSnapshotService` | 같음 |
| `SectorPriceSnapshotRepositoryImpl` | `targetPredicate`를 윈도우 latest 기준으로. **위 주석의 "30일"도 고친다 — 이미 10일이다** |
| `MarketMapCategoryChangeRateSnapshotRepositoryImpl` | 같음 |
| `SectorPriceSnapshotRepositoryCustom` | 시그니처, **그리고 `SnapshotRetentionSummary` record에 새 로그용 필드 추가** |
| `MarketMapCategoryChangeRateSnapshotRepositoryCustom` | 같음 (이쪽에도 같은 record가 따로 선언돼 있다) |
| `SnapshotRetentionScheduler` | **호출부는 안 바뀐다.** 클래스 Javadoc의 `장마감(15:30) 시각이 아닌 데이터를 지운다`를 고친다 |
| `SectorPriceSnapshotServiceTest` | **`deleteSnapshotsBefore(eq(cutoff), eq(LocalTime.of(15, 30)))`를 단언하고 있어 반드시 깨진다** |
| `MarketMapCategoryChangeRateSnapshotServiceTest` | 같은 구조 |

## 반드시 지킬 것

**`market-monitor.retention.dry-run`은 `true` 그대로 둔다.** 이 PR은 아무것도 지우지 않는다.
실삭제 전환은 별도 작업이고, 위 로그로 며칠 관찰한 뒤에 한다.

## 테스트

2단계(순수 자바 함수)에 대해:

- 윈도우 안에 15:30과 15:35가 둘 다 있으면 **15:35만 고른다**
- 15:35가 없고 15:30만 있으면 **15:30을 고른다**
- 윈도우가 통째로 비면 **그 날짜는 결과에 없다**
- **마켓별로 다른 시각이 잡히는 경우** — KOSPI는 15:35, KOSDAQ은 15:30
- 윈도우 밖 시각(15:25, 15:40)은 후보에 안 들어온다

서비스 레벨에서:

- **드라이런일 때 삭제가 호출되지 않는다**
- 보존 시각과 「보존할행없음」이 로그에 실린다

**cutoff 이후 데이터를 안 건드린다는 것**은 1단계·3단계 술어에 `snapshotTime.before(cutoff)`가
들어가는지로 확인한다. 위에 적은 대로 술어 자체는 이 레포에서 테스트할 수단이 없으니, **PR
설명에 그 조건이 어느 줄에 있는지 명시**한다.

앞의 것들은 "무엇을 남기나"를 보고, 뒤의 것들은 "무엇을 안 지우나"를 본다. 되돌릴 수 없는 쪽은
후자다.

---

# 공통

- **커밋은 1부와 2부를 나눈다**
- 완료 기준은 `docs/rules/process.md`를 따른다. 축약하지 마라 —
  `compileJava compileTestJava` / `test` / **`spotlessApply` 실행 후** `spotlessCheck` /
  경고 증가 없음(`-Werror`라 경고가 곧 실패다) / PR 설명
- `docs/` 아래는 이 파일 삭제 외에 건드리지 않는다
- 이 파일은 마지막 커밋에서 지운다
- PR을 올린 뒤 멈춘다. 병합은 사용자가 한다

## PR 설명에 반드시 담을 것

- **1부 검증 체크리스트** (위 「검증」의 4항목, 복붙 가능한 명령 포함). 구현 세션이 실행할 수
  없으므로 이게 유일한 검증 경로다
- 2부에서 `snapshotTime.before(cutoff)` 조건이 들어간 위치
- 지시서에 없어서 스스로 정한 것이 있으면 그 목록
