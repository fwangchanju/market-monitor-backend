# 백엔드 정비 작업 지시서

> **임시 문서다.** 마지막 단계까지 끝나면 이 파일을 삭제한다.
>
> 시니어 리뷰 결과 + 사용자가 확정한 내용 + 별도 검증 에이전트의 지적을 반영한 문서다.
> **1단계부터 순서대로** 진행한다. 단계를 건너뛰지 않는다.

> **⚠️ 라인 번호는 참고용이다.** 1B 단계의 `spotlessApply`가 23개 파일의 라인을 밀어버린다.
> 위치는 **메서드명·식별자 문자열**로 찾는다. 라인 번호가 어긋나 있어도 그것만으로 "지시서와 코드가
> 모순"이라고 판단하지 않는다.

---

## 이 작업의 배경

지금까지 이 프로젝트는 **사용자가 코드를 한 줄씩 읽으면서** 진행됐다. 이제 그러지 않는다.

> **사람이 리뷰를 안 하면, 그 자리를 자동 검증이 메워야 한다.**

안전망(CI·테스트)을 버그 수정보다 먼저 만드는 이유가 이것이다. 안전망 없이 코드를 고치면 고쳤는지
망가뜨렸는지 아무도 모른다.

---

## 역할

| 역할 | 현재 담당 | 하는 일 |
|---|---|---|
| **설계·문서·리뷰** | Opus | 문서 작성, PR 리뷰. `docs/**`와 `CLAUDE.md`는 이쪽만 수정한다 |
| **구현** | Sonnet | 이 지시서를 읽고 **코드만** 작성. 문서는 건드리지 않는다 |
| **결정** | 사용자 | PR 병합, 배포 시점 |

**구현 세션은 `docs/` 아래 문서를 수정하지 않는다.** 작업 중 알게 된 것, 지시서와 실제가 달랐던
부분은 전부 **PR 설명에** 적는다. 문서 반영은 리뷰 세션이 한다.

예외: 이 파일(`work-plan.md`)의 마지막 삭제 작업만 마지막 단계에서 수행한다.

---

## 작업 규칙

- **한 PR = 하나의 단계.** 통으로 끝내고 PR을 올린 뒤 **멈춘다**
- 다음 단계는 **직전 PR이 main에 병합된 뒤** 최신 main에서 브랜치를 따서 시작한다
- 단계 중간에 사용자에게 확인을 구하지 않는다
- **PR 병합은 하지 않는다.** 사용자가 한다
- 리뷰 코멘트가 달리면 같은 PR에 반영해서 push한다. 왕복은 1회

### 판단 범위 — 이건 중요하다

**이 지시서에 조치가 적혀 있으면 그대로 따른다. 더 나은 방법이 떠올라도 바꾸지 않는다.**

여기 적힌 조치들은 대안을 검토한 끝에 정해진 것이고, 근거가 `docs/decisions.md`에 있다. 구현 시점에
다시 판단하면 이미 접은 선택지로 되돌아간다.

- **설계 판단**(무엇을 어떤 방식으로) — 이 지시서가 정한다. **재판단하지 않는다**
- **구현 세부**(변수명, 메서드 분리, 테스트 케이스 구성) — 스스로 정한다. PR에 설명 안 해도 된다
- **지시서에 없는 것** — 기존 코드의 같은 패턴을 따르고, PR 설명에 한 줄 적는다
- **지시서대로 하면 안 될 것 같으면** — 임의로 바꾸지 말고 **멈추고 확인한다**

**멈추고 확인해야 하는 경우 (둘뿐):**
- 지시서와 실제 코드가 **구조적으로** 모순돼서 진행이 불가능할 때(라인 번호 불일치는 해당 없음)
- 데이터 삭제·마이그레이션 등 되돌리기 어려운 작업의 범위가 지시서에 적힌 것보다 커질 때

### 완료 기준 (모든 단계 공통)

1. `./gradlew compileJava compileTestJava` 통과
2. `./gradlew test` 통과 (매뉴얼 테스트 제외)
3. `./gradlew spotlessApply` 실행 후 `spotlessCheck` 통과
4. 컴파일 경고가 작업 전보다 **늘지 않았을 것**
5. PR 설명에 변경 내용·이유·검증 방법이 적혀 있을 것

### 커밋

- PR을 올리기 전에 브랜치 안에서 커밋을 **의미 단위 1~3개**로 정리한다
- **`spotlessApply`로 인한 포맷 변경은 반드시 별도 커밋으로 분리한다.** 포맷 위반이 **23개 파일**
  (main 21 + test 2)이라, 섞이면 실제 작업 diff가 묻혀서 리뷰가 불가능해진다
- squash merge는 쓸 수 없다(`promote-main`이 `HEAD^2`를 사용). 정리는 **PR 올리기 전 브랜치 안에서**

---

## 절대 건드리지 말 것

**아래는 "죽은 코드"가 아니다. 의도적으로 남긴 것이다. 삭제하거나 "정리"하지 마라.**

| 대상 | 위치 |
|---|---|
| 주석 처리된 메서드 본문 4개 | `MarketQueryService` |
| 주석 처리된 스케줄 메서드 | `CollectionScheduler.collectMarketDataHourly` 등 |
| 주석 처리된 startup 단계 | `StartupRunner.run()` |
| `ImageStitcher` 클래스 | `domain/notification/service/` |
| `okhttp` / `okhttp-urlconnection` 의존성 | `build.gradle` |
| `domain/krx` 전체 | — |
| `CollectionChecker`, `CollectionScheduler.isHoliday()` | 지연 감지 기능용. `docs/backlog.md` 참고 |

**⚠️ 테스트 중 태깅하면 안 되는 것**

`domain/stock/collector/StockInfoCollectorTest`는 **CI에서 반드시 돌아야 하는 단위 테스트**다.
매뉴얼 테스트가 아니다. `@Tag("manual")`을 붙이지 마라.
(매뉴얼 테스트 두 개가 비슷한 이름이라 헷갈리기 쉽다 — 실제 경로는 아래 1B-1 참고)

**`domain/access`(IP 화이트리스트·관리자 토큰)는 이번 정비 대상이 아니다.** 로그인 기능으로 통째로
대체될 예정이라 지금 다듬으면 낭비다(`docs/backlog.md`).

---

# 1A단계 — 배포 워크플로

브랜치명 예: `claude/ci/deploy-split`

> **왜 1B와 나누나**: 성격이 다르고(워크플로 vs 애플리케이션 코드) 되돌릴 이유도 다르다.
> `docs/rules/process.md`의 "성격이 다른 작업을 한 PR에 섞지 않는다"를 따른다.
> **1A를 먼저 한다** — 이게 병합돼야 이후 모든 병합이 배포를 일으키지 않는다.

## 1A-1. 배포를 병합에서 분리

`release.yml`의 `promote-main` job에서 **배포 스텝만 제거**한다.

```
promote-main job (main 병합 시)
  ✅ 머지된 브랜치 tip(HEAD^2) 찾기
  ✅ :sha-<그커밋> → :main 재태깅            ← 남긴다
  ❌ Deploy to server 1 (application)        ← 제거
  ❌ Health check                            ← 제거
  ✅ 오래된 GHCR 이미지 정리                  ← 남긴다
```

**승격(재태깅)은 반드시 남긴다.** 이걸 빼면 `:main` 태그가 안 만들어져서 수동 배포 시 처음부터 다시
빌드하게 된다(빌드 2회).

**함께 정리할 잔여물**
- `promote-main`의 `if` 조건에 남는 `needs.changes.outputs.application_scripts == 'true'`는 배포
  스텝이 사라지면 **아무 일도 안 하는 job을 띄우는 조건**이 된다. 제거한다
- job 상단 설명 주석과 배포 스텝 근처 주석이 "배포한다"고 되어 있다. 사실과 맞게 고친다

**⚠️ PR 설명에 반드시 적을 것 — 동작 변화**

지금까지는 `infra/scripts/*.sh`나 `docker-compose.yml`만 바꿔도 main 병합 시 자동 재배포되면서 서버의
스크립트가 갱신됐다. **이제는 다음 수동 배포 전까지 서버에 반영되지 않는다**(서버의
`git fetch && reset --hard`가 배포 스크립트 실행 시점에 일어나므로).

또한 **이 PR 자체가 병합될 때는 이미 새 워크플로가 적용되어 자동 배포가 일어나지 않는다.** 즉 이
PR로 바뀐 배포 스크립트는 사용자가 수동 배포를 한 번 돌려야 서버에 들어간다. 이 사실도 적는다.

## 1A-2. 브랜치 시험 배포의 이미지 확인 구멍 막기

`application` job(수동 배포)의 "이미지가 이미 있으면 스킵"은 **태그 존재 여부만** 본다. 브랜치에 새
커밋을 push하고 빌드가 도는 중에 수동 배포하면, `:branch-<이름>` 태그가 **이전 커밋 이미지**를 가리켜
옛 코드가 조용히 배포된다.

**조치: 브랜치 배포만 sha 기준으로 바꾼다. main 배포는 지금처럼 `:main` 태그를 쓴다.**

| Use workflow from | 배포에 쓰는 태그 | 없으면 |
|---|---|---|
| `main` | `:main` (승격이 보증한다) | 빌드 |
| 그 외 브랜치 | **`:sha-${{ github.sha }}`** | 빌드 |

**main을 sha 기준으로 바꾸면 안 된다.** `build` job은 `github.ref != 'refs/heads/main'` 조건이라
**main 커밋으로는 이미지가 만들어진 적이 없다.** main의 `github.sha`는 머지 커밋이므로 100% 캐시
미스가 되어 배포마다 전체 재빌드가 걸린다. 1A-1에서 승격을 살려둔 이유가 정확히 이것이다.

브랜치에서 발생하던 구멍은 브랜치 쪽만 sha로 바꾸면 닫힌다.

**빌드가 필요해진 경우** — `:sha-<커밋>`으로 푸시하고 `:branch-<이름>` 별칭도 함께 갱신한다.
별칭을 안 밀면 그 태그가 영구히 낡는다.

**⚠️ PR 설명에 반드시 적을 것**

바뀐 **배포 절차와 롤백 절차**를 사용자가 그대로 따라 할 수 있는 형태로 적는다.
`docs/operations.md`는 구현 세션이 못 고치므로, 이 PR 설명이 병합 후 문서가 갱신될 때까지 유일한
안내가 된다.

---

# 1B단계 — 안전망

브랜치명 예: `claude/ci/safety-net`

**이 작업 전체에서 가장 중요한 단계다.** 이후 모든 수정의 검증 근거가 된다.

## 1B-1. 매뉴얼 테스트를 일반 테스트에서 분리 (최우선)

`src/test`의 10개 중 6개가 **실제 외부 API를 호출하고 실제 텔레그램 메시지를 발송하는** 수동
검증용인데, `@Disabled`도 태그도 없이 일반 `test` 태스크에 포함돼 있다.

**대상 (FQCN — 디렉터리 구조가 일관되지 않으니 클래스명으로 찾는다)**

```
dev.eolmae.marketmonitor.collector.FullDataCollectionTest
dev.eolmae.marketmonitor.collector.KiwoomApiVerificationTest
dev.eolmae.marketmonitor.api.KrxLoginTest
dev.eolmae.marketmonitor.domain.renderer.client.ScreenshotClientManualTest
dev.eolmae.marketmonitor.domain.notification.service.MarketMapTelegramReportSenderManualTest
dev.eolmae.marketmonitor.domain.notification.service.TelegramReportCycleManualTest
```

**앞의 두 개는 `domain.stock.collector`가 아니라 `collector` 패키지에 있다.**
`domain.stock.collector`에 있는 `StockInfoCollectorTest`는 **단위 테스트이므로 태깅 대상이 아니다.**

조치:
- 위 6개에 `@Tag("manual")` 부여
- `build.gradle`의 `test` 태스크에서 `excludeTags 'manual'`
- 매뉴얼 테스트만 실행하는 별도 태스크 추가 (`includeTags 'manual'`)
- 각 매뉴얼 테스트 Javadoc에 실행 조건과 실행 명령 명시

**⚠️ 서버 스크립트가 깨진다 — PR 설명에 반드시 적을 것**

`excludeTags`는 JUnit 엔진 레벨 필터라 `--tests`보다 먼저 걸린다. `test` 태스크에 `excludeTags`를
걸면 아래는 Gradle의 "No tests found for given includes"로 **빌드가 실패한다.**

```bash
./gradlew test --tests "*.TelegramReportCycleManualTest"    # ← 이제 실패
```

사용자가 서버에서 쓰는 스크립트가 이 형태다. **새 태스크 기준의 실행 명령을 PR 설명에 명시**해야
사용자가 서버 스크립트를 고칠 수 있다.

## 1B-2. CI 추가

`release.yml`은 **배포 전용**이고 테스트·빌드 검증이 **아예 없다.** PR을 올려도 아무것도 검증되지 않는다.

- `.github/workflows/ci.yml` 신규 생성 (`release.yml`은 건드리지 않는다)
- 트리거: PR(대상 main) + main push
- 수행: `./gradlew spotlessCheck compileJava compileTestJava test`
- DB·외부 API 없이 돌아야 한다 → 1B-1이 선행 조건
- Gradle 캐시 사용

## 1B-3. 핵심 로직 테스트 작성

`docs/rules/testing.md`를 먼저 읽는다.

9,397줄에 대해 실질 단위 테스트가 4개뿐이다. **이후 단계에서 이 코드들을 고칠 것이므로, 고치기 전에
현재 동작을 고정해두는 것이 목적이다.**

**5개 전부 작성한다.** PR 설명에 각 대상별로 어떤 케이스를 썼는지 목록으로 남긴다.

**1) `CollectionChecker`**

7개 메서드 중 `isWeekend`/`isWeekday`/`previousTradingDay`는 이미 인자를 받는 순수 함수라 지금도
테스트된다. 막힌 건 내부에서 `LocalDateTime.now()`를 부르는 `isTradingTime()`과
`expectedSnapshotTime()` **두 개**다.

**조치: static 유틸로 유지하되, 시각과 설정값을 전부 인자로 받게 바꾼다.**

```java
public static boolean isTradingTime(LocalDateTime now, int startHour, int endHour)
public static LocalDateTime expectedSnapshotTime(LocalDateTime now, int startHour, int endHour, int intervalMinutes)
```

- Spring 빈으로 바꾸지 않는다. `WatchStockBackfillService`의 static 호출 3곳이 깨진다
- 하드코딩된 `8` / `20` / `5`(`COLLECTION_START_TIME` 등)를 제거하고 인자로 대체한다.
  `collect.*` 프로퍼티를 읽어 넘기는 책임은 호출부에 둔다
- `KstClock`에는 `now()`가 없다. `getNowTruncateMinute()`뿐이다. 필요하면 `now()`를 추가한다
- **이 클래스는 2단계 이후 호출부가 없어진다**(2-6 참고). 그래도 `docs/backlog.md`의 "데이터 지연
  감지"에서 그대로 쓸 코드이므로 **삭제하지 않고 테스트만 붙여둔다**

**2) `CategoryRankingTextBuilder.buildRankingText()`** — TOP3 선정, 대분류 필터, 구간 제외, 포맷

**3) `MarketQueryService.getProgramTradingRankings()`** — 종목별 합산·정렬·순위 부여
- **`snapshotTime` 값은 단언하지 않는다.** 2-6에서 바뀔 값이라 단언하면 2단계에서 깨진다

**4) `KiwoomValueParser` / `NumberParser` / `Strings`** — 파싱 유틸

**5) `MarketMapQueryService`의 트리 구성** — `buildCategoryTree`는 private이므로 공개 진입점
(`getCustomMarketMap` / `getDefaultMarketMap`) 경유로 테스트한다. `MarketMapQueryServiceTest`에 이미
3개 케이스가 있으므로, **거기에 더해** 다음을 덮는다.
- 자식 카테고리가 있는 노드의 `totalMarketValue`가 자기 items + 자식 합계인지
- 가격 스냅샷이 없는 종목이 제외되는지
- 빈 트리(카테고리 0개)일 때 빈 리스트가 나오는지

---

# 2단계 — 실제 장애로 이어지는 버그

브랜치명 예: `claude/fix/critical-bugs`

## 2-1. 에러 알림 경로가 에러를 더 크게 만든다 ⚠️

`EscalationNotifier.onEscalation()`이 `telegramClient.sendMessage()`를 호출하고, 실패하면
`TelegramClient`가 `EscalateException`을 던진다. `@EventListener`는 동기라 이 예외가
`EscalationPublisher.report()`를 거쳐 호출부로 역류한다.

```java
// CollectionScheduler.run()
} catch (Exception e) {
    escalationPublisher.report(...);   // ← 여기서 또 예외가 터지면
    success = false;                   // ← 이 줄이 실행되지 않고
}                                      // ← collectMarketData() 전체가 죽는다
```

- 수집기 하나 실패 + 텔레그램 장애 → **그 사이클의 남은 수집기가 전부 스킵**된다
- `GlobalExceptionHandler.handleBusinessException`도 동일하다. 예외 핸들러 안에서 예외가 터지면
  클라이언트는 원인 불명의 500을 받고 원래 에러가 가려진다

**조치**: `EscalationNotifier`에서 예외를 전부 잡아 로그만 남긴다. 알림은 best-effort여야 한다.

## 2-2. 외부 API 타임아웃 없음 → 앱 전체 정지 가능 ⚠️

`ApplicationConfig.restClient()`가 `RestClient.create()`라 connect/read 타임아웃이 무제한이다.
키움 연결이 매달리면 → `KiwoomApiClient.acquire()`의 `synchronized` 락을 쥔 채 무한 대기 → 모든 키움
호출이 영구 블로킹 → `@Scheduled` 기본 풀이 1스레드라 **모든 스케줄 작업이 정지**한다.

**조치: 아래 표대로 빈을 나눈다.**

| 빈 이름 | 쓰는 곳 | connect | read |
|---|---|---|---|
| `restClient` (**`@Primary` 유지**) | `KrxCrawler` 등 나머지 | 3s | 10s |
| `kiwoomRestClient` | `KiwoomApiClient`, `KiwoomTokenManager` | 3s | 10s |
| `telegramRestClient` | `TelegramClient` | 3s | 30s |
| `rendererRestClient` | `ScreenshotClient` | 3s | **90s** |

- **기존 `restClient` 빈을 `@Primary`로 유지**한다. 그래야 `KrxCrawler`(건드리면 안 되는 코드)가
  `@Qualifier` 없이 그대로 주입받는다
- 나머지 4곳(`KiwoomApiClient`, `KiwoomTokenManager`, `TelegramClient`, `ScreenshotClient`)에
  `@Qualifier`를 붙인다
- **렌더러 read 타임아웃은 90초**다. `containers/renderer/server.js`가 `page.goto` 30초 +
  `waitForSelector` 15초를 쓰므로, 10초를 걸면 **일일 리포트 스크린샷이 항상 실패한다**
- 텔레그램은 이미지 여러 장을 멀티파트로 올리므로 30초

**스케줄러 스레드 풀**: `application.properties`에 `spring.task.scheduling.pool.size=3`을 추가한다.

**Spring Boot 4 기준 설정이다.** 타임아웃 설정 API가 Boot 3과 다르다
(`org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder` / `ClientHttpRequestFactorySettings`).
Boot 3 문법으로 쓰면 컴파일이 막히니, 현재 클래스패스에 있는 API를 확인하고 쓴다.

## 2-3. 마켓맵 API 전체를 죽일 수 있는 NPE

`MarketMapQueryService.buildCategoryTree()`의 `Collectors.groupingBy` 키 추출부에서
`stockCategoryMap.get(stockInfo.getStockCode()).getCategoryId()`를 호출한다.
`market_map_stock_category`에 행이 없는 종목이 하나라도 걸리면 NPE → 마켓맵/섹터 API 전부 500.

같은 파일의 `resolveDisplayName()`은 **똑같은 맵을 null 체크한다.** 한 파일 안에서 같은 자료구조를
한쪽은 믿고 한쪽은 안 믿는다.

배정 누락이 생기는 경로: `StockInfoCollector.sync()`의 이벤트는 **신규 종목만** 싣는다. 이미
`stock_info`에 있던 종목이 나중에 일반주가 되면(ETF로 등록됐다가 marketCode가 바뀌는 등) 배정을 영영
못 받는다.

**조치: 조회 경로는 건드리지 않는다. `MarketMapCategoryService.onStockInfoSynced`에서 구멍을 닫는다.**

- 고칠 파일은 **`MarketMapCategoryService`**다. `StockInfoCollector`가 아니다
  - `stock → marketmap` 직접 의존은 순환이고, **바로 그것 때문에 이벤트가 존재한다**
  - `syncStockCategories`는 `MarketMapCategoryService`의 **private 메서드**라 외부에서 호출할 수 없다
- 리스너가 **이벤트 payload(신규 종목)에 더해, "활성 일반주인데 `market_map_stock_category`에 행이
  없는 종목"을 직접 계산**해서 `syncStockCategories`에 넘긴다.
  계산 방식은 `MarketMapCategoryTreeService.findStocksMissingAfterRestore()`와 동일하다
- **`StockInfoCacheService`가 아니라 `StockInfoRepository`에서 직접 조회한다.**
  2-4로 캐시 evict가 커밋 후로 밀리면, 캐시에는 방금 저장한 신규 종목이 없다. 캐시를 쓰면 신규 상장
  종목이 하루 뒤에야 배정되고 그 사이 이 NPE가 그대로 난다
- `StockInfoCollector`가 이벤트에 싣는 내용은 **그대로 둔다**(신규 종목만)
- ETF 자체를 배정 대상으로 삼는 게 아니다. ETF였던 종목이 일반주가 된 순간에만 대상이 된다

**기존 테스트**: `StockInfoCollectorTest`의 두 테스트는 "이벤트 payload에 신규 종목만, ETF/ELW 제외"를
단언한다. `StockInfoCollector`를 안 바꾸므로 **이 단언은 그대로 유지한다.** 새 동작은
`MarketMapCategoryService` 쪽 테스트로 덮는다.

**조회 경로에 null 체크를 넣지 않는다.** 전제가 깨지지 않게 만드는 것이 목적이지, 깨져도 굴러가게
만드는 게 목적이 아니다(`style.md` §5).

## 2-4. 캐시를 커밋 전에 비우는 레이스

`StockInfoCollector.sync()`(`@Transactional`) 안에서 `saveAll` 직후 `stockInfoCacheService.evict()`를
호출한다. evict와 커밋 사이에 다른 스레드가 캐시를 재적재하면 **커밋 전 옛 데이터를 읽어 캐시에
굳힌다.**

**조치: `evict()`를 커밋 후로 옮긴다.**

현재 주석의 의도("핸들러가 최신 캐시를 보게")는 **달성되지 않는 의도**다. 핸들러는 같은 트랜잭션 안에서
돌기 때문에 캐시를 다시 채워도 커밋 전 상태가 굳을 뿐이다. 핸들러 쪽은 2-3에서 리포지토리 직접 조회로
바꾸므로 문제없다.

**⚠️ 트랜잭션 없는 단위 테스트가 깨진다.** `TransactionSynchronizationManager.registerSynchronization()`은
활성 트랜잭션이 없으면 `IllegalStateException`을 던진다. `StockInfoCollectorTest`는 Spring 없이 도는
단위 테스트라 그대로 두면 `sync()` 자체가 터진다.

→ `TransactionSynchronizationManager.isSynchronizationActive()`로 가드하고, 비활성이면 즉시 evict한다.

## 2-5. 키움 5xx·타임아웃이 "파싱 실패"로 보고됨

`KiwoomApiClient.fetch()`의 `catch (RestClientException e)`가 연결 실패·타임아웃·5xx를 전부
`KIWOOM_RESPONSE_PARSE_FAILED`로 보고한다. 그리고 429만 재시도 대상이라 일시적 5xx는 버린다.

**조치**

예외 판정:
- `ResourceAccessException` → 연결/타임아웃
- `HttpServerErrorException` → 5xx (`RestClientResponseException` 계열이라 현재
  `HttpClientErrorException` catch에는 안 걸린다)

| 상황 | ErrorCode | 재시도 |
|---|---|---|
| 연결 실패·타임아웃 | `KIWOOM_CONNECTION_FAILED` (신규) | O |
| 5xx | `KIWOOM_SERVER_ERROR` (신규) | O |
| 429 | `KIWOOM_RATE_LIMIT` (기존) | O |
| 그 외 4xx | `KIWOOM_HTTP_ERROR` (기존) | X |
| 진짜 파싱 실패 | `KIWOOM_RESPONSE_PARSE_FAILED` (기존) | X |

**재시도는 ErrorCode가 아니라 예외 타입으로 결정된다.** 현재
`@Retryable(retryFor = KiwoomRateLimitException.class)`이므로, `BadRequestException`을 던져봐야
재시도되지 않는다.

- 새 시그널 예외 `KiwoomTransientFailureException`을 만든다.
  `KiwoomRateLimitException`과 같은 계열(순수 시그널 타입, `BusinessException`과 무관 — `style.md` §6)
- `retryFor = {KiwoomRateLimitException.class, KiwoomTransientFailureException.class}`
- **`@Recover` 메서드를 추가한다.** 없으면 재시도 소진 시 `ExhaustedRetryException`이 나서 아무 데서도
  안 잡히고 원래 에러가 가려진다. 소진 시 `KIWOOM_CONNECTION_FAILED`로 `BadRequestException`을 던진다
- **재시도 정책 최종값: `maxAttempts = 3`, `backoff = @Backoff(delay = 2000)`.**
  Javadoc이 원래 이 값으로 적혀 있었고 애노테이션만 달랐다. Javadoc을 기준으로 맞춘다

## 2-6. 응답의 스냅샷 시각이 실제 데이터 시각과 다르다

같은 `SnapshotResponse.snapshotTime` 필드에 서비스마다 다른 의미가 담겨 있다.

| 서비스 | 넣는 값 | 조치 |
|---|---|---|
| `MarketQueryService` | **기대 시각** | **실제 시각으로 바꾼다** |
| `MarketMapQueryService` | 실제 시각 | 그대로 |
| `MarketMapCategoryChangeRateSnapshotService` | 실제 시각 | 그대로 |
| `MarketMapStockCategoryService` | 실제 시각 | 그대로 |

`MarketQueryService`는 `latestSnapshotTime`으로 데이터를 조회해놓고 응답 라벨에는 "지금쯤이면 있어야
할" 시각을 담는다. **데이터가 16:05 것인데 화면엔 16:10이 찍힌다.**

**바꿀 곳: `SnapshotResponse`를 만드는 네 메서드**
- `getMarketOverviews()`
- `getInvestorTradingSummaries()`
- `getProgramTradingRankings(List<Market>, ...)`
- `getIndexContribution()`

각각 이미 구한 `latestSnapshotTime`을 그대로 넣는다.

**건드리지 않는 것**
- `toProgramTradingDailyHistoryResponse` — `StockHistoryResponse`이고 `latestSnapshotTime`이 스코프에
  없다. 게다가 이 메서드는 4-1이 "미사용 경고 억제 대상"으로 지정한 죽은 코드다
- 주석 처리된 블록 안의 호출

프론트 변경은 필요 없다. 정상 상황에서는 두 값이 같고, 수집 중이거나 지연됐을 때만 실제 데이터 시각이
찍힌다. 그게 맞는 동작이다.

**이 변경으로 `CollectionChecker.expectedSnapshotTime()`의 호출부가 없어진다. 삭제하지 않는다**
(`docs/backlog.md`의 "데이터 지연 감지"에서 쓸 코드).

## 2-7. 예상 못 한 예외는 알림이 가지 않음

`GlobalExceptionHandler`에 `@ExceptionHandler(Exception.class)`가 없다. NPE 등이 컨트롤러에서 터지면
Spring 기본 처리로 나가고 **텔레그램 알림이 가지 않는다.**

**조치: catch-all 핸들러를 추가하되, 반드시 아래 두 가지를 함께 한다.**

**(1) 접근제어 403과 MVC 내장 예외를 제외한다 — 안 하면 텔레그램이 죽는다**

`domain/access`는 예외 계층에 403이 없어 `ResponseStatusException(FORBIDDEN)`으로 우회하고 있고
(`AccessCheckController`, `AdminTokenService`), nginx는 **모든 요청**에 `auth_request`를 건다
(`infra/nginx.conf`). catch-all을 그냥 추가하면:

- 화이트리스트에 없는 IP의 **모든 요청**이 403 대신 500 → nginx 접근제어 흐름이 깨진다
- 요청 하나하나가 **텔레그램 알림 1건**. 공개 도메인이라 봇 스캔만으로 분당 수십 건
- 2-1로 알림 실패를 삼키게 했으므로 **rate limit에 걸려도 조용히 채널이 죽는다**

같은 문제가 `NoResourceFoundException`(favicon 404), `HttpRequestMethodNotSupportedException`,
`HttpMessageNotReadableException`, `AsyncRequestNotUsableException`(클라이언트 연결 끊김)에도 적용된다.

→ catch-all 진입부에서 `ErrorResponse` 구현체(= `ResponseStatusException`과 MVC 내장 예외 대부분)는
**다시 던져서 Spring 기본 처리로 보낸다.** 또는 `ResponseEntityExceptionHandler`를 상속한다.

**(2) 중복 알림을 억제한다**

같은 예외 클래스 + 같은 메시지는 **5분에 1회만** 알림을 보낸다. 사용자는 로그를 보지 않으므로 폭주를
알아챌 방법이 없다. 억제된 건수는 로그에만 남긴다.

**2-1이 선행돼야 이 핸들러가 안전하다.**

---

# 3단계 — 운영

브랜치명 예: `claude/feat/data-retention`

## 3-1. 스냅샷 정리 배치

`sector_price_snapshot`은 종목 약 2,800개 × 하루 145회 ≈ **하루 80만 행**씩 무한히 쌓인다.
정리 정책이 전혀 없다.

### 스펙 (사용자 확정)

- **대상 테이블 2개**: `sector_price_snapshot`, `market_map_category_change_rate_snapshot`
- **주기**: 매일 **04:00**, `zone = "Asia/Seoul"` 명시 (기존 `CollectionScheduler` 패턴과 동일)
- **보존**: 스냅샷 시각이 **15:30인 데이터만 남기고 나머지 삭제**
- **삭제 조건**: 스냅샷 시각의 일자가 배치 수행 시각 기준 **30일 이전**
- **15:30이 없는 날**: 그날이 통째로 사라져도 무방(사용자 확인 완료)

### ⚠️ 첫 릴리즈는 드라이런으로 낸다

이 정비에서 **유일하게 되돌릴 수 없는 조치**인데, DB 없는 단위 테스트로는 "15:30만 남기고 지운다"는
술어를 검증할 수 없다. 술어가 반대로 뒤집혀 있어도 CI는 초록색이다.

- `market-monitor.retention.dry-run` 프로퍼티를 두고 **기본값 `true`**
- 드라이런이면 **삭제하지 않고 "지울 대상 건수"만 조회해서 `log.info`로 남긴다**
- 실삭제 전환은 사용자가 프로퍼티를 바꿔 배포하는 것으로 한다. 코드 변경 없이 전환 가능해야 한다
- PR 설명에 **"드라이런으로 배포됨. 다음날 로그에서 건수를 확인한 뒤 프로퍼티를 바꿔 실삭제로
  전환한다"**와 그 방법을 적는다

### 구현 제약

- **벌크 DELETE로 한다.** `@Modifying @Query`(JPQL 또는 네이티브). `deleteAll`이나 파생
  `deleteBy...`는 **금지** — 엔티티 80만 건을 로드해서 한 건씩 지우게 되고, 컴파일·테스트는 통과한 채
  새벽 4시에 DB만 죽는다
- 삭제 메서드에 **`@Transactional`(readOnly 아님)을 명시**한다. 두 서비스 모두 클래스 레벨이
  `@Transactional(readOnly = true)`라 안 붙이면 런타임에만 터진다
- **cutoff 경계**: `snapshot_time < (오늘 KST − 30일)의 00:00`. 즉 30일째 되는 날의 데이터는 남긴다
- **15:30 판정**: 시각 부분이 15:30이 아닌 행만 삭제한다
- 삭제(또는 드라이런 조회) **건수를 테이블별로 `log.info`**에 남긴다. 사용자가 동작을 확인할 유일한
  수단이다
- **실패 시 `EscalationPublisher.report(EscalateException.wrap(...))`로 알림**을 보낸다.
  `@Scheduled` 메서드가 그냥 예외를 던지면 Spring이 로그만 찍고 끝나서, 매일 조용히 실패해도 아무도
  모른다

### 패키지 배치

두 테이블이 서로 다른 도메인에 걸쳐 있다. `docs/architecture.md`의 "여러 도메인에 걸치는 배치 작업"
예시대로 한다.

```
domain/stock/service/SectorPriceSnapshotService
    └ 삭제(또는 카운트) 메서드
domain/marketmap/service/MarketMapCategoryChangeRateSnapshotService
    └ 삭제(또는 카운트) 메서드
domain/stock/scheduler/  에 새 스케줄러 클래스
    └ 위 둘을 호출하는 얇은 조율 메서드 + 04:00 cron
```

기존 `CollectionScheduler`에 넣지 않는다. 성격(수집 vs 정리)이 다르고 주기도 다르다.

### 검증

- **cutoff 계산을 순수 함수로 분리해 단위 테스트**한다(경계값 포함)
- 리포지토리 호출을 Mockito로 검증한다(드라이런일 때 삭제 메서드가 호출되지 않는 것 포함)
- 실제 삭제 술어는 드라이런 로그로 확인한다

### 인덱스는 추가하지 않는다

`market_map_category_change_rate_snapshot`에 `snapshot_time` 단독 인덱스가 없어 배치가 풀스캔한다.
하지만 인덱스를 추가하려면 `V1__create_schema.sql`을 고쳐야 하고, 그러면 운영 DB checksum 대응 절차를
밟아야 한다(`docs/operations.md`). 새벽 배치 속도를 위해 그 비용을 치르지 않는다.
성능 문제가 실제로 보이면 그때 한다(`docs/backlog.md`).

## 3-2. 수집 시간 설정 이중화 해소

`application.properties`의 `collect.start-hour` / `end-hour` / `interval-minutes`를
`CollectionScheduler`는 `@Value`로 읽는데, `CollectionChecker`는 `8` / `20` / `5`를 **하드코딩**한다.

1B-3에서 `CollectionChecker`를 "설정값을 인자로 받는" 형태로 바꾸므로 하드코딩 상수는 이미 사라진다.
**이 단계에서는 남은 것만 처리한다.**

- `application.properties`의 주석 `# 자바 코드에서는 안 읽음`은 **사실이 아니다**
  (`CollectionScheduler`가 읽고 있다). 주석을 고친다
- `CollectionChecker` 호출부가 `collect.*` 값을 넘기고 있는지 확인한다.
  2-6 이후 `expectedSnapshotTime()` 호출부는 없어지므로, 남은 호출부는 `WatchStockBackfillService`뿐이다

---

# 4단계 — 정리

브랜치명 예: `claude/refactor/cleanup`

**"절대 건드리지 말 것" 절을 먼저 다시 읽는다.**

## 4-1. 컴파일 경고 0 만들고, 재발을 CI가 막게 한다

errorprone 경고가 18개 상시로 떠 있다. 경고가 늘 깔려 있으면 **새로 생긴 진짜 문제가 묻힌다.**

대부분이 의도적 보존 코드에서 나온다. **코드는 그대로 두고 경고만 억제한다.**

- `@SuppressWarnings("UnusedVariable")` 등 + **왜 남겨두는지 한 줄 주석**
- 주석이 중요하다. 다음에 이 코드를 보는 쪽이 "안 쓰네, 지우자"로 판단하지 않게 하는 것이 목적이다

경고 목록(참고):
```
StartupRunner: getWatchStockCache, syncHoldings, watchStockBackfillService
DailyMarketReportSender: telegramClient, telegramProperties, marketMapCategoryRankingTelegramReportSender
MarketQueryService: toProgramTradingDailyHistoryResponse, 미사용 리포지토리 4개, 미사용 파라미터 4개
CollectionScheduler: isHoliday(date) 파라미터
Strings / KiwoomValueParser: InlineTrivialConstant
```

`CollectionScheduler.isHoliday()`는 항상 false를 반환하는 **미구현** 상태다(`TODO(#38)`).
**이번에 구현하지 않는다.** TODO는 유지하고, 억제 사유 주석에 "미구현"임을 명시한다.

**마지막 조치 — 경고가 다시 쌓이지 않게 한다.**

경고를 0으로 만든 뒤, `build.gradle`에서 **경고를 빌드 실패로 승격**시킨다(`-Werror` 또는 errorprone의
동등 설정). 그래야 다음 PR에서 경고가 생기면 CI가 잡는다. 완료 기준 4번("경고가 늘지 않았을 것")은
지금 아무도 검증하지 않는 항목이고, 이게 그걸 자동 검증으로 바꾸는 유일한 방법이다.

`compileTestJava`는 이미 errorprone이 꺼져 있으므로 그대로 둔다.

## 4-2. `commons-lang3` 의존성 제거

사용처 0건. `docs/rules/style.md` §13이 "미사용"으로 명시한 라이브러리다.

## 4-3. `KstClock` 정리

`LocalDateTime.now(Zone.KST.zoneId())` 직접 호출이 **57곳**이고, 이를 모으려고 만든 `KstClock`은
1곳에서만 쓰인다.

**조치: `KstClock`은 유지한다. 삭제하지 않는다. 다만 57곳에 기계적으로 퍼뜨리지도 않는다.**

| 어디 | 처리 |
|---|---|
| 엔티티의 `createdAt`/`updatedAt` (엔티티 20개, 대입 51곳) | **손대지 않는다.** JPA Auditing 전환은 이번 범위에서 제외(`docs/backlog.md`) |
| `CollectionScheduler` 4곳 (로그용, 비활성 메서드) | 그대로 |
| 수집기 2곳 (저장 시각) | 그대로 |
| `CollectionChecker` | **1B-3에서 이미 처리됨** |

`KstClock`은 "현재 시각을 만드는 경계"라는 역할만 갖는다. 로직은 시각을 인자로 받는 순수 함수로 두고,
`KstClock`은 그 값을 만드는 자리에서만 쓴다.

## 4-4. 그 밖의 일관성 정리

| 항목 | 위치 | 조치 |
|---|---|---|
| 4xx를 `log.error`로 기록 | `GlobalExceptionHandler.handleBusinessException` | 400/404/409는 `warn` 이하로. ERROR는 실제 장애만 |
| 봇 토큰이 로그에 남음 | `TelegramClient` | 아래 참고 |
| `ObjectMapper` 빈의 정체 | `ApplicationConfig` | 아래 참고 |

**봇 토큰 마스킹**

토큰이 새는 경로는 URL을 만드는 `botUrl()`이 아니라 **예외 cause의 메시지**다.
`ResourceAccessException`의 메시지에 전체 URI(토큰 포함)가 들어가고, 그게
`EscalationPublisher.report()`의 `ESCALATION_LOG.error(logMessage, e)`로 **스택트레이스째** 찍힌다.
알림 메시지에도 `getCauseMessage()`를 통해 들어간다.

→ **`TelegramClient`에서 예외를 감쌀 때 cause의 메시지를 마스킹한다.** 마스킹 유틸은
`common/util/`에 둔다(`style.md` §13의 "상수 공유 금지"에 따라 다른 클래스의 private 상수를 노출하지
않는다). 로그 파일의 스택트레이스까지 덮으려면 감싼 예외에 원본 cause를 그대로 달지 않아야 한다 —
원본 메시지를 마스킹한 새 예외를 cause로 단다.

**`ObjectMapper` 빈**

빈 이름을 `internalObjectMapper`로 바꾸고 JavaTimeModule을 등록한다. Spring Boot 4는 웹 직렬화에
Jackson 3(`tools.jackson`)을 쓰는데 이 빈은 Jackson 2(`com.fasterxml`)라 **웹 레이어에 아무 영향이
없는데 그렇게 보인다.** Jackson 3 전환은 비활성 코드(`KrxCrawler`)까지 건드려야 해서 하지 않는다.

주입받는 3곳(`KrxCrawler`, `TelegramClient`, `MarketMapCategoryTreeService`)은 타입 주입이라 이름만
바꾸면 그대로 동작한다.

**⚠️ PR 전 필수 검증**: 빈 이름 변경 후 **로컬에서 앱을 기동해 성공을 확인**하고 그 사실을 PR에
적는다. 자동 검증이 없다(`@SpringBootTest`는 전부 매뉴얼로 빠진다). 빈 이름 변경으로
`NoUniqueBeanDefinitionException`이 나면 **앱이 기동조차 못 한다.**

---

# 마지막 단계 — 문서 마무리

**설계·문서 세션이 수행한다.** 구현 세션은 관여하지 않는다.

- `docs/rules/style.md` **전면 재검토** — 225줄 중 상당수가 사용자가 승인한 적 없는 규칙이다.
  1A~4단계에서 실제로 바뀐 내용을 반영하고, 코드와 어긋나거나 근거가 약한 항목을 정리한다.
  판단이 애매한 항목은 목록으로 뽑아 사용자 확인을 받는다
- `docs/rules/testing.md` 확정 — 실제 작성한 테스트 반영
- `docs/architecture.md` 갱신
- `docs/operations.md` 갱신 — **1A로 바뀐 배포·롤백 절차 반영(우선순위 높음. 1A 병합 직후 즉시)**
- `docs/decisions.md` / `docs/backlog.md` 갱신 — 각 PR에서 나온 판단 회수
- **`docs/work-plan.md`(이 파일) 삭제**

---

# 이번 작업에서 다루지 않는 것

배경과 구상은 `docs/backlog.md`에 있다.

- **보안 모델 전반** — 로그인 기능 도입으로 통째로 대체될 영역
- **로그인 기능** — 정비 후 첫 신규 기능
- **공휴일 판정 구현** (`TODO(#38)`)
- **데이터 지연 감지** — 공휴일 판정이 선행. 프론트 작업도 필요
- **JPA Auditing 전환** — 엔티티 20개(대입 51곳)의 동작 변경인데 DB 없는 CI로는 검증 불가.
  하나라도 빠뜨리면 프로덕션 INSERT가 NOT NULL 위반으로 실패한다
- **관심종목(WatchStock) 구조 정리**
- **대량 insert 배치화**
- **`snapshot_time` 인덱스 추가** — `V1` 수정 부담
- **프론트엔드 레포 정비** — 백엔드 갈무리 후
