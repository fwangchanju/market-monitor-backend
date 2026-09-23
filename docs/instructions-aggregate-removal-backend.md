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

기존 테스트 13개(`MarketMapQueryServiceTest`의 `getTopCategoryRankings*`,
`getTopCategoryRankingsByChangeRate*`, `getMergedTopCategoryRanking*`)가 기대값을 고정하고 있다.
**이 테스트들의 기대값을 한 글자도 바꾸지 않고 통과시키는 것이 이 PR의 합격선이다.** 5-1을 반드시
읽어라.

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

`CategoryTierBreakdown`에는 `tierId`가 들어간다. 지금 `collectSnapshots`가 구간 라벨로
`MarketValueTierThreshold`를 찾아 id를 얻는 것(`tierByLabel.get(...).getId()`)을 그대로 옮긴다.

클래스 위치와 이름은 `docs/architecture.md`를 보고 정한다. 옮긴 메서드는 옛 서비스에서 지운다 — **같은
합산 코드가 두 벌 남으면 안 된다.** 옮기는 커밋에서는 옛 `captureSnapshot`이 새 클래스를 부르게 해
동작을 유지하고, 수집기 호출이 사라지는 커밋(7절 5번)에서 `captureSnapshot`을 지운다.

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
          now    트리 = getCustomMarketMapTree(market, t)                   → 결정 1 로 합산
          before 트리 = getCustomMarketMapTree(market, t − beforeMinutes)    → 결정 1 로 합산
          now 트리가 비면 그 마켓은 결과에서 뺀다  (지금 동작과 같다)
          before 가 없는 카테고리는 withoutBefore  (지금 동작과 같다)
```

`findRankingForMarkets`의 주석이 이 두 규칙을 적어뒀다 — "그 시각에 데이터가 없는 마켓은 결과
목록에서 아예 빠진다", "before 시각에 정확히 일치하는 스냅샷이 없으면 해당 카테고리는 before 없이
내려준다". **그대로 지킨다.**

`getMergedTopCategoryRanking`은 `findTierBreakdownsByCategoryId(markets, t)`를 부르던 자리를, 마켓마다
트리를 빌드해 합산한 결과로 바꾼다. 마켓 간 병합(같은 카테고리 id의 목록을 이어붙임)은 지금 코드
그대로다.

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
죽는다.** 백로그는 "백엔드만 나가면 섹터 페이지가 404"라고만 적었는데 실제로는 지도까지 깨진다. 그리고
텔레그램 지도 캡처도 같이 죽는다.

빈 배열이면 안 깨진다. **기본 마켓맵이 이미 빈 배열을 보내고 있다**
(`MarketMapCategoryNode.leaf()`가 `List.of()`), 옛 프론트는 그 경우 종목에서 직접 평균을 계산하는
폴백을 이미 탄다(`MarketMapCategorySection`의 `localWeightedAvgChangeRate`, `useGlobalSettings`의
`topPickAverage`). 이미 검증된 모양이다.

`buildCustomMarketMap`은 `findTierBreakdownsByCategoryId`를 부르지 않고 `buildCategoryTree(markets, t)`
(빈 맵을 넘기는 2인자)를 부르면 된다. 필드와 그 주석은 PR 2에서 지운다.

### 결정 4 — `/api/map`의 `snapshotTime`은 "요청한 마켓 전부에 있을 때만"

```
GET /api/map?market=KOSPI&isCustom=true                          최신
GET /api/map?market=KOSPI&isCustom=true&snapshotTime=2026-...     그 시각
```

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
키     (market, snapshotTime)       — 마켓 하나 단위. 목록 조회는 마켓별로 불러 합친다
값     가격 행의 불변 record        — 종목코드·현재가·등락률·시각. JPA 엔티티를 넣지 않는다
TTL    2시간, expireAfterWrite
크기   maximumSize 로 상한을 둔다
적재   각 마켓 수집 트랜잭션이 커밋된 직후 + 읽을 때 없으면 DB에서 읽어 넣는다
```

**백로그와 다른 점 하나.** 백로그는 "구현 `CacheService<T>` + `@Cacheable`"이라 적었지만
`CacheService<T>`는 인자 없는 `getCache()`만 있는 계약이라 **키가 있는 캐시에 안 맞는다.** 키를 받는
`@Cacheable` 메서드로 만든다.

지금 기본 `CacheManager`(`ApplicationConfig.cacheManager`)는 TTL이 없다. 이 캐시는 TTL과 크기 상한이
필요하므로 별도 설정이 필요하다 — `accessCacheManager`처럼 전용 매니저를 두든, 캐시별 스펙을 주든
기존 패턴 중 하나를 따른다.

값을 record로 바꾸면 `toMarketMapItem`이 `SectorPriceSnapshot` 대신 그 record를 받게 된다. 쓰는 필드는
`getCurrentPrice`·`getChangeRate`·`getSnapshotTime` 셋뿐이다.

**적재 자리.** `IndexContributionRankingCollector.collect`가 마켓마다
`transactionTemplate.executeWithoutResult(...)`로 트랜잭션을 따로 연다. 그 호출이 돌아온 직후가 그
마켓의 커밋 직후다. 백로그는 "`run("지수기여도랭킹")`이 성공을 반환한 다음"이라 적었는데 그러면 한
마켓만 실패한 tick에 성공한 마켓까지 적재를 못 한다. **마켓별 트랜잭션 직후에 넣는다.**

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
- 지도 캡션은 "수집이 실패해서 그 시각 가격 행이 없음 → 트리가 빔 → 랭킹이 빔"으로 자연히 빈다.
  `buildCaption`에 **이미 있는** "랭킹이 비면 캡션 없이 보낸다" 분기가 그 경우를 그대로 받는다

그 과정에서 근거가 사라지는 주석 둘을 같이 정리한다 — `CollectionScheduler`의 "맵 이미지는 카테고리
등락률 스냅샷과 무관해서 …", `getMergedTopCategoryRanking`의 "캡션 전용이다. 이 결과로 어느 마켓을
캡처할지 정하면 안 된다 …".

---

## 4. 범위

### 할 것

- 결정 1~6
- `SnapshotRetentionScheduler`에서 `카테고리등락률스냅샷정리` 단계와 그 의존성 제거

### 안 할 것

- **테이블·엔티티·리포지토리·QueryDSL 구현체를 지우지 않는다.** PR 2다
- **`MarketMapCategoryChangeRateSnapshotService`를 통째로 지우지 않는다.** 결정 1로 옮긴 메서드만
  지우고, 조회·정리 메서드는 호출부 없는 채로 남긴다. PR 2에서 지운다
- **`MarketMapCategoryNode.tierBreakdown` 필드를 지우지 않는다** (결정 3)
- **프론트를 건드리지 않는다.** 프론트 PR 1은 따로 있다
- **요약 페이지 개편은 이번 범위가 아니다**(사용자 결정)
- **테이블 이름을 `custom_*`으로 바꾸지 않는다.** PR 2다
- **랭킹 로직(TOP2·필터·델타)을 다시 짜지 않는다** (결정 2)

---

## 5. 함정

### 5-1. 테스트 기대값을 바꾸지 마라 ★★

`MarketMapQueryServiceTest`의 텔레그램 랭킹 테스트 13개가 지금 이렇게 되어 있다.

```java
when(marketMapCategoryChangeRateSnapshotService.findRankingForMarkets(...)).thenReturn(...)          // 5곳
when(marketMapCategoryChangeRateSnapshotService.findTierBreakdownsByCategoryId(...)).thenReturn(...) // 4곳
when(marketMapCategoryChangeRateSnapshotService.combine(Mockito.anyList()))
        .thenAnswer(invocation -> combine(invocation.getArgument(0)));                                 // 2곳
```

결정 1·2대로 하면 이 스텁들이 부를 대상이 없어진다. **넘겨주는 값(`CategoryTierBreakdown` 목록)과
기대값은 그대로 두고, 그 값이 새 경로로 흘러 들어가게 테스트 입력만 바꾼다.** 결정 1에서 출력 모양을
맞춰둔 이유가 이것이다.

**기대값을 "새 코드가 내는 값"으로 고쳐서 통과시키면 이 PR의 회귀망이 사라진다.** 테스트가 하나라도
기대값을 바꿔야만 통과한다면, 그건 숫자가 달라졌다는 뜻이다. 멈추고 PR 설명에 어느 테스트가 왜
그런지 적어라.

#### 입력을 바꿀 때 걸리는 것 둘

**하나, 트리가 소리 없이 빈다.** 이 테스트는 `SectorPriceSnapshotService`를 mock이 아니라 **진짜
객체**로 쓴다(리포지토리만 mock).

```java
private final SectorPriceSnapshotService sectorPriceSnapshotService =
        new SectorPriceSnapshotService(sectorPriceSnapshotRepository);
```

그리고 `existsByMarketTypeAndSnapshotTime`은 **지금 한 번도 스텁되지 않는다.** Mockito 기본값이
`false`라, 랭킹이 `getCustomMarketMapTree`를 부르는 순간 `notExistsSnapshot`이 참이 되어 **모든 트리가
비고, 모든 마켓이 결과에서 빠지고, 13개가 전부 빈 결과로 실패한다.** 숫자가 달라진 게 아니라 스텁이
빠진 것이다. 테스트가 쓰는 (마켓, 시각)마다 이 스텁을 채워라.

**둘, now와 before를 구분해야 한다.** 지금은 `findRankingForMarkets` 한 번이 now·before를 짝지어
돌려주므로 테스트가 "before 없음"을 직접 만든다(`withoutBefore`). 새 경로에서는 now 트리와 before
트리를 **따로** 빌드해 각각 합산한다. 테스트 대역이 두 호출에 같은 값을 돌려주면 before가 now와 같아져
델타가 전부 0이 되고, "before 없음" 케이스도 재현이 안 된다.

- before 없음은 `existsByMarketTypeAndSnapshotTime(market, before 시각)`을 `false`로 두면 자연히 재현된다
- now와 before에 **다른** 합계를 넘기는 방법은 구현자가 정한다. 다만 **호출 순서에 기대는 방식**
  (`thenReturn(a).thenReturn(b)`)은 피하라 — 코드가 before를 먼저 빌드하도록 바뀌면 조용히 뒤집힌다.
  시각으로 구분되는 모양이 안전하다

새 합산 클래스에는 **따로 테스트를 둔다.** 트리를 넣었을 때 카테고리·구간별 합계가 맞는지 —
하위 카테고리 재귀 포함, 여러 구간, 가중·산술 둘 다. 지금 `collectSnapshots`에 직접 테스트가 없으므로
이게 합산 코드의 첫 테스트가 된다.

### 5-2. 없어지는 테스트

아래는 지우는 게 맞다. 지우는 이유를 PR 설명에 적는다.

- `getCategoryChangeRates_스냅샷의_categoryId가_카테고리_테이블에_없으면_그_항목만_빠진다` — 카테고리 버전
  복원 직후 저장된 행이 없어진 id를 가리키는 경우였다. 트리 기반에선 id가 항상 현재 카테고리
  테이블에서 오므로 **그 상황이 생기지 않는다**
- `SectorTelegramReportSenderTest`의 `send(dataTime, false)` 테스트,
  `MarketMapAlbumReportSenderTest`의 `send(dataTime, false)` 테스트 — **분기 자체가 사라진다**

### 5-2-1. `getCategoryChangeRates_*` 테스트 넷은 지우지 말고 3인자로 옮겨라 ★

위에서 하나를 지우고 남는 넷이 **전부 2인자 메서드를 부른다.**

```java
service.getCategoryChangeRates(MarketQuery.KOSPI, 60);        // 2인자 — 이 PR에서 지워진다
```

2인자를 지우면 이 테스트들도 컴파일이 깨진다. **지우고 싶어지는 자리인데 지우면 안 된다.** 이 넷이
지키는 것이 바로 5-3의 **지수 등락률 붙이기**다.

```
getCategoryChangeRates_랭킹_스냅샷이_없으면_빈_응답을_그대로_반환한다
getCategoryChangeRates_랭킹과_같은_시각의_지수_등락률이_마켓별로_붙는다
getCategoryChangeRates_그_시각에_지수_스냅샷이_없으면_index가_null이다
getCategoryChangeRates_before_시각에_지수_스냅샷이_없으면_index_before가_null이다
```

**3인자(`getCategoryChangeRates(MarketQuery, LocalDateTime, int)`)를 부르도록 옮긴다.** 2인자는 최신
시각을 스스로 조회했지만 3인자는 시각을 인자로 받으므로, 테스트가 쓰던 시각을 직접 넘기면 된다.
기대값은 그대로다.

### 5-3. 지수 등락률을 빠뜨리지 마라 ★

3인자 `getCategoryChangeRates`는 랭킹에 **지수 등락률(`MarketIndexChangeRate`)**을 붙인다
(`decorateRanking` → `toMarketIndexChangeRate`). 텔레그램 섹터 캡션의 `#코스피 +x.xx%`가 이 값이다
(`toCategoryRankingSummary`의 `marketRanking.index().now()`).

안쪽을 트리 기반으로 바꾸면서 이 부분을 같이 걷어내기 쉽다. **지수는 트리와 무관하게
`marketOverviewSnapshotRepository`에서 온다. 그대로 둔다.** 관련 테스트(`…지수_등락률이_마켓별로_붙는다`
등)가 이걸 지킨다.

### 5-4. 빈 결과를 캐시하지 마라 ★

`@Cacheable`은 기본적으로 빈 결과도 캐시한다. 어떤 시각을 수집 커밋 **전에** 누가 조회하면 빈 맵이
2시간 동안 박힌다. 그 시각은 그동안 지도·섹터·텔레그램 전부 빈다.

**빈 결과는 캐시하지 않는다**(`unless`로 거른다). 과거 시각의 가격 행은 한 번 쓰이면 안 바뀌므로,
비어 있지 않은 결과만 캐시하면 무효화가 필요 없다.

### 5-5. 캐시 적재가 실패해도 수집은 성공이다

결정 5의 적재는 성능 최적화지 정확성 조건이 아니다. 읽을 때 없으면 DB에서 읽는다. **적재 중 예외가
수집을 실패로 만들면 안 된다** — 그러면 `lastIndexContributionSuccess`가 거짓이 되어 텔레그램 대신
실패 알림이 나간다.

### 5-6. 트리 빌드가 늘어난다

텔레그램 섹터 발송 한 번에 `ALL_STOCK` × (now, before) = 트리 **4번**이다. 지금은 저장된 합계를
읽으니 0번이었다.

한 번에 드는 것은 `stockInfoCacheService.getCache()`(캐시), `marketMapCategoryRepository.findAll()`,
`marketMapStockCategoryRepository.findAll()`, 구간 목록, 가격 행(결정 5 캐시)이다. 15분에 한 번이라
문제될 크기는 아니다. **다만 가격 행 캐시가 없으면 가격 행을 4번 DB에서 읽는다** — 결정 5가 이 PR에
들어가는 이유다.

### 5-7. `docs/`는 수정하지 않는다

작업 중 알게 된 것은 PR 설명에 남긴다. 아래 완료 기준의 마지막 항목만 예외다.

---

## 6. 완료 기준

1. `./gradlew spotlessApply build` 통과 (`compileJava`에 `-Werror`)
2. 텔레그램 랭킹 테스트 13개가 **기대값 변경 없이** 통과한다 (5-1)
3. `getCategoryChangeRates_*` 넷이 3인자로 옮겨져 **기대값 변경 없이** 통과한다 (5-2-1)
4. 새 합산 클래스에 트리 → 합계 테스트가 있다
5. `/api/map`이 `snapshotTime`을 받는다. 커스텀·기본 둘 다. 요청 마켓 중 하나라도 그 시각이 없으면 빈
   응답이다 — 이 규칙에 테스트가 있다
6. 커스텀 트리의 `tierBreakdown`이 항상 빈 배열이다
7. `GET /api/sector`와 2인자 `getCategoryChangeRates`가 없다
8. `CollectionScheduler`가 집계 테이블에 쓰지 않는다. `lastChangeRateSuccess`가 없다
9. 발송기·디스패처에 `sectorAvailable` 인자가 없다 (`sendMapSinglePage` 포함)
10. `SnapshotRetentionScheduler`가 집계 테이블을 정리하지 않는다
11. 가격 행 캐시가 빈 결과를 캐시하지 않는다 — 테스트가 있다
12. 텔레그램 섹터 캡션에 지수 등락률이 그대로 붙는다 (5-3)
13. 이 지시서 파일(`docs/instructions-aggregate-removal-backend.md`)을 마지막 커밋에서 삭제한다

---

## 7. 커밋 분리

되돌릴 단위를 잘게 둔다. 아래 순서가 각 커밋마다 빌드가 깨지지 않는 순서다.

1. 합산 로직을 새 클래스로 옮긴다 (결정 1) — 동작 변화 없음. 옛 서비스가 새 클래스를 부르게 한다
2. 텔레그램 랭킹 셋의 입력을 트리 기반으로 (결정 2) — 테스트 스텁 대상 이동 포함
3. `/api/map`에 `snapshotTime` (결정 4)
4. 가격 행 캐시 (결정 5)
5. 수집기 저장 제거 + 발송 인자 제거 + 정리 배치 단계 제거 (결정 6) — 여기서 집계 테이블 쓰기가 멈춘다
6. `tierBreakdown` 빈 배열 + `/api/sector` 삭제 (결정 2·3)
7. 지시서 파일 삭제

**2번 커밋 직후가 제일 중요한 확인 지점이다.** 테스트 13개가 기대값 그대로 통과하는지 여기서 본다.
안 되면 3번 이후로 넘어가지 않는다.

---

## 8. 배포

### 프론트 PR 1과 같이 나간다 — 프론트 먼저

`/api/sector`가 없어지므로 **이 PR만 나가면 지금 프론트의 섹터 페이지가 404다.** 텔레그램 섹터 캡처도
같이 죽는다.

결정 3 덕분에 지도 페이지는 순서가 어긋나도 안 깨진다. 섹터만 깨진다.

프론트가 먼저 나가면 그 사이 몇 분간 **섹터 화면의 변화율이 표시되지 않을 수 있다** — 새 프론트가
before를 `snapshotTime`으로 조회하는데 옛 백엔드는 그 파라미터를 모른다. 프론트 PR 1이 이 경우를
"before 없음"으로 처리하도록 되어 있어야 한다(프론트 지시서 몫). 텔레그램은 백엔드가 계산하므로 영향
없다.

### 배포 뒤 확인

- **텔레그램 섹터 캡션의 숫자가 배포 전과 같은 방식으로 나오는지** — 배포 직전 tick과 직후 tick의
  캡션을 나란히 본다. 시장이 움직이니 값은 다르지만, 형식·지수 표기·TOP2 개수가 같아야 한다
- 섹터 페이지 응답 시간 — 1절의 기준선(7.6초)과 비교
- 렌더러 로그에 캡처 오류가 늘지 않는지(`docker logs -t market-monitor-renderer 2>&1 | grep '캡처 오류'`)

### 그리고 다음은 PR 2다

테이블 삭제와 이름 변경(`custom_*`)이 PR 2다. **PR 2는 배포 전 CTAS 백업이 선행 조건이다** —
`docs/backlog.md`의 「배포 전 필수 — CTAS 백업」. 사용자 커스텀 데이터(카테고리 트리, 종목 배정,
별칭, 버전, 색 구간, 시가총액 구간)가 들어 있는 테이블들이다.
