# 지시서 — 카테고리 집계 테이블 제거 (구획 1, PR 1 백엔드)

이 파일 하나만 읽고 작업할 수 있게 썼다. 설계 배경은 `docs/backlog.md`의 「카테고리 집계 테이블을
없앤다」에 있다. **이 지시서가 그 절과 다르게 정한 것이 몇 있고, 3절에 이유와 함께 적었다. 둘이
어긋나면 이 지시서를 따른다.**

---

## 1. 무엇을 하나

`market_map_category_change_rate_snapshot`(카테고리별 등락률을 미리 더해둔 테이블)을 **읽고 쓰는
곳을 전부 끊는다.** 테이블과 엔티티는 남긴다 — 삭제는 PR 2다.

```
지금                                          바뀐 뒤
수집 tick마다 트리를 빌드해 합계를 저장   →    저장하지 않는다
텔레그램 캡션이 저장된 합계를 읽는다      →    조회 시점에 트리를 빌드해 더한다
/api/map 응답에 저장된 합계를 싣는다      →    빈 배열 (프론트가 종목에서 직접 더한다)
/api/sector 가 저장된 합계를 내려준다     →    엔드포인트 삭제 (섹터 페이지는 /api/map 을 쓴다)
```

그리고 두 가지를 더한다.

- `/api/map`에 `snapshotTime` 파라미터 — 섹터 페이지가 "15분 전"을 조회할 수 있게
- 종목 가격 캐시 — 같은 시각 가격 행을 반복해서 DB에서 읽지 않게

### 왜 없애나 (한 줄)

사용자별 분류가 들어오면 이 테이블이 사용자 수만큼 곱해진다. 그리고 실측으로 **지금도 원본보다
느리다.**

```
/api/sector?market=ALL_STOCK   7.11  7.26  8.29 초   ← 집계 테이블을 읽는다
/api/map?market=ALL_STOCK      2.89  2.29  2.99 초   ← 종목 행을 읽는다
(2026-09-22 장중, 렌더러 서버에서 측정)
```

집계 테이블은 쓸 수 있는 인덱스가 없다(UK 선두가 `market_type`). **이 숫자가 배포 전 기준선이다** —
배포 뒤 섹터 페이지는 `/api/map`을 두 번 받으므로 5.4초 안팎이 나와야 한다.

---

## 2. 핵심 원칙 — 텔레그램 숫자는 한 자리도 바뀌면 안 된다

이 PR에서 제일 위험한 곳은 **텔레그램 캡션의 등락률**이다. 계산 방식이 "저장된 합계 읽기"에서 "그
자리에서 더하기"로 바뀌는데, 결과 숫자가 달라지면 사용자는 알아챌 방법이 없다. 지도와 섹터 숫자가
갈려 한참 잡았던 백엔드 PR #113·프론트 PR #57이 정확히 이 종류였다.

그래서 이렇게 한다.

> **수집기가 저장하던 것과 정확히 같은 경로로 계산한다. 합산 코드는 새로 짜지 말고 옮긴다.**

지금 저장 경로가 이렇다.

```java
// CollectionScheduler.captureCategoryChangeRateSnapshots
for (Market market : Market.values()) {
    List<MarketMapCategoryNode> tree = marketMapQueryService.getCustomMarketMapTree(market, snapshotTime);
    marketMapCategoryChangeRateSnapshotService.captureSnapshot(market, snapshotTime, tree);
}

// MarketMapCategoryChangeRateSnapshotService.captureSnapshot
//   → collectSnapshots(tree, ...)   트리를 재귀로 돌며 카테고리 × 구간별로 묶는다
//   → computeRawSums(items)         Σ(등락률×시총), Σ시총, Σ등락률, 종목 수
```

**마켓별로, 커스텀 트리를, `getCustomMarketMapTree`로 빌드해서, `collectSnapshots`로 더한다.**
조회 시점에도 이 네 가지를 똑같이 하면, 입력(그 시각 가격 행·카테고리 배정·구간 경계)이 같은 한
숫자는 정의상 같다.

이걸 두 가지로 확인한다. **둘 다 해야 한다.**

1. **기존 테스트의 기대값을 한 글자도 바꾸지 않는다.** `MarketMapQueryServiceTest`의 텔레그램 랭킹
   테스트 13개가 기대값을 고정하고 있다. 입력(스텁)은 새 경로에 맞게 바꿔야 하지만, 기대값은
   그대로 통과해야 한다. 5-1을 반드시 읽어라
2. **실데이터로 옛 값과 새 값을 대조한다.** 테스트는 우리가 만든 입력만 본다. 운영 DB에 저장된
   합계와 새 경로가 같은 시각에 같은 숫자를 내는지 직접 비교한다. 6절

---

## 3. 확정된 결정

이미 결정된 사항이다. 더 나은 방법이 떠올라도 그대로 따른다.

### 결정 1 — 합산 로직을 새 클래스로 옮긴다

`MarketMapCategoryChangeRateSnapshotService`의 `collectSnapshots`·`collectItems`·`computeRawSums`·
`combine`을 **새 클래스 하나로 옮긴다.** 엔티티를 만들어 저장하던 부분만 빼고, 같은 결과를
`Map<Long, List<CategoryTierBreakdown>>`(카테고리 id → 구간별 합계)으로 돌려준다.

```
입력   트리 (List<MarketMapCategoryNode>)
출력   Map<Long, List<CategoryTierBreakdown>>
       — 지금 findTierBreakdownsByCategoryId 가 마켓 하나에 대해 돌려주는 것과 같은 모양
```

**출력 모양을 `findTierBreakdownsByCategoryId`와 똑같이 맞추는 것이 핵심이다.** 그래야 그걸 받아
쓰던 랭킹 코드(`avgOf`, `toTopCategoryItem` 등)가 한 줄도 안 바뀐다.

- `CategoryTierBreakdown`에는 `tierId`가 들어간다. 지금 `collectSnapshots`가 구간 라벨로
  `MarketValueTierThreshold`를 찾아 id를 얻는 것(`tierByLabel.get(...).getId()`)을 그대로 옮긴다
- **Map은 `groupingBy`의 기본 `HashMap`으로 만든다.** 지금 `findTierBreakdownsByCategoryId`가 그렇게
  만든다. `LinkedHashMap`·`TreeMap`으로 바꾸면 등락률이 같은 카테고리끼리 순서가 달라져 TOP2가
  바뀔 수 있다
- `combine`의 규칙(소수 4자리, `HALF_UP`, 빈 목록이면 0)은 그대로 옮긴다. 식을 고치지 않는다

클래스 위치와 이름은 `docs/architecture.md`를 보고 정한다. 옮긴 메서드는 옛 서비스에서 지운다 — **같은
합산 코드가 두 벌 남으면 안 된다.** 옮기는 커밋에서는 옛 `captureSnapshot`이 새 클래스를 부르게 해
동작을 유지하고, 수집기 호출이 사라지는 커밋(8절 5번)에서 `captureSnapshot`을 지운다.

**새 클래스는 트리를 받는다. `MarketMapQueryService`를 의존하지 않는다.** 옛 서비스의 클래스 주석이
그 이유를 적어뒀다 — "이 서비스가 `MarketMapQueryService`를 직접 의존하면 … 순환 참조가 된다".
트리 빌드는 `MarketMapQueryService`가 하고, 합산만 새 클래스가 한다.

### 결정 2 — 랭킹 로직은 그대로, 입력의 출처만 바꾼다

**백로그와 다르게 정한 것이다.** 백로그 PR 경계는 `getCategoryChangeRates`와 `CategoryTierBreakdown`을
"제거"로 적었는데, 이 지시서는 **API 표면에서만 뺀다.**

| | 처리 |
|---|---|
| `SectorController` (`GET /api/sector`) | **삭제** |
| `getCategoryChangeRates(MarketQuery, int)` — 2인자, 컨트롤러 전용 | **삭제** |
| `getCategoryChangeRates(MarketQuery, LocalDateTime, int)` — 3인자 | **유지.** 안쪽만 바꾼다 |
| `CategoryChangeRateItem`, `CategoryChangeRateMarketRanking` | **유지.** 텔레그램 내부 표현 |
| `CategoryTierBreakdown` | **유지.** 합산 단위 |
| `MarketMapCategoryNode.tierBreakdown` | 결정 3 |

이유: 텔레그램 랭킹 셋(`getTopCategoryRankings`, `getTopCategoryRankingsByChangeRate`,
`getMergedTopCategoryRanking`)은 TOP2 선정·대분류 필터·섹터 제외·기본 제외 구간·델타 계산·지수까지
붙어 있는 복잡한 로직이다. **이걸 다시 짜면 2절의 테스트 13개가 무엇을 보증하는지 알 수 없게
된다.** 입력의 출처만 바꾸면 테스트는 스텁 대상만 바뀌고 기대값은 그대로 간다.

3인자 `getCategoryChangeRates`의 안쪽은 이렇게 바꾼다.

```
지금    marketMapCategoryChangeRateSnapshotService.findRankingForMarkets(markets, t, beforeMinutes)
바뀐 뒤  마켓마다:
          now    = 결정 1 합산( getCustomMarketMapTree(market, t) )
          before = 결정 1 합산( getCustomMarketMapTree(market, t − beforeMinutes) )
          now 합산 결과(Map)가 비면 그 마켓은 결과에서 뺀다   (지금 동작과 같다)
          before 에 없는 카테고리는 withoutBefore              (지금 동작과 같다)
```

`findRankingForMarkets`의 주석이 이 두 규칙을 적어뒀다 — "그 시각에 데이터가 없는 마켓은 결과
목록에서 아예 빠진다", "before 시각에 정확히 일치하는 스냅샷이 없으면 해당 카테고리는 before 없이
내려준다". **그대로 지킨다.**

**"비었다"의 기준은 트리가 아니라 합산 결과다.** 지금 테이블에 행이 없던 조건이 "합산 결과가
비었다"와 같다. 트리는 비어 있지 않아도(카테고리 노드는 있는데 가격 행이 없어 종목이 0개) 합산
결과는 빌 수 있다. 트리로 판단하면 그 경우 빈 랭킹을 가진 마켓이 결과에 남는다.

`getMergedTopCategoryRanking`은 `findTierBreakdownsByCategoryId(markets, t)`를 부르던 자리를, 마켓마다
트리를 빌드해 합산한 결과로 바꾼다. 마켓 간 병합(같은 카테고리 id의 목록을 이어붙임)은 지금 코드
그대로다. **규칙 하나를 더한다 — 요청한 마켓 중 하나라도 합산 결과가 비면 빈 목록을 돌려준다.** 이유는
결정 6에 적었다.

**`ALL_STOCK` 트리를 한 번 빌드해서 마켓별로 나누지 않는다.** 백로그 결정 4는 그렇게 적었지만
택하지 않는다. `MarketMapItem`에 마켓 정보가 없어 나누려면 새 코드가 필요하고, 그건 저장하던
경로(마켓별 빌드)와 다른 경로라 숫자가 같다는 보장이 없다. 트리 빌드 비용은 결정 5의 캐시가 싸게
만든다.

### 결정 3 — `tierBreakdown`은 이번엔 빈 배열로 보낸다. 필드 삭제는 PR 2

**백로그와 다르게 정한 것이다.** `MarketMapCategoryNode.tierBreakdown` 필드를 지우지 않고, 커스텀
트리에서도 **항상 빈 배열**을 싣는다.

이유는 배포 순서다. 프론트 zod 스키마가 이 필드를 **필수**로 잡고 있다.

```ts
// market-monitor-frontend  src/types/api.ts:231
tierBreakdown: z.array(CategoryTierBreakdownSchema),
```

필드가 사라지면 옛 프론트의 `MarketMapResponseSchema.parse`가 던져서 **지도 페이지가 통째로
죽는다.** 텔레그램 지도 캡처도 같이 죽는다.

빈 배열이면 안 깨진다. **기본 마켓맵이 이미 빈 배열을 보내고 있다**
(`MarketMapCategoryNode.leaf()`가 `List.of()`), 옛 프론트는 그 경우 종목에서 직접 평균을 계산하는
폴백을 이미 탄다(`MarketMapCategorySection`의 `localWeightedAvgChangeRate`, `useGlobalSettings`의
`topPickAverage`). 이미 검증된 모양이다. 다만 숫자가 저장된 합계와 조금 다를 수 있다 — 5-8.

`buildCustomMarketMap`은 `findTierBreakdownsByCategoryId`를 부르지 않고 `buildCategoryTree(markets, t)`
(빈 맵을 넘기는 2인자)를 부르면 된다. 필드와 그 주석은 PR 2에서 지운다.

### 결정 4 — `/api/map`의 `snapshotTime`은 "요청한 마켓 전부에 있을 때만"

```
GET /api/map?market=KOSPI&isCustom=true                                   최신
GET /api/map?market=KOSPI&isCustom=true&snapshotTime=2026-09-22T10:05:00   그 시각
```

- 받는 형식: `@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  LocalDateTime snapshotTime`. 응답의 `snapshotTime`이 이미 이 형식(`2026-09-22T10:05:00`)으로
  나가므로, 프론트는 받은 값을 그대로 돌려보내면 된다
- 없으면 지금처럼 `findLatestCommonSnapshotTime`의 최신 시각
- 있으면 **그 시각에 정확히 일치하는** 스냅샷. 가까운 시각으로 대체하지 않는다
- **요청한 마켓 전부에 그 시각이 있을 때만** 트리를 내려준다. 하나라도 없으면 빈 응답
  (`MarketMapResponse.empty()`)
- 커스텀(`getCustomMarketMap`)과 기본(`getDefaultMarketMap`) **둘 다** 받는다. 섹터 페이지가 기본
  모드도 지원하기 때문이다

세 번째 규칙이 중요하다. `ALL_STOCK`으로 before를 조회했는데 그 시각에 코스피만 있고 코스닥이
없으면, 반쪽 트리(코스피 종목만)가 나간다. 프론트는 그걸 모르고 before 평균을 코스피만으로, now
평균을 두 마켓으로 계산해 **틀린 델타를 그린다.** 지금 프론트가 `beforeAvailable`로 막던 것
("병합 대상 마켓 중 하나라도 before가 없으면 … 델타 자체를 숨긴다")을 응답이 하나로 합쳐진 뒤에는
백엔드가 막아야 한다.

마켓별 존재 확인은 `SectorPriceSnapshotService.existsSnapshot(market, time)`이 이미 있다.

### 결정 5 — 종목 가격 캐시

지금 `buildCategoryTree`가 매번 `SectorPriceSnapshotService.findPriceByStockCode(markets, time)`로
가격 행을 DB에서 읽는다. 이걸 캐시로 감싼다.

```
위치   stock 도메인의 새 빈 하나. SectorPriceSnapshotService 안에 두지 않는다
메서드 @Cacheable, 키 (Market, LocalDateTime) — 마켓 하나 단위
값     가격 행의 불변 record 를 담은 Map.copyOf(...)  — 종목코드 → (현재가·등락률·시각)
       JPA 엔티티를 넣지 않는다
조건   unless = "#result == null || #result.isEmpty()"   — 5-5
매니저 전용 CacheManager 빈. expireAfterWrite 2시간, maximumSize 100
```

**새 빈으로 분리하는 이유.** Spring `@Cacheable`은 프록시로 동작해서 **같은 클래스 안에서 부르면
캐시를 안 탄다.** `SectorPriceSnapshotService.findLatestPriceByStockCode`가 이미 같은 클래스의
`findPriceByStockCode`를 부르고 있다. 거기 `@Cacheable`을 달면 그 경로는 조용히 캐시를 건너뛴다.

**`SectorPriceSnapshotService`의 기존 메서드는 바꾸지 않는다.** `findPriceByStockCode`,
`findLatestPriceByStockCode`와 그걸 쓰는 `MarketMapStockCategoryService`는 그대로 둔다.
`MarketMapQueryService.buildCategoryTree`만 새 빈을 마켓별로 불러 합친다. 값이 record로 바뀌므로
`toMarketMapItem`이 `SectorPriceSnapshot` 대신 그 record를 받는다. 쓰는 필드는
`getCurrentPrice`·`getChangeRate`·`getSnapshotTime` 셋뿐이다.

**전용 매니저를 둔다.** 기본 `CacheManager`(`ApplicationConfig.CACHE_MANAGER`)는 TTL이 없고
`STOCK_INFO`·`WATCH_STOCK`이 쓴다. 여기에 `setCaffeine`으로 TTL을 걸면 그 둘까지 2시간 뒤 만료된다.
**`ACCESS_CACHE_MANAGER`와 같은 모양으로 매니저 빈을 하나 더 만들고** `@Cacheable(cacheManager = …)`로
지정한다. 기본 매니저는 건드리지 않는다.

**`CacheService<T>`는 쓰지 않는다.** 백로그는 "구현 `CacheService<T>` + `@Cacheable`"이라 적었지만
`CacheService<T>`는 인자 없는 `getCache()`/`evict()`만 있는 계약이라 키가 있는 캐시에 안 맞는다.

**적재(워밍).** `IndexContributionRankingCollector.collect`가 마켓마다
`transactionTemplate.executeWithoutResult(...)`로 트랜잭션을 따로 연다. **그 호출이 돌아온 직후, 그
마켓에 대해 캐시 메서드를 한 번 부른다.** 커밋이 끝난 뒤라 DB에서 다시 읽어 캐시에 넣는 것이다.
API 응답을 직접 넣지 않는다 — `collectSectorPrice`는 행이 이미 있으면 저장을 건너뛰면서도 API 응답은
돌려주므로, 응답과 DB가 다를 수 있다.

- 마켓별로 `try/catch`로 감싸고 실패하면 로그만 남긴다(5-6)
- 백로그는 "`run("지수기여도랭킹")`이 성공을 반환한 다음"이라 적었는데, 그러면 한 마켓만 실패한
  tick에 성공한 마켓까지 적재를 못 한다. 마켓별 트랜잭션 직후에 넣는다

### 결정 6 — 발송 인자 `sectorAvailable`을 없앤다

`lastChangeRateSuccess`는 "카테고리 집계 캡처가 성공했나"였다. 캡처가 없어지면 이 상태 자체가 없다.

| 자리 | 처리 |
|---|---|
| `CollectionScheduler.lastChangeRateSuccess` | 필드·대입·전달 전부 삭제 |
| `CollectionScheduler.captureCategoryChangeRateSnapshots` | 삭제, `run("카테고리등락률스냅샷", …)` 삭제 |
| `TelegramReportDispatcher`의 네 메서드 | `sectorAvailable` 인자 삭제 |
| `SectorTelegramReportSender.send` | 인자와 `!sectorAvailable` 분기 삭제 |
| `MarketMapAlbumReportSender.send`, **`sendMapSinglePage`** | 인자 삭제, `buildCaption`의 `!sectorAvailable` 분기 삭제 |

**`sendMapSinglePage`는 백로그에 없다** — PR #117에서 생겼다. 빠뜨리지 마라.

지워도 되는 근거:

- 섹터 발송은 `lastIndexContributionSuccess`가 거짓이면 이미 상류에서 실패 알림으로 갈린다
  (`CollectionScheduler`의 `if (!lastIndexContributionSuccess)`). 그 뒤에 남는 경우는 전부 성공이다
- 지도 캡션은 "수집이 실패해서 그 시각 가격 행이 없음 → 합산이 빔 → 랭킹이 빔"으로 자연히 빈다.
  `buildCaption`에 **이미 있는** "랭킹이 비면 캡션 없이 보낸다" 분기가 그 경우를 받는다

**그런데 두 번째 근거에는 구멍이 하나 있다. 결정 2의 "하나라도 비면 빈 목록" 규칙이 그걸 막는다.**

```
tick 10:05  코스피 수집 성공, 코스닥 수집 실패
지금    lastChangeRateSuccess = false → 지도 캡션 없음
그냥 지우면  getMergedTopCategoryRanking(ALL_STOCK) 이 코스피 합계만으로 TOP2를 뽑는다
            → 지도 이미지는 두 마켓인데 캡션은 코스피 기준. 틀린 캡션이 조용히 나간다
규칙을 더하면  코스닥 합산이 비었으므로 빈 목록 → 기존 isEmpty 분기 → 캡션 없음 (지금과 같다)
```

그 과정에서 근거가 사라지는 주석을 같이 정리한다 — 5-10.

### 결정 7 — 안 쓰게 된 코드는 주석 처리하지 말고 지운다

`docs/rules/style.md` §9는 "미사용 코드는 바로 지우지 말고 주석 + TODO"다. **이 PR은 예외다.**
되돌릴 필요가 생기면 커밋 단위로 되돌린다(8절). 주석으로 남긴 코드는 PR 2에서 또 치워야 한다.

**안 쓰게 된 필드도 지운다.** `compileJava`가 Error Prone + `-Werror`라 주입만 받고 안 쓰는 필드는
`UnusedVariable`로 빌드가 깨진다. `@SuppressWarnings("UnusedVariable")`을 새로 달지 않는다.

| 커밋 | 지울 필드 |
|---|---|
| 5 | `CollectionScheduler.marketMapQueryService` (쓰는 곳이 캡처 한 줄뿐) |
| 5 | `CollectionScheduler.marketMapCategoryChangeRateSnapshotService` |
| 5 | `SnapshotRetentionScheduler.marketMapCategoryChangeRateSnapshotService` |
| 5 | 옛 서비스가 새 합산 클래스를 받던 필드 (`captureSnapshot`이 지워지면서 안 쓰게 된다) |
| 6 | `MarketMapQueryService.marketMapCategoryChangeRateSnapshotService` |

필드를 지우면 생성자도 바뀐다. 그 생성자를 직접 부르는 테스트를 같은 커밋에서 고친다.

---

## 4. 범위

### 할 것

- 결정 1~7
- `SnapshotRetentionScheduler`에서 `카테고리등락률스냅샷정리` 단계와 그 의존성 제거
- 6절 실데이터 비교 테스트

### 안 할 것

- **테이블·엔티티·리포지토리·QueryDSL 구현체를 지우지 않는다.** PR 2다
- **`MarketMapCategoryChangeRateSnapshotService`를 통째로 지우지 않는다.** 결정 1로 옮긴 메서드와
  `captureSnapshot`만 지우고, 조회·정리 메서드(`findTierBreakdownsByCategoryId`,
  `findRankingForMarkets`, 정리 메서드)는 호출부 없는 채로 남긴다. 6절 비교 테스트가 옛 값을 읽는
  데 쓴다. PR 2에서 지운다
- **`MarketMapCategoryNode.tierBreakdown` 필드를 지우지 않는다** (결정 3)
- **프론트를 건드리지 않는다.** 프론트 PR 1은 따로 있다
- **요약 페이지 개편은 이번 범위가 아니다** (사용자 결정)
- **테이블 이름을 `custom_*`으로 바꾸지 않는다.** PR 2다
- **랭킹 로직(TOP2·필터·델타)을 다시 짜지 않는다** (결정 2)
- **옛 프론트 라우트 제거·캡처 ID 이름 맞추기는 이번 범위가 아니다.** 백로그에 따로 있다

---

## 5. 함정

### 5-1. 테스트 기대값을 바꾸지 마라 ★★

대상은 `MarketMapQueryServiceTest`의 텔레그램 랭킹 테스트 13개다.

```
getTopCategoryRankings_*               6개  (자식_카테고리, TOP2까지만, before가_없는, before가_전부_없으면,
                                              기본_제외_구간, 평균_방식에_따라_델타)
getTopCategoryRankingsByChangeRate_*   3개  (등락률_기준으로, 평균_방식에_따라, 섹터_제외를_켜면)
getMergedTopCategoryRanking_*          4개  (두_마켓의_원시값을, 원시값을_합산한_뒤, 그_시각_스냅샷이_없으면,
                                              섹터_제외를_켜면)
```

지금은 합계를 직접 스텁한다.

```java
when(marketMapCategoryChangeRateSnapshotService.findRankingForMarkets(...)).thenReturn(...)
when(marketMapCategoryChangeRateSnapshotService.findTierBreakdownsByCategoryId(...)).thenReturn(...)
when(marketMapCategoryChangeRateSnapshotService.combine(Mockito.anyList()))
        .thenAnswer(invocation -> combine(invocation.getArgument(0)));
```

결정 1·2대로 하면 이 스텁들이 부를 대상이 없어진다. **기대값은 그대로 두고, 같은 합계가 새 경로로
흘러 들어오게 테스트 입력만 바꾼다.**

#### 합산 클래스는 mock 하지 말고 진짜를 쓴다

합산 클래스를 mock 하면 "트리 → 합계"를 테스트가 대신 정해주는 셈이라, 새 경로가 옛 경로와 같은
숫자를 내는지 아무것도 보증하지 않는다. 그리고 트리 두 개(now·before)가 모양이 같으면 mock은 둘을
구분할 수 없다. **진짜 합산 클래스를 쓰고, 입력을 종목 가격 행으로 만든다.**

그러려면 트리가 실제로 빌드돼야 한다. 테스트가 쓰는 (마켓, 시각)마다 아래를 채운다.

| 스텁 | 빠뜨리면 |
|---|---|
| `existsByMarketTypeAndSnapshotTime(market, t)` → `true` | **지금 한 번도 스텁되지 않는다.** Mockito 기본값 `false`라 `notExistsSnapshot`이 참이 되어 모든 트리가 비고, 모든 마켓이 빠지고, 테스트가 전부 빈 결과로 실패한다. 숫자가 달라진 게 아니라 스텁이 빠진 것이다 |
| before 없음 케이스는 before 시각만 `false` | "before 없음"이 자연히 재현된다 |
| 가격 행 조회 — **now와 before에 다른 행** | 같으면 델타가 전부 0이 된다 |
| `stockInfoCacheService.getCache()` | 종목 시가총액이 없어 구간을 못 정한다 |
| `marketMapStockCategoryRepository.findAll()` | 종목이 카테고리에 안 붙는다 |
| `marketMapCategoryRepository.findAll()` | 카테고리 노드가 없다 |
| `MarketValueTierThresholdService` — **진짜 객체**(리포지토리만 mock) | mock이면 `resolveTier`가 `null`을 돌려 NPE. 이 서비스는 의존성이 리포지토리 하나라 진짜를 쓸 수 있다 |

- 가격 행은 **시각으로 구분되게** 스텁한다. 호출 순서에 기대는 방식(`thenReturn(a).thenReturn(b)`)은
  코드가 before를 먼저 빌드하도록 바뀌면 조용히 뒤집힌다
- 지금 `CategoryTierBreakdown`으로 적힌 픽스처를 **같은 합계가 나오는 종목 가격 행**으로 옮긴다.
  구간 id는 구간 라벨 → `MarketValueTierThreshold` id에서 나오므로, 구간 픽스처의 id와 라벨이 기존
  `CategoryTierBreakdown`의 `tierId`와 맞아야 한다
- `getTopCategoryRankings_자식_카테고리는_랭킹에서_제외된다`는 기대값이 이름만 본다. 숫자 검증이
  아니다. 그대로 두되 이 테스트로 숫자를 보증했다고 적지 않는다
- 테스트 클래스 끝의 `combine` 재현 메서드(`thenAnswer`가 쓰던 것)는 지운다. 진짜 합산 클래스가
  그 계산을 한다

**기대값을 "새 코드가 내는 값"으로 고쳐서 통과시키면 이 PR의 회귀망이 사라진다.** 어떤 테스트가
기대값을 바꿔야만 통과한다면, 또는 기존 기대값을 만드는 입력을 도저히 만들 수 없다면, **멈추고
보고한다.** 어느 테스트가 왜 그런지 PR 설명에 적고, 기대값은 건드리지 않는다.

새 합산 클래스에는 **따로 테스트를 둔다.** 트리를 넣었을 때 카테고리·구간별 합계가 맞는지 —
하위 카테고리 재귀 포함, 여러 구간, 가중·산술 둘 다, 빈 카테고리. 지금 `collectSnapshots`에 직접
테스트가 없으므로 이게 합산 코드의 첫 테스트가 된다.

### 5-2. 없어지는 테스트

아래는 지우는 게 맞다. 지우는 이유를 PR 설명에 적는다.

| 테스트 | 커밋 | 이유 |
|---|---|---|
| `getCategoryChangeRates_스냅샷의_categoryId가_카테고리_테이블에_없으면_그_항목만_빠진다` | 2 | 카테고리 버전 복원 직후 저장된 행이 없어진 id를 가리키는 경우였다. 트리 기반에선 id가 항상 현재 카테고리 테이블에서 오므로 그 상황이 생기지 않는다 |
| `getCategoryChangeRates_랭킹_스냅샷이_없으면_빈_응답을_그대로_반환한다` | 6 | 2인자 전용이다. `verifyNoInteractions(marketOverviewSnapshotRepository)`를 검증하는데, 3인자는 시각을 받으므로 이 조건이 성립하지 않아 옮길 수 없다. 2인자와 함께 지운다 |
| `SectorTelegramReportSenderTest`의 `send(dataTime, false)` 테스트 | 5 | 분기 자체가 사라진다 |
| `MarketMapAlbumReportSenderTest`의 `send(dataTime, false)` 테스트 | 5 | 분기 자체가 사라진다. **대신** "한 마켓만 합산이 있으면 캡션 없이 보낸다"(결정 6의 구멍)를 보는 테스트로 바꾼다 |

### 5-3. `getCategoryChangeRates_*` 테스트 셋은 지우지 말고 3인자로 옮겨라 ★

```
getCategoryChangeRates_랭킹과_같은_시각의_지수_등락률이_마켓별로_붙는다
getCategoryChangeRates_그_시각에_지수_스냅샷이_없으면_index가_null이다
getCategoryChangeRates_before_시각에_지수_스냅샷이_없으면_index_before가_null이다
```

이 셋이 **전부 2인자 메서드를 부른다.** 2인자를 지우면 컴파일이 깨진다. 지우고 싶어지는 자리인데
지우면 안 된다 — 이 셋이 5-4의 **지수 등락률 붙이기**를 지킨다.

**2번 커밋에서 3인자(`getCategoryChangeRates(MarketQuery, LocalDateTime, int)`)를 부르도록 옮긴다.**
2인자는 최신 시각을 스스로 조회했지만 3인자는 시각을 인자로 받으므로, 테스트가 쓰던 시각을 직접
넘긴다. 입력은 5-1과 같은 방식으로 새 경로에 맞추고, 기대값은 그대로다.

### 5-4. 지수 등락률을 빠뜨리지 마라 ★

3인자 `getCategoryChangeRates`는 랭킹에 **지수 등락률(`MarketIndexChangeRate`)**을 붙인다
(`decorateRanking` → `toMarketIndexChangeRate`). 텔레그램 섹터 캡션의 `#코스피 +x.xx%`가 이 값이다
(`toCategoryRankingSummary`의 `marketRanking.index().now()`).

안쪽을 트리 기반으로 바꾸면서 이 부분을 같이 걷어내기 쉽다. **지수는 트리와 무관하게
`marketOverviewSnapshotRepository`에서 온다. 그대로 둔다.**

5-3의 셋은 `getCategoryChangeRates`의 결과만 본다. **`getTopCategoryRankings`가 돌려주는
`CategoryRankingSummary`에 지수 값이 실리는지 보는 테스트는 지금 없다.** 하나 더한다 — 캡션이 실제로
읽는 자리가 거기다.

### 5-5. 빈 결과를 캐시하지 마라 ★

`@Cacheable`은 기본적으로 빈 결과도 캐시한다. 어떤 시각을 수집 커밋 **전에** 누가 조회하면 빈 맵이
2시간 동안 박힌다. 그 시각은 그동안 지도·섹터·텔레그램 전부 빈다.

**빈 결과는 캐시하지 않는다**(결정 5의 `unless`). 과거 시각의 가격 행은 한 번 쓰이면 안 바뀌므로,
비어 있지 않은 결과만 캐시하면 무효화가 필요 없다.

**이건 단위 테스트로는 확인이 안 된다.** `@Cacheable`은 프록시가 있어야 동작하므로 `new`로 만든
객체에선 아무 일도 안 일어난다. `SchedulingConfigTest`처럼 `ApplicationContextRunner`로 작은 컨텍스트를
띄워서 본다 — `@EnableCaching`, 전용 매니저, 새 캐시 빈, 리포지토리 mock.

- 같은 키로 두 번 부르면 리포지토리가 한 번만 불린다
- 빈 결과면 두 번 부를 때 리포지토리가 두 번 불린다
- 기본 매니저의 `STOCK_INFO`에 TTL이 생기지 않았다 (기본 매니저를 안 건드렸는지)

### 5-6. 캐시 적재가 실패해도 수집은 성공이다

결정 5의 적재는 성능 최적화지 정확성 조건이 아니다. 읽을 때 없으면 DB에서 읽는다. **적재 중 예외가
수집을 실패로 만들면 안 된다** — `CollectionScheduler.run()`이 예외를 `false`로 바꾸므로
`lastIndexContributionSuccess`가 거짓이 되고, 텔레그램 대신 실패 알림이 나간다.

테스트로 확인한다 — 캐시 빈이 예외를 던져도 `collect`가 정상 종료하고 나머지 마켓도 처리된다.

### 5-7. 트리 빌드가 늘어난다

텔레그램 섹터 발송 한 번에 마켓 2개 × (now, before) = 트리 **4번**이다. 08:10 폴백 경로를 타면
**8번**이다. 지금은 저장된 합계를 읽으니 0번이었다.

한 번에 드는 것은 `stockInfoCacheService.getCache()`(캐시), `marketMapCategoryRepository.findAll()`,
`marketMapStockCategoryRepository.findAll()`, 구간 목록, 가격 행(결정 5 캐시)이다. 15분에 한 번이라
문제될 크기는 아니다. **다만 가격 행 캐시가 없으면 가격 행을 매번 DB에서 읽는다** — 결정 5가 이 PR에
들어가는 이유다.

### 5-8. 알려진 차이 — PR 설명에 적는다

아래는 버그가 아니라 설계상 달라지는 것이다. PR 설명에 그대로 옮긴다.

- **before가 현재 배정으로 다시 계산된다.** 지금은 15분 전에 저장된 합계(그때의 카테고리 배정)를
  읽는다. 바뀐 뒤엔 15분 전 가격 행을 **지금의** 배정으로 더한다. 그 사이 사용자가 종목 배정을 바꿨다면
  before 값이 달라진다. 배정이 바뀐 직후 한 tick만 해당한다
- **부분 실패 다음 tick.** 10:05에 코스닥 수집만 실패하면 10:20 tick의 before(10:05)에 코스닥이 없다.
  코스피는 델타가 있고 코스닥 카테고리는 before 없음으로 빠진다. 지금도 저장 행이 없어 같은 결과다
- **옛 프론트의 헤더 평균이 조금 다를 수 있다.** `tierBreakdown`이 비면 옛 프론트는 종목에서 평균을
  계산하는데, 섹터 필터로 제외된 하위 카테고리와 시가총액 0인 종목을 빼고 계산한다. 저장된 합계는
  둘 다 포함했다. 섹터 필터를 켠 사용자에게만 보이고, 프론트 PR 1이 나가면 사라진다

### 5-9. 같이 고칠 테스트

필드·인자를 지우면 컴파일이 깨지는 테스트가 있다. 그 커밋에서 같이 고친다.

| 테스트 | 커밋 | 고칠 것 |
|---|---|---|
| `MarketMapCategoryChangeRateSnapshotServiceTest` | 1, 5 | 생성자 인자 (새 합산 클래스 주입, 이후 제거) |
| `TelegramReportDispatcherTest` | 5 | `sectorAvailable = true`로 검증하던 테스트 넷의 인자 |
| `TelegramReportCycleManualTest` | 5 | 발송 메서드 호출 넷의 인자 |
| `SectorTelegramReportSenderTest`, `MarketMapAlbumReportSenderTest` | 5 | 5-2 |

### 5-10. 근거가 사라지는 주석

- `CollectionScheduler`의 "맵 이미지는 카테고리 등락률 스냅샷과 무관해서 …"
- `getMergedTopCategoryRanking`의 "캡션 전용이다. 이 결과로 어느 마켓을 캡처할지 정하면 안 된다 …"
- `MarketMapAlbumReportSender.send`의 Javadoc과 `buildCaption`의 `sectorAvailable` 관련 주석
- `SectorTelegramReportSender.send`의 첫 주석
- `decorateRanking` 위 주석 중 저장된 스냅샷을 전제로 한 문장
- `getCustomMarketMapTree`의 Javadoc 중 "수집기가 집계 스냅샷을 저장할 때 쓴다"는 취지의 문장
- 테스트 안에서 "저장된 스냅샷"을 전제로 한 주석

주석을 새 동작에 맞게 고치거나, 근거가 없어졌으면 지운다.

### 5-11. `docs/`는 수정하지 않는다

작업 중 알게 된 것은 PR 설명에 남긴다. 완료 기준의 마지막 항목만 예외다.

---

## 6. 실데이터 비교 ★★

2절의 두 번째 확인이다. 테스트가 아무리 촘촘해도 우리가 만든 입력만 본다. **운영 DB에 저장된
합계와 새 경로의 결과를 같은 시각에 대조한다.** 이 PR까지는 수집기가 계속 저장하고 있었으므로
비교할 옛 값이 DB에 있다.

```
대상     최근 tick 12개 안팎 × KOSPI, KOSDAQ
옛 값    MarketMapCategoryChangeRateSnapshotService.findTierBreakdownsByCategoryId(List.of(market), t)
새 값    새 합산 클래스( MarketMapQueryService.getCustomMarketMapTree(market, t) )
비교     카테고리 id 집합이 같다
         카테고리마다 구간별 (tierId, weightedSum, totalValue, simpleSum, itemCount) 가 같다
         BigDecimal 은 compareTo 로 비교한다 (equals 는 scale 까지 본다)
```

- `@Tag("manual")` 테스트로 만든다. 일반 `test`에서 빠지고 `./gradlew manualTest --tests "*.<클래스명>" -i`로
  돈다. `scheduling.enabled=false`로 띄워 수집기가 돌지 않게 한다
- **읽기만 한다.** 저장·삭제를 부르지 않는다. 운영 DB에서 돈다
- **불일치를 전부 출력한다.** 처음 하나에서 멈추지 않는다. 시각·마켓·카테고리·구간·옛 값·새 값
- 끝에 요약 한 줄 — 비교한 (시각, 마켓) 수, 일치 수, 불일치 수

**실행은 구현자가 하지 않는다.** 운영 DB가 있는 서버에서 사용자가 돌린다. PR 설명에 실행 명령을 적어
둔다. 결과는 사용자가 PR에 붙인다.

- 전부 일치해야 병합한다
- 불일치가 5-8의 "배정이 바뀐 직후" 한 가지로 설명되면 괜찮다. 그 시각과 바뀐 배정을 같이 적는다
- **설명되지 않는 불일치가 하나라도 있으면 병합하지 않는다**

이 테스트는 PR 2에서 옛 테이블과 함께 지운다.

---

## 7. 완료 기준

1. `./gradlew spotlessApply build` 통과 (`compileJava`에 `-Werror`)
2. 텔레그램 랭킹 테스트 13개가 **진짜 합산 클래스로, 기대값 변경 없이** 통과한다 (5-1)
3. `getCategoryChangeRates_*` 셋이 3인자로 옮겨져 **기대값 변경 없이** 통과한다 (5-3)
4. 새 합산 클래스에 트리 → 합계 테스트가 있다
5. `CategoryRankingSummary`에 지수 값이 실리는지 보는 테스트가 있다 (5-4)
6. `/api/map`이 `snapshotTime`을 받는다. 커스텀·기본 둘 다. 요청 마켓 중 하나라도 그 시각이 없으면 빈
   응답이다. ISO 형식 문자열이 바인딩되는지를 포함해 테스트가 있다
7. 커스텀 트리의 `tierBreakdown`이 항상 빈 배열이다
8. `GET /api/sector`와 2인자 `getCategoryChangeRates`가 없다
9. `CollectionScheduler`가 집계 테이블에 쓰지 않는다. `lastChangeRateSuccess`가 없다
10. 발송기·디스패처에 `sectorAvailable` 인자가 없다 (`sendMapSinglePage` 포함)
11. `getMergedTopCategoryRanking`이 요청 마켓 중 하나라도 합산이 비면 빈 목록이다 — 테스트가 있다
12. `SnapshotRetentionScheduler`가 집계 테이블을 정리하지 않는다
13. 가격 캐시가 별도 빈이고 전용 매니저를 쓴다. 컨텍스트 테스트가 5-5의 세 가지를 확인한다
14. 캐시 적재가 실패해도 수집이 성공한다 — 테스트가 있다 (5-6)
15. 안 쓰는 필드가 없고, `@SuppressWarnings("UnusedVariable")`을 새로 달지 않았다 (결정 7)
16. 6절 비교 테스트가 있고, PR 설명에 서버 실행 명령이 있다
17. PR 설명에 5-8의 알려진 차이와 5-2의 지운 테스트·이유가 있다
18. 이 지시서 파일(`docs/instructions-aggregate-removal-backend.md`)을 마지막 커밋에서 삭제한다

---

## 8. 커밋 분리

되돌릴 단위를 잘게 둔다. 아래 순서가 각 커밋마다 빌드가 깨지지 않는 순서다. **커밋마다 빌드와
테스트가 통과해야 한다.**

| # | 내용 | 같이 고칠 테스트 |
|---|---|---|
| 1 | 합산 로직을 새 클래스로 옮긴다 (결정 1). 옛 `captureSnapshot`이 새 클래스를 부른다. 동작 변화 없음 | 새 클래스 테스트 추가, `combine` 스텁을 진짜 클래스로, 옛 서비스 테스트 생성자 |
| 2 | 텔레그램 랭킹 셋의 입력을 트리 기반으로 (결정 2). 병합 랭킹의 "하나라도 비면 빈 목록" | 13개 입력 전환, `getCategoryChangeRates_*` 셋 3인자로, stale id 테스트 삭제, 지수 테스트, 병합 규칙 테스트 |
| 3 | `/api/map`에 `snapshotTime` (결정 4) | 서비스·컨트롤러 테스트 |
| 4 | 가격 캐시 (결정 5) — 새 빈, 전용 매니저, `toMarketMapItem`, 적재 | 컨텍스트 테스트, 적재 실패 테스트 |
| 5 | 수집기 저장 제거, 발송 인자 제거, 정리 배치 단계 제거, `captureSnapshot` 삭제, 필드 정리 (결정 6·7). 여기서 집계 테이블 쓰기가 멈춘다 | 5-9 전부, 발송기 테스트 교체 |
| 6 | `tierBreakdown` 빈 배열, `/api/sector` 삭제, 2인자 삭제, `MarketMapQueryService` 필드 정리 (결정 2·3·7) | 빈 응답 테스트 삭제, 커스텀 트리의 `tierBreakdown`이 빈 배열인지 보는 테스트 추가 |
| 7 | 6절 실데이터 비교 테스트 | — |
| 8 | 지시서 파일 삭제 | — |

**2번 커밋 직후가 제일 중요한 확인 지점이다.** 테스트 13개와 옮긴 셋이 기대값 그대로 통과하는지
여기서 본다. 안 되면 3번 이후로 넘어가지 않고 보고한다.

지금 `getCustomMarketMap_*` 테스트는 `tierBreakdown`을 검증하지 않는다(`findTierBreakdownsByCategoryId`가
스텁되지 않아 mock 기본값인 빈 맵이 들어간다). 그래서 6번에서 빈 배열 테스트를 새로 더한다. 이때
합계가 **있을 법한** 입력(가격 행이 있는 종목)을 넣고도 빈 배열인지 봐야 의미가 있다.

---

## 9. 배포

### 병합 전 — 6절을 서버에서 돌린다

PR이 올라오면 사용자가 운영 서버에서 6절 비교 테스트를 돌리고 결과를 PR에 붙인다. 병합 여부는 그
결과로 정한다.

### 프론트 PR 1과 같이 나간다 — 프론트 먼저

`/api/sector`가 없어지므로 **이 PR만 나가면 지금 프론트의 섹터 페이지가 404다.** 텔레그램 섹터 캡처도
같이 죽는다.

결정 3 덕분에 지도 페이지는 순서가 어긋나도 안 깨진다. 섹터만 깨진다.

프론트가 먼저 나가면 그 사이 몇 분간 **섹터 화면의 변화율이 표시되지 않을 수 있다** — 새 프론트가
before를 `snapshotTime`으로 조회하는데 옛 백엔드는 그 파라미터를 몰라 최신 시각을 돌려준다. 프론트
PR 1이 "응답의 `snapshotTime`이 요청한 시각과 다르면 before 없음"으로 처리해야 한다(프론트 지시서
몫). 이 처리가 없으면 now와 before가 같은 데이터가 되어 변화율이 전부 0으로 그려진다. 텔레그램은
백엔드가 계산하므로 영향 없다.

### 배포 뒤 확인

- **텔레그램 섹터 캡션** — 배포 직전 tick과 직후 tick의 캡션을 나란히 본다. 시장이 움직이니 값은
  다르지만, 형식·지수 표기·TOP2 개수가 같아야 한다
- **텔레그램 지도 캡션** — 캡션이 붙어 나오는지
- 섹터 페이지 응답 시간 — 1절의 기준선(7.6초)과 비교
- 렌더러 로그에 캡처 오류가 늘지 않는지(`docker logs -t market-monitor-renderer 2>&1 | grep '캡처 오류'`)
- 집계 테이블의 최신 `snapshot_time`이 배포 시각에서 멈췄는지

### 그리고 다음은 PR 2다

테이블 삭제와 이름 변경(`custom_*`)이 PR 2다. **PR 2는 배포 전 CTAS 백업이 선행 조건이다** —
`docs/backlog.md`의 「배포 전 필수 — CTAS 백업」. 사용자 커스텀 데이터(카테고리 트리, 종목 배정,
별칭, 버전, 색 구간, 시가총액 구간)가 들어 있는 테이블들이다. 이름을 바꾸는 마이그레이션이 한 번
잘못 돌면 되돌릴 수 없다.
