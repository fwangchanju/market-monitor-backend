# 백엔드 정비 작업 지시서

> **임시 문서다.** 5단계까지 끝나면 이 파일을 삭제하는 것이 마지막 작업이다.
>
> 시니어 개발자가 코드 전반을 리뷰한 결과와, 그에 대해 사용자가 직접 정정·확정한 내용을 담고 있다.
> **1단계부터 순서대로** 진행한다. 단계를 건너뛰지 않는다.

---

## 이 작업의 배경

지금까지 이 프로젝트는 **사용자가 코드를 한 줄씩 읽으면서** 진행됐다. 이제 그러지 않기로 했다.

그래서 이 정비의 핵심은 하나다.

> **사람이 리뷰를 안 하면, 그 자리를 자동 검증이 메워야 한다.**

1단계(안전망)를 버그 수정보다 먼저 하는 이유가 이것이다. 안전망 없이 코드를 고치면 고쳤는지
망가뜨렸는지 아무도 모른다.

---

## 역할

| 역할 | 하는 일 |
|---|---|
| **설계·문서·리뷰 세션** | 문서 작성, PR 리뷰. `docs/**`와 `CLAUDE.md`는 이쪽만 수정한다 |
| **구현 세션** | 이 지시서를 읽고 **코드만** 작성. 문서는 건드리지 않는다 |
| **사용자** | PR 병합, 배포 시점 결정 |

**구현 세션은 `docs/` 아래 문서를 수정하지 않는다.** 작업 중 알게 된 것, 판단이 갈렸던 지점,
지시서와 실제가 달랐던 부분은 전부 **PR 설명에** 적는다. 문서 반영은 리뷰 세션이 한다.

예외: 이 파일(`work-plan.md`)의 마지막 삭제 작업만 5단계에서 수행한다.

---

## 작업 규칙

- **한 단계 = 브랜치 하나 = PR 하나.** 단계를 통으로 끝내고 PR을 올린 뒤 **멈춘다**
- 다음 단계는 **직전 PR이 main에 병합된 뒤** 최신 main에서 브랜치를 따서 시작한다
- 단계 중간에 사용자에게 확인을 구하지 않는다. 판단이 갈리면 **합리적인 쪽을 선택하고 PR 설명에 이유를
  남긴다**
- **PR 병합은 하지 않는다.** 사용자가 한다
- 리뷰 코멘트가 달리면 같은 PR에 반영해서 push한다. 왕복은 1회

**멈추고 확인해야 하는 경우 (둘뿐):**
- 이 지시서와 실제 코드가 명백히 모순돼서 진행이 불가능할 때
- 데이터 삭제·마이그레이션 등 되돌리기 어려운 작업의 범위가 지시서에 적힌 것보다 커질 때

### 완료 기준 (모든 단계 공통)

1. `./gradlew compileJava compileTestJava` 통과
2. `./gradlew test` 통과 (매뉴얼 테스트 제외 — 1단계에서 분리함)
3. `./gradlew spotlessApply` 실행 후 `spotlessCheck` 통과
4. 컴파일 경고가 작업 전보다 **늘지 않았을 것**
5. PR 설명에 변경 내용·이유·검증 방법이 적혀 있을 것

### 커밋

- PR을 올리기 전에 브랜치 안에서 커밋을 **의미 단위 1~3개**로 정리한다
- **`spotlessApply`로 인한 포맷 변경은 반드시 별도 커밋으로 분리한다.** 현재 포맷 위반이 20개 파일이라,
  섞이면 실제 작업 diff가 묻혀서 리뷰가 불가능해진다
- squash merge는 쓸 수 없다(`promote-main`이 `HEAD^2`를 사용). 그래서 정리는 병합 시점이 아니라
  **PR 올리기 전 브랜치 안에서** 해야 한다

---

## 절대 건드리지 말 것

**아래는 "죽은 코드"가 아니다. 사용자가 나중에 필요할 가능성 때문에 의도적으로 남긴 것이다.
삭제하거나 "정리"하지 마라.**

| 대상 | 위치 |
|---|---|
| 주석 처리된 메서드 본문 4개 | `MarketQueryService.java` 322~430 |
| 주석 처리된 스케줄 메서드 | `CollectionScheduler.java` 117~161 |
| 주석 처리된 startup 단계 | `StartupRunner.java` 35~41 |
| `ImageStitcher` 클래스 | `domain/notification/service/` |
| `okhttp` / `okhttp-urlconnection` 의존성 | `build.gradle` |
| `domain/krx` 전체 | — |

이 코드들 때문에 컴파일 경고가 나는데 **경고를 없애려고 코드를 지우면 안 된다.** 4단계에서
`@SuppressWarnings` + 사유 주석으로 억제한다.

`commons-lang3` 의존성만 제거 대상이다(사용처 0건, 사용자 확인 완료).

**`domain/access`(IP 화이트리스트·관리자 토큰)는 이번 정비 대상이 아니다.** 곧 로그인 기능으로 통째로
대체될 예정이라 지금 다듬으면 낭비다(`docs/decisions.md` 참고).

---

# 1단계 — 안전망

브랜치명 예: `claude/ci/safety-net`

**이 작업 전체에서 가장 중요한 단계다.** 이후 모든 수정의 검증 근거가 된다.

## 1-1. 매뉴얼 테스트를 일반 테스트에서 분리 (최우선)

현재 `src/test`의 10개 중 6개가 **실제 외부 API를 호출하고 실제 텔레그램 메시지를 발송하는** 수동
검증용인데, `@Disabled`도 태그도 없이 일반 `test` 태스크에 포함돼 있다. 누군가 `./gradlew build`를
치면 실 API가 호출되고 실 알림이 나간다.

대상:
- `domain/stock/collector/FullDataCollectionTest`
- `domain/stock/collector/KiwoomApiVerificationTest`
- `api/KrxLoginTest`
- `domain/renderer/client/ScreenshotClientManualTest`
- `domain/notification/service/MarketMapTelegramReportSenderManualTest`
- `domain/notification/service/TelegramReportCycleManualTest`

조치:
- 위 6개에 `@Tag("manual")` 부여
- `build.gradle`의 `test` 태스크에서 `excludeTags 'manual'`
- 매뉴얼 테스트만 실행하는 별도 태스크 추가 — 지금처럼 `--tests`로 지정해 돌리는 운영 방식을 유지할 수
  있게 한다
- 각 매뉴얼 테스트 Javadoc에 실행 조건과 실행 명령 명시

## 1-2. CI 추가

현재 `.github/workflows/release.yml`은 **배포 전용**이고 테스트·빌드 검증 단계가 **아예 없다.**
PR을 올려도 아무것도 검증되지 않는다.

- `.github/workflows/ci.yml` 신규 생성 (`release.yml`은 건드리지 않는다)
- 트리거: PR(대상 main) + main push
- 수행: `./gradlew spotlessCheck compileJava compileTestJava test`
- DB·외부 API 없이 돌아야 한다 → 1-1이 선행 조건
- Gradle 캐시 사용

## 1-3. 배포를 병합에서 분리

`release.yml`의 `promote-main` job에서 **배포 스텝만 제거**한다.

```
promote-main job (main 병합 시)
  ✅ 머지된 브랜치 tip(HEAD^2) 찾기
  ✅ :sha-<그커밋> → :main 재태깅            ← 남긴다
  ❌ Deploy to server 1 (application)        ← 제거
  ❌ Health check                            ← 제거
  ✅ 오래된 GHCR 이미지 정리                  ← 남긴다
```

**승격(재태깅)은 반드시 남긴다.** 이걸 빼면 `:main` 태그가 안 만들어져서, 수동 배포 시 이미지가 없어
처음부터 다시 빌드하게 된다(빌드 2회).

이걸 1단계에 넣는 이유: 이후 2~4단계 PR을 병합해도 배포가 안 일어나서, 사용자가 장 시간에도 마음 놓고
병합할 수 있다.

`nginx` job의 `repository_dispatch`(프론트 자산) 자동 배포는 **그대로 유지한다.**

## 1-4. 수동 배포의 이미지 확인 구멍 막기

`application` job의 "이미지가 이미 있으면 스킵"은 **태그 존재 여부만** 본다. 브랜치에 새 커밋을 push한
직후 빌드가 아직 도는 중에 수동 배포하면, `:branch-<이름>` 태그가 **이전 커밋 이미지**를 가리켜서 옛
코드가 조용히 배포된다.

`promote-main`에는 "빌드 완료까지 최대 600초 대기" 로직이 있는데(`release.yml:151-166`)
`application` job에는 없다. 1-3으로 수동 배포가 주 경로가 되므로 이 구멍을 막는다.

배포 대상 커밋의 `:sha-<커밋>` 이미지가 실제로 존재하는지 확인하는 방식을 권한다.

## 1-5. 핵심 로직 테스트 작성

`docs/rules/testing.md`를 먼저 읽는다.

현재 9,397줄에 대해 실질 단위 테스트가 4개뿐이다. **이후 단계에서 이 코드들을 고칠 것이므로, 고치기
전에 현재 동작을 고정해두는 것이 목적이다.**

우선순위:

1. **`CollectionChecker`** — 수집 시간대 판정, `expectedSnapshotTime()`, `previousTradingDay()`
   - **지금 상태로는 테스트가 불가능하다.** 내부에서 `LocalDateTime.now()`를 직접 호출한다
   - **시각을 주입 가능하게 리팩터링한다.** 인자로 받거나 `java.time.Clock`을 주입한다
   - 기존 `common/util/KstClock`을 활용하거나 정리한다(4단계와 연결)
2. **`CategoryRankingTextBuilder.buildRankingText()`** — TOP3 선정, 대분류 필터, 구간 제외, 포맷
3. **`MarketQueryService.getProgramTradingRankings()`** — 종목별 합산·정렬·순위 부여
4. **`KiwoomValueParser` / `NumberParser` / `Strings`** — 파싱 유틸
5. **`MarketMapQueryService.buildCategoryTree()`** — 트리 구성·합계 계산

---

# 2단계 — 실제 장애로 이어지는 버그

브랜치명 예: `claude/fix/critical-bugs`

**6건 전부 "언제 터져도 이상하지 않은" 문제다.**

## 2-1. 에러 알림 경로가 에러를 더 크게 만든다 ⚠️

`EscalationNotifier.onEscalation()`(`listener/EscalationNotifier.java:26`)이
`telegramClient.sendMessage()`를 호출하고, 실패하면 `TelegramClient.java:43`에서 `EscalateException`을
던진다. `@EventListener`는 동기라 이 예외가 `EscalationPublisher.report()`를 거쳐 호출부로 역류한다.

```java
// CollectionScheduler.java:201-203
} catch (Exception e) {
    escalationPublisher.report(...);   // ← 여기서 또 예외가 터지면
    success = false;                   // ← 이 줄이 실행되지 않고
}                                      // ← collectMarketData() 전체가 죽는다
```

- 수집기 하나 실패 + 텔레그램 장애 → **그 사이클의 남은 수집기가 전부 스킵**된다
- `GlobalExceptionHandler.java:39`도 동일하다. 예외 핸들러 안에서 예외가 터지면 클라이언트는
  ProblemDetail 대신 원인 불명의 500을 받고 원래 에러가 가려진다

**조치**: `EscalationNotifier`에서 예외를 전부 잡아 로그만 남긴다. 알림은 best-effort여야 한다.

## 2-2. 외부 API 타임아웃 없음 → 앱 전체 정지 가능 ⚠️

```java
// config/ApplicationConfig.java:42-44
public RestClient restClient() {
    return RestClient.create();   // connect/read 타임아웃 무제한
}
```

키움·텔레그램·렌더러가 이 빈 하나를 공유한다. 여기에 `KiwoomApiClient.java:86`의
`private synchronized void acquire()`가 겹친다.

키움 연결이 매달리면 → 그 스레드가 락을 쥔 채 무한 대기 → 모든 키움 호출이 영구 블로킹 →
`@Scheduled` 기본 풀이 1스레드라 **모든 스케줄 작업이 정지**한다. 예외가 안 나므로 실패 알림조차 가지
않는다.

**조치**:
- `ClientHttpRequestFactorySettings`로 타임아웃 설정 (connect 3초 / read 10초 수준)
- 용도별 `RestClient` 빈 분리 — 렌더러는 스크린샷 생성이라 read 타임아웃이 더 길어야 할 수 있다
- `@Scheduled` 스레드 풀을 최소 2 이상으로

## 2-3. 마켓맵 API 전체를 죽일 수 있는 NPE

```java
// MarketMapQueryService.java:153
stockInfo -> stockCategoryMap.get(stockInfo.getStockCode()).getCategoryId()
```

`market_map_stock_category`에 행이 없는 종목이 하나라도 걸리면 NPE → 마켓맵/섹터 API 전부 500.

같은 파일 244행 `resolveDisplayName()`은 **똑같은 맵을 null 체크한다.** 한 파일 안에서 같은 자료구조를
한쪽은 믿고 한쪽은 안 믿는다.

배정 누락은 실제로 발생한다:
- `MarketMapCategoryTreeService.findStocksMissingAfterRestore()`(109행)가 "복원 후 배정이 빠진 종목"을
  찾는 코드로 이미 존재한다
- `StockInfoCollector`의 이벤트는 **신규 종목만** 싣는다(`StockInfoCollector.java:90-96`).
  ETF→주권 전환이나 비활성→재활성 종목은 영원히 배정을 못 받는다

**조치**: 배정 없는 종목은 "미분류"로 흘리거나 필터링한다. 전체 API가 죽는 것보다 낫다. 어느 쪽을
택했는지 PR 설명에 남긴다.

## 2-4. 캐시를 커밋 전에 비우는 레이스

```java
// StockInfoCollector.java:83-85  (@Transactional sync() 내부)
stockInfoRepository.saveAll(newStocks);
stockInfoCacheService.evict();      // ← 아직 커밋 전
```

evict와 커밋 사이에 다른 스레드가 캐시를 재적재하면 **커밋 전 옛 데이터를 읽어 캐시에 굳힌다.**
다음 evict까지 낡은 종목 정보가 서빙된다.

**조치**: `@TransactionalEventListener(AFTER_COMMIT)` 또는 `TransactionSynchronization`의
`afterCommit`으로 옮긴다. 단, 현재 주석("핸들러가 최신 상태를 보게 하려고 이벤트 발행 전에 evict")이
설명하는 의도를 깨지 않는지 확인하고, 깨진다면 그 의도를 만족하는 다른 방법을 찾는다.

## 2-5. 키움 5xx·타임아웃이 "파싱 실패"로 보고됨

```java
// KiwoomApiClient.java:124-126
} catch (RestClientException e) {
    throw new BadRequestException(ErrorCode.KIWOOM_RESPONSE_PARSE_FAILED, e, request.apiId());
}
```

`RestClientException`은 연결 실패·타임아웃·5xx를 전부 포함한다. 키움 서버 500이나 네트워크 단절도
"응답 파싱 실패"로 알림이 온다. 그리고 429만 재시도 대상이라 일시적 5xx는 재시도 없이 버린다.

**조치**:
- 예외 종류를 구분해 각각 맞는 `ErrorCode`로 매핑 (연결/타임아웃 / 5xx / 파싱 실패). 필요하면
  `ErrorCode`를 추가한다
- 5xx와 타임아웃도 재시도 대상에 포함
- `KiwoomApiClient.java:46`의 Javadoc이 "최대 3회 재시도(2초 간격)"라고 되어 있으나 실제
  애노테이션은 `maxAttempts=2, delay=1000`이다. 바뀐 정책에 맞춰 Javadoc도 함께 고친다

## 2-6. 예상 못 한 예외는 알림이 가지 않음

`GlobalExceptionHandler`에 `@ExceptionHandler(Exception.class)`가 없다. NPE 등이 컨트롤러에서 터지면
Spring 기본 처리로 나가고 **텔레그램 알림이 가지 않는다.** `EscalateException`을 만든 목적(즉시 인지)이
정작 "예상 못 한 장애"에서 작동하지 않는다.

**조치**: catch-all 핸들러를 추가해 500 ProblemDetail 응답 + 에스컬레이션 알림. **2-1이 선행돼야
이 핸들러가 안전하다.**

---

# 3단계 — 운영 / 성능

브랜치명 예: `claude/feat/data-retention`

## 3-1. 스냅샷 정리 배치 (사용자 확정 스펙)

현재 스냅샷 테이블에 정리 정책이 전혀 없다. `sector_price_snapshot`은 종목 약 2,800개 × 하루 145회
≈ **하루 40만 행**씩 무한히 쌓인다.

**사용자가 확정한 스펙 — 그대로 구현한다:**

- **대상 테이블 2개**: `sector_price_snapshot`, `market_map_category_change_rate_snapshot`
  (다른 스냅샷 테이블은 사이클당 수십 건 수준이라 대상 아님)
- **주기**: 매일 도는 스케줄 배치
- **보존 규칙**: 스냅샷 시각이 **15:30인 데이터만 남기고 나머지는 전부 삭제**
- **삭제 조건**: 스냅샷 시각의 **일자**가 배치 수행 시각 기준 **30일 이전**인 데이터
- **15:30 데이터가 없는 날**: 그날이 통째로 사라져도 무방하다. 사용자 확인 완료 — "15:30이
  중요하고, 그게 없으면 다른 시각 데이터는 무의미하다"
- **배치 수행 시각**: 정해지지 않았다. 수집이 끝난 뒤 새벽(예: 03:00)으로 잡고 PR 설명에 명시한다

**패키지 배치**: 두 테이블이 서로 다른 도메인(`stock`, `marketmap`)에 걸쳐 있다.
`docs/architecture.md`의 "여러 도메인에 걸치는 배치 작업" 예시를 그대로 따른다 — 삭제 로직은 각 도메인
서비스에 두고, 스케줄러가 둘을 호출한다.

**구현 주의**: 30일치를 처음 돌리면 수백만~수천만 행을 한 번에 지운다. 단일 DELETE는 락과 테이블
팽창(bloat) 문제를 일으킨다. **배치 단위로 나눠 삭제**하고(하루치씩 또는 N행씩 반복) 진행 상황을
로그로 남긴다.

## 3-2. 수집 시간 설정 3중화 해소

같은 값이 세 군데에 따로 있고 서로 어긋날 수 있다.

- `application.properties:33-35` — `collect.start-hour=8` / `end-hour=20` / `interval-minutes=5`
- `CollectionScheduler.java:60-61` — `@Value("${collect.end-hour}")`로 읽음
- `CollectionChecker.java:13-15` — `8` / `20` / `5`를 **하드코딩**

`collect.end-hour`를 바꾸면 스케줄러는 따라가지만 `CollectionChecker.expectedSnapshotTime()`은 안
따라가서, **화면에 표시되는 기준 시각만 조용히 틀려진다.**

또한 `application.properties:32`의 주석 `# 자바 코드에서는 안 읽음`은 **사실이 아니다**
(`CollectionScheduler`가 읽고 있다). 주석도 함께 고친다.

**조치**: `CollectionChecker`가 properties 값을 받도록 바꾼다. 1-5에서 이 클래스를 테스트 가능하게
리팩터링하므로 그 구조와 함께 정리한다.

## 3-3. (선택) 대량 insert 성능

`IndexContributionRankingCollector.java:129-131`이 종목당 `save()`를 호출해 5분마다 약 5,600번의 개별
INSERT가 발생한다. PK가 `GENERATED ALWAYS AS IDENTITY`라 Hibernate 배치가 **구조적으로 불가능**하다
(INSERT를 실행해야 번호가 나오므로 한 건씩 보낼 수밖에 없음).

**다만 초당 20건 수준이라 장애로 이어지지 않는다.** 수집 한 사이클이 1~3초 더 걸리는 정도다.
**시간이 남을 때만 하고, 안 해도 무방하다.**

해결 방향(택1): PK를 시퀀스 방식으로 변경(마이그레이션 필요) / 이 경로만 JdbcTemplate 벌크 insert.

**마이그레이션을 택한다면 `docs/rules/process.md`의 "DB 마이그레이션 규칙"을 반드시 지킨다**
(마이그레이션 선-머지, 하위호환 추가만).

---

# 4단계 — 정리

브랜치명 예: `claude/refactor/cleanup`

**"절대 건드리지 말 것" 절을 먼저 다시 읽는다.**

## 4-1. 컴파일 경고 0 만들기

현재 errorprone 경고가 18개 상시로 떠 있다. 경고가 늘 깔려 있으면 **새로 생긴 진짜 문제가 묻힌다.**

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

`CollectionScheduler.isHoliday()`는 항상 false를 반환하는 **미구현** 상태다(`TODO(#38)`). 공휴일에도
수집·발송이 계속되고 있다. **이번에 구현하지 않는다.** TODO는 유지하고, 억제 사유 주석에 "미구현"임을
명시한다.

## 4-2. `commons-lang3` 의존성 제거

`build.gradle`. 사용처 0건. 사용자 확인 완료.

## 4-3. `KstClock` 정리

26곳에서 `LocalDateTime.now(Zone.KST.zoneId())`를 직접 호출하는데, 이를 모으려고 만든
`common/util/KstClock`은 **1곳에서만 쓰인다.**

문제는 미관이 아니라 **테스트 가능성**이다(1-5에서 `CollectionChecker`가 이것 때문에 막힌다).

**조치**: 1단계에서 정한 방향(Clock 주입 등)에 맞춰 통일한다. 엔티티의 `createdAt/updatedAt` 세팅은
4-4에서 별도 처리하므로 여기서는 제외한다.

## 4-4. JPA Auditing 도입

엔티티 8개, 약 20곳에서 `this.updatedAt = LocalDateTime.now(Zone.KST.zoneId());`를 손으로 넣고 있다.

**조치**: `@EnableJpaAuditing` + `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate` /
`@LastModifiedDate`로 전환한다. KST 기준을 유지해야 하므로 `DateTimeProvider` 빈으로 KST를 지정한다.

## 4-5. 그 밖의 일관성 정리

| 항목 | 위치 | 조치 |
|---|---|---|
| 4xx를 `log.error`로 기록 | `GlobalExceptionHandler.java:41` | 400/404/409는 `warn` 이하로. ERROR는 실제 장애만 |
| `isAllowedAdmin` 캐시 없음 | `AllowedIpAccessService.java:27` | **하지 않는다.** `domain/access`는 로그인 도입으로 대체될 예정 |
| 봇 토큰이 URL에 노출 | `TelegramClient.java:107` | 연결 실패 시 예외 메시지에 전체 URL이 실려 **ESCALATION 로그에 봇 토큰이 남는다.** 로그·알림 메시지에서 토큰을 마스킹 |
| `ObjectMapper` 빈의 정체 | `ApplicationConfig.java:47` | Spring Boot 4는 웹 직렬화에 Jackson 3(`tools.jackson`)을 쓰는데 이 빈은 Jackson 2(`com.fasterxml`)다. **웹 레이어에 아무 영향이 없는데 그렇게 보인다.** 전용 용도임을 주석으로 명시하거나 Jackson 3으로 통일한다. JavaTimeModule이 없어 날짜 필드를 쓰면 터지는 상태인 것도 함께 처리 |

---

# 5단계 — 문서 마무리

브랜치명 예: `claude/docs/rules-overhaul`

**이 단계는 설계·문서 세션이 수행한다.** 구현 세션은 `work-plan.md` 삭제만 하거나, 아예 관여하지
않는다.

- `docs/rules/style.md` **전면 재검토** — 현재 225줄 중 상당수가 사용자가 승인한 적 없는 규칙이다.
  0~4단계에서 실제로 바뀐 내용을 반영하고, 코드와 어긋나는 항목·근거가 약한 항목을 정리한다.
  판단이 애매한 항목은 목록으로 뽑아 사용자 확인을 받는다
- `docs/rules/testing.md` 확정 — 실제 작성한 테스트를 반영
- `docs/architecture.md` 갱신 — 정리 후 구조 반영
- `docs/operations.md` 갱신 — 1-3/1-4로 바뀐 배포 절차 반영
- `docs/decisions.md` 갱신 — 각 PR에서 나온 판단 중 남길 것 회수
- **`docs/work-plan.md`(이 파일) 삭제**

---

# 이번 작업에서 다루지 않는 것

- **보안 모델 전반** — 관리자 토큰 평문 저장, 토큰만 알면 자기 IP를 admin으로 등록 가능, 브루트포스
  방어 없음, `X-Real-IP` 헤더 무조건 신뢰. **로그인 기능 도입으로 통째로 대체될 영역**이라 제외
- **`isHoliday()` 공휴일 판정 구현** — `TODO(#38)`로 등록된 별도 기능 작업
- **관심종목(WatchStock) 구조 정리** — 여러 수집기와 startup 단계가 이것 때문에 비활성 상태다.
  사용자가 "구조 정리 전까지 비활성"으로 명시해둔 영역
- **로그인 기능** — 정비가 끝난 뒤 새 플로우의 첫 신규 기능으로 진행(`docs/decisions.md`)
