# 지시서 — 텔레그램 캡션을 화면과 같은 기준으로 계산한다

이 파일 하나만 읽고 작업할 수 있게 썼다. 근거가 필요하면 인용한 코드를 직접 열어 확인하면 된다.

---

## 1. 무엇이 문제인가

**같은 텔레그램 메시지 안에서 이미지와 캡션이 서로 다른 기준으로 계산된다.**

```
섹터 이미지 = 렌더러가 /category-change-rate 를 캡처한 것
캡션 텍스트 = MarketMapQueryService 가 계산한 TOP2
```

어긋나는 지점은 **두 개**다.

### 어긋남 1 — 평균 방식

화면은 산술평균(동일가중)이 기본이다.

```ts
// useGlobalSettings.ts:83
const [avgChangeRateUseSimple, setAvgChangeRateUseSimple] =
    usePersistedState('marketMap.avgChangeRateUseSimple', true)   // ← 기본 산술
```

서버는 분기 없이 가중평균 고정이다.

```java
// MarketMapQueryService.java:362-363
SnapshotAverages averages = marketMapCategoryChangeRateSnapshotService.combine(included);
return averages.weightedAvgChangeRate();   // ← simpleAvgChangeRate 는 쓰지 않는다
```

### 어긋남 2 — 제외된 섹터

화면은 `market_map_category.is_excluded = true`인 카테고리를 랭킹에서 뺀다. **세션스토리지가 비어
있어도 그렇다** — 서버 응답으로 자동으로 채워지기 때문이다.

```ts
// useGlobalSettings.ts:126-132
setExcludedCategoryNames(seedExcludedCategoryNames(data.items, []))
// seedExcludedCategoryNames(29-40행): if (node.isExcluded) out.set(node.categoryId, ...)
```

`isCustom`(67행)과 `sectorFilterEnabled`(112행) 기본값이 둘 다 `true`라 `excludedCategoryIds`가
실제로 채워지고, `CategoryChangeRatePage.tsx:285`가 그 값을 그래프에서 뺀다.

```ts
for (const excludedId of excludedCategoryIds) ids.delete(excludedId)
```

서버 캡션에는 그 필터가 없다. `toCategoryRankingSummary`(298-312행)는 `depth == 0`과
`excludedTierIds`만 본다. `category.isExcluded()`는 **`MarketMapQueryService.java:453` 한 곳**,
트리 노드를 만들 때만 쓰인다.

### 어긋나지 않는 것 — 건드리지 마라

| 항목 | 렌더러가 찍는 화면 | 서버 캡션 | 판정 |
|---|---|---|---|
| 구간 필터 | 새 탭이라 세션스토리지가 비어 `isExcludedByDefault` 기본값 | `excludedTierIds()` = `isExcludedByDefault` | 일치 |
| 마켓 범위 (섹터) | `?market=KOSPI` 단일 마켓 | 마켓별로 따로 계산 | 일치 |

렌더러가 컨텍스트를 재사용해도(`containers/renderer/server.js`) 캡처마다 `newPage()`로 새 탭을
열고 닫는다. sessionStorage는 탭 단위라 매번 비어 있다. 이 앱은 localStorage·쿠키·IndexedDB를
쓰지 않으므로 컨텍스트 재사용으로 새는 상태가 없다.

사용자 본인 브라우저와 텔레그램이 다른 것은 **그 사람이 설정을 바꿨기 때문**이고 정상이다.

---

## 2. 확정된 결정

이 절은 이미 결정된 사항이다. 더 나은 방법이 떠올라도 그대로 따른다.

### 결정 1 — 둘 다 양방향으로 만들고, 값은 파라미터로 넘긴다

평균 방식도 섹터 제외도 **켜고 끌 수 있게** 만들고, 어느 쪽을 쓸지는 넘겨받는다. 한쪽으로 박지 않는다.

파라미터의 출처는 요청이 아니라 **서버 설정**이다. 텔레그램 발송은 스케줄러가 부르는 배치라 값을
실어 보낼 호출자가 없다. 이 구조는 이미 `beforeMinutes`가 증명했다.

```java
// SectorTelegramReportSender — 프로퍼티 하나가 캡처 URL과 캡션 계산 양쪽에 들어간다
int beforeMinutes = telegramProperties.beforeMinutes();

sectorPath(summary.market(), beforeMinutes)              // → "?market=KOSPI&beforeMinutes=15"
marketMapQueryService.getTopCategoryRankings(..., beforeMinutes)
```

같은 자리에 두 개를 더 얹는다. **새로운 구조를 만들지 마라.**

### 결정 2 — 기본값은 "산술평균" + "제외 적용"

화면 기본값과 같은 값으로 둔다. 배포하자마자 이미지와 캡션이 맞아야 하기 때문이다.

| | 서버 기본값 | 대응하는 화면 기본값 |
|---|---|---|
| 평균 방식 | 산술(동일가중) | `avgChangeRateUseSimple = true` |
| 섹터 제외 | 적용한다 | `isCustom && sectorFilterEnabled = true` |

### 결정 3 — 발송기는 두 값을 항상 URL에 싣는다

기본값이 화면과 같으니 URL에 안 실어도 지금은 맞는다. 그래도 **항상 명시적으로 싣는다.** 프론트
기본값이 나중에 바뀌어도 텔레그램은 서버 설정을 그대로 따라야 한다. `beforeMinutes`도 그렇게 한다.

### 결정 4 — 섹터 제외 필터는 TOP2 경로에만 건다

`getCategoryChangeRates`(화면용 조회)에는 **넣지 마라.** 거기서 거르면 사용자가 "섹터 제외" 마스터
스위치를 꺼도 제외된 카테고리를 다시 볼 수 없다. 서버는 다 내려주고 화면이 거르는 지금 구조를 유지한다.

필터는 `toCategoryRankingSummary` / `toCategoryRankingSummaryByChangeRate` /
`getMergedTopCategoryRanking` 세 곳, 즉 캡션용 TOP2를 뽑는 자리에만 건다.

### 결정 5 — 맵 앨범의 "이미지는 마켓별, 캡션은 병합"은 그대로 둔다

`MarketMapAlbumReportSender`는 이미지를 마켓별로 2장 찍는데(`mapPath(market)`, 86-88행) 캡션은
두 마켓을 합친 TOP2다(`getMergedTopCategoryRanking(ALL_STOCK, ...)`, 60행).

숫자가 다른 건 맞지만 캡션 헤더가 `[#코스피 / #코스닥 섹터 등락률]`라고 **합친 값임을 명시**하고
있다. 앨범 전체에 붙는 캡션으로 의도된 모양이라 이번 범위 밖이다. **알고 남기는 차이다.**

### 결정 6 — 이름

| 자리 | 값 |
|---|---|
| 프로퍼티 | `telegram.average-mode=SIMPLE`, `telegram.sector-filter=true` |
| 자바 타입 | `AverageMode { WEIGHTED, SIMPLE }` (새 enum), `boolean` |
| 쿼리 파라미터 | `avgMode=simple\|weighted`, `sectorFilter=true\|false` |

평균 방식만 enum이다. 값이 두 개의 "이름 붙은 방식"이고 쿼리 문자열로도 나가서 `MarketQuery`와
같은 결이다. 섹터 제외는 켜고 끄는 스위치라 boolean이 그대로 맞고, 화면 상태 이름
(`sectorFilterEnabled`)과도 일치한다.

**`AverageMode`는 `SnapshotAverages`를 import하지 마라.** `domain/view/enums`의 기존 enum
(`MarketQuery`)은 `common/enums`에만 의존한다. `dto`를 끌어오면 이 패키지 최초 사례가 된다. 값을
고르는 분기는 `MarketMapQueryService`의 private 헬퍼에 둔다.

---

## 3. 범위

### 할 것

- 백엔드: 평균 방식과 섹터 제외 적용 여부를 파라미터로 받는다
- 백엔드: 두 발송기(`SectorTelegramReportSender`, `MarketMapAlbumReportSender`)의 캡처 URL에 두 값을 싣는다
- 프론트: `/category-change-rate`와 `/market-map`이 두 쿼리를 읽어 화면 상태에 반영한다

### 안 할 것

- **`MarketMapTelegramReportSender`의 캡처 URL은 손대지 않는다.** 부모
  `TelegramReportSender.send()`가 캡처 URL을 `?market=` 값만 붙여 만들어서
  (`SectorTelegramReportSender` 클래스 주석 19-25행에 그 이유가 있다) 값을 더 실으려면 부모 구조를
  건드려야 한다. 프로덕션 호출부는 `CollectionScheduler.collectMarketDataHourly()` 하나뿐이고 그
  `@Scheduled`가 주석 처리돼 있다(135행). 캡션 계산에 쓰는 인자만 프로퍼티 값으로 넘기고 끝낸다.
  (`MarketMapTelegramReportSenderManualTest`가 `./gradlew manualTest`로 이 경로를 실제로 부르긴
  한다. 그 경우 이미지와 캡션이 여전히 어긋나는데, 수동 테스트라 받아들인다.)
- **구간 필터와 섹터 마켓 범위는 건드리지 않는다.** 1절 표에서 이미 일치한다고 확인했다.
- **화면의 기본 토글 동작을 바꾸지 않는다.** 쿼리가 들어왔을 때만 덮어쓴다.
- **집계 테이블 구조·수집 로직은 손대지 않는다.** `SnapshotAverages`가 이미 두 값을 다 들고 있어서
  계산을 새로 짤 일이 없다.

---

## 4. 1부 — 백엔드

### 4-1. `AverageMode` enum 추가

`domain/view/enums`에 만든다. `WEIGHTED`, `SIMPLE` 두 값과 쿼리 문자열용 소문자 값을 돌려주는
메서드 하나(`queryValue()`). **그 이상 넣지 마라** (결정 6 참고).

### 4-2. `MarketMapQueryService`

`weightedAvgOf`(355행)가 유일한 계산 진입점이다. 이름이 "가중"으로 박혀 있으니 같이 바꾼다.

```java
// 지금
private BigDecimal weightedAvgOf(List<CategoryTierBreakdown> breakdowns, Set<Long> excludedTierIds)

// 바꾼 뒤
private BigDecimal avgOf(List<CategoryTierBreakdown> breakdowns, Set<Long> excludedTierIds, AverageMode averageMode)
```

두 값을 받도록 시그니처를 늘려야 하는 public 메서드:

| 메서드 | 쓰이는 곳 |
|---|---|
| `getTopCategoryRankings` | 섹터 캡션(평상시), 비활성 맵 발송 |
| `getTopCategoryRankingsByChangeRate` | 섹터 캡션(매일 첫 발송 폴백) |
| `getMergedTopCategoryRanking` | 맵 앨범 캡션 |

그 아래 `toCategoryRankingSummary` / `toCategoryRankingSummaryByChangeRate` /
`toTopCategoryItem` / `toTopCategoryItemByChangeRate`까지 같이 타고 내려간다.

**`getCategoryChangeRates`는 건드리지 마라** (결정 4). 화면용 조회고, 구간별 원시값을 그대로
내려줘서 평균은 프론트가 낸다.

### 4-3. 섹터 제외 필터

`getMergedTopCategoryRanking`은 이미 `categoryById`를 로컬에 들고 있다(276-277행). 거기에
`isExcluded` 조건을 한 줄 더한다.

`toCategoryRankingSummary` 계열은 `CategoryChangeRateItem`만 받는데 그 DTO에는 `isExcluded`가
없다. **DTO에 필드를 추가하지 마라** — 프론트가 소비하는 응답 모양이 바뀐다. 대신
`getTopCategoryRankings`가 `categoryById`를 한 번 조회해서 아래로 넘긴다.

### 4-4. `TelegramProperties`에 필드 추가

```java
public record TelegramProperties(
        String botToken,
        String chatId,
        String developerChatId,
        int sendMinute,
        int sendIntervalMinutes,
        int beforeMinutes,
        AverageMode averageMode,          // ← 추가
        boolean sectorFilter,             // ← 추가
        List<LocalTime> mapSendTimes) {}
```

`application.properties`에 `telegram.average-mode=SIMPLE`, `telegram.sector-filter=true`를
추가한다. `telegram.before-minutes=15`가 있는 22행 자리다. `application-prod.properties`는
`telegram.*`를 덮어쓰지 않으므로 여기만 고치면 된다.

### 4-5. 캡처 URL에 싣기

```java
// SectorTelegramReportSender.sectorPath
return RenderTarget.CATEGORY_CHANGE_RATE.path()
        + "?market=" + market.name()
        + "&beforeMinutes=" + beforeMinutes
        + "&avgMode=" + averageMode.queryValue()
        + "&sectorFilter=" + sectorFilter;

// MarketMapAlbumReportSender.mapPath
return RenderTarget.MARKET_MAP.path() + "?market=" + market.name()
        + "&avgMode=" + averageMode.queryValue()
        + "&sectorFilter=" + sectorFilter;
```

두 발송기 모두 `telegramProperties`의 **같은 값**을 캡처 URL과 캡션 계산에 쓴다. 서로 다른 값을
쓰면 이번에 고치는 문제가 그대로 남는다.

---

## 5. 2부 — 프론트

### 5-1. `useGlobalSettings`에 setter 두 개를 연다

지금은 토글만 나간다(338행, 352행 근처).

```ts
avgChangeRateUseSimple,
onToggleAvgChangeRateUseSimple: () => setAvgChangeRateUseSimple(prev => !prev),
```

URL에서 받은 값으로 **직접 세팅**해야 하므로 setter가 필요하다. 이름은 기존 규칙을 따라
`onChangeAvgChangeRateUseSimple`, `onChangeSectorFilterEnabled` 정도.

**페이지가 쓰는 하단 `return` 객체(381-413행)에도 같이 넣어야 한다.** `settingsModalProps`
(323행부터의 별도 객체 리터럴) 쪽에만 넣으면 페이지에서 못 쓴다.

### 5-2. `CategoryChangeRatePage` — 쿼리 판정 추가

`market`·`beforeMinutes`를 처리하는 `useEffect`가 176-203행에 있다. 거기에 두 개를 더한다.

```ts
const avgModeParam = searchParams.get('avgMode')
const isValidAvgMode = avgModeParam === 'simple' || avgModeParam === 'weighted'
if (isValidAvgMode) onChangeAvgChangeRateUseSimple(avgModeParam === 'simple')

const sectorFilterParam = searchParams.get('sectorFilter')
const isValidSectorFilter = sectorFilterParam === 'true' || sectorFilterParam === 'false'
if (isValidSectorFilter) onChangeSectorFilterEnabled(sectorFilterParam === 'true')
```

기존 주석이 명시한 규칙을 그대로 지킨다.

- **파라미터를 서로 독립적으로 판정한다.** 하나가 잘못됐다고 다른 것까지 무시하지 않는다
- **유효했던 것만 주소에서 지운다**
- 유효하지 않으면 기존(저장된) 값을 그대로 쓴다

### 5-3. `MarketMapCustomPage` — effect를 먼저 재구성해야 한다 ★

`market` 하나만 보는 `useEffect`가 **193-206행**에 있다. **`CategoryChangeRatePage`와 구조가
다르다.**

```ts
// MarketMapCustomPage.tsx:193-197
useEffect(() => {
  const param = searchParams.get('market')
  if (param !== 'KOSPI' && param !== 'KOSDAQ' && param !== 'ALL_STOCK') return   // ← 조기 반환
  handleMarketChange(param)
  setSearchParams(...)
```

`market`이 없거나 잘못되면 그 자리에서 반환한다. 뒤에 `avgMode`·`sectorFilter` 처리를 이어 붙이면
**실행되지 않는다.** 앞에 놓으면 `setSearchParams`의 `delete('market')`과 엇갈린다.

이 effect를 `CategoryChangeRatePage`(176-203행)와 같은 모양 — 파라미터별 독립 판정, 유효했던
것만 삭제 — 으로 재구성한 뒤에 추가하라.

렌더러가 보내는 URL은 `market`이 항상 유효해서 **이 버그는 테스트로도 렌더러로도 안 드러난다.**
사용자가 `?avgMode=weighted`만 있는 링크를 열었을 때만 조용히 무시된다. 그래서 여기 적는다.

지도 페이지는 `beforeMinutes`를 안 쓰므로 그것까지 넣지는 마라.

---

## 6. 함정 — 반드시 먼저 읽어라

### 6-1. 테스트 픽스처를 새로 만들어야 한다 ★★

`MarketMapQueryServiceTest`가 `combine()`을 자체 재현하는데 **산술평균 자리에 항상 0을 넣는다.**

```java
// MarketMapQueryServiceTest.java:699-709
private SnapshotAverages combine(List<CategoryTierBreakdown> breakdowns) {
    // ... weightedSum / totalValue 만 계산
    return new SnapshotAverages(weightedAvg, BigDecimal.ZERO);   // ← 여기
}
```

여기까지는 "헬퍼를 고치면 된다"로 보이지만 **그것만으로는 안 된다.** `tier()` 헬퍼(725-733행)가
이렇게 채운다.

```java
private CategoryTierBreakdown tier(Long tierId, String label, long weightedSum, long totalValue) {
    return new CategoryTierBreakdown(tierId, label,
            BigDecimal.valueOf(weightedSum),    // weightedSum
            BigDecimal.valueOf(totalValue),     // totalValue
            BigDecimal.valueOf(weightedSum),    // simpleSum  ← weightedSum 을 그대로 재사용
            1);                                 // itemCount  ← 항상 1
}
```

`tier(10L, "대형", 100_000, 10_000)` 기준으로:

| | 계산 | 값 |
|---|---|---|
| 가중평균 | 100,000 / 10,000 | `10` → "+10%" |
| 산술평균 | 100,000 / 1 | `100,000` ← **퍼센트가 아니라 원시 합계** |

그래서 `combine()` 헬퍼만 고치면 산술평균 자리에 100,000 같은 숫자가 들어간다. 스케일이 깨진 값이라
기대값을 적을 수가 없다.

**`tier()`가 `simpleSum`과 `itemCount`를 인자로 받도록 같이 고치고, 픽스처를 새로 만들어라.**
서로 다른 `totalValue`와 `itemCount > 1`을 가진 구간이 있어야 두 평균이 실제로 다른 값이 된다.

### 6-2. 기존 테스트 5개 중 실제로 깨지는 건 2개뿐이다 ★

나머지 3개는 기본값을 산술로 바꿔도 **그대로 통과한다.** 기존 픽스처가 `totalValue`를 전부 같은 값
(10,000)으로 쓰고 `itemCount = 1`이라, 두 평균이 단조 동형이라 순위가 안 바뀌기 때문이다.

| 테스트 | 산술로 바꿨을 때 |
|---|---|
| `getTopCategoryRankings_기본_제외_구간은_평균_계산에서_빠진다` (562행) | **깨진다** |
| `getMergedTopCategoryRanking_원시값을_합산한_뒤_한_번만_나눈다` (638행) | **깨진다** |
| `getTopCategoryRankings_TOP2까지만_등락률_내림차순으로_노출된다` (506행) | 그대로 통과 |
| `getTopCategoryRankingsByChangeRate_등락률_기준으로_...` (583행) | 그대로 통과 |
| `getMergedTopCategoryRanking_두_마켓의_원시값을_합친_기준으로_TOP2를_뽑는다` (612행) | 그대로 통과 |

**통과하는 3개를 보고 "분기가 잘 동작한다"고 판단하지 마라.** 아무것도 증명하지 않는다. 6-1대로
픽스처를 새로 만들어야 완료 기준 4를 만족한다.

`getMergedTopCategoryRanking_원시값을_합산한_뒤_한_번만_나눈다`가 지키는 성질(이미 나뉜 평균끼리
다시 평균내면 틀린다)은 **산술평균에서도 똑같이 성립해야 한다.** 그 테스트를 약화시키지 마라.

### 6-3. 시그니처가 바뀌면 컴파일이 깨지는 곳

- `MarketMapQueryServiceTest` 9개 메서드 — 485, 506, 529, 550, 562, 583, 612, 638, 662행
- `new TelegramProperties(...)` **4곳, 3개 파일**
  - `SectorTelegramReportSenderTest.java:35`
  - `MarketMapAlbumReportSenderTest.java:33`
  - `EscalationNotifierTest.java:22`, `:37`

전부 컴파일러가 잡아준다. 다음 항목은 안 잡아준다.

### 6-4. `TelegramPropertiesBindingTest`에 단언을 추가하라 ★★

`src/test/.../notification/properties/TelegramPropertiesBindingTest.java`가 실제
`application.properties`를 읽어 각 컴포넌트를 검증한다. 그 클래스 주석이 존재 이유를 직접 적어놨다.

> `TelegramProperties`는 record라 프로퍼티 이름을 하나라도 틀리면 **조용히 0/null로 바인딩**되고 …
> 드러나는 시점이 배포 직후 컨테이너 기동 실패다.

`averageMode`를 추가하고 `telegram.average-mode`를 빠뜨리거나 오타 내면:

```
averageMode == null  →  이 테스트는 단언이 없어 통과
                     →  TelegramSendSchedule.validateConfiguration(33-42행)도 이 필드를 안 봐서 기동 성공
                     →  운영 첫 발송에서 averageMode.queryValue() NPE
```

**새 필드 두 개에 대한 단언을 이 테스트에 반드시 추가하라.** 이 프로젝트에 enum 타입 프로퍼티
선례가 없다(`AdminProperties`/`RendererProperties`/`KiwoomProperties` 전부 String·int·List).
Spring의 relaxed binding이 String→Enum을 처리하긴 하지만, 이름을 틀렸을 때의 안전망은 이
테스트뿐이다.

### 6-5. 캡처 경로를 문자열로 비교하는 테스트

`SectorTelegramReportSenderTest`에 `"/category-change-rate?market=KOSPI&beforeMinutes=15"` 류
하드코딩이 8곳, `MarketMapAlbumReportSenderTest:26-27`에 `KOSPI_MAP_PATH`/`KOSDAQ_MAP_PATH`
상수가 있다. 새 파라미터가 붙은 경로로 고쳐야 한다.

단 `Mockito.contains("KOSPI")`를 쓰는 곳(116, 227, 247-248행)은 파라미터가 붙어도 안 깨진다.
**고쳐야 할 곳과 안 고쳐도 통과하는 곳이 섞여 있으니**, 컴파일·실행이 통과한다고 다 고쳤다고
믿지 말고 경로 문자열을 직접 grep해서 확인하라.

### 6-6. 프론트 기본값과 프로퍼티 기본값이 갈리면 안 된다

결정 2의 표가 그 대응이다. 한쪽만 바꾸면 이번에 고치는 문제가 그대로 돌아온다. 바꿔야 할 일이
생기면 코드에서 정하지 말고 PR 설명에 적어라.

### 6-7. `docs/` 는 수정하지 않는다

작업 중 알게 된 것은 PR 설명에 남긴다. 아래 완료 기준의 마지막 항목만 예외다.

---

## 7. 완료 기준

1. `./gradlew spotlessApply build` 통과 (`build.gradle:111-113`이 `compileJava`에 `-Werror`를
   걸어놔서 경고 하나도 빌드를 깬다)
2. 프론트 `npm run lint`, `npm run build` 통과
3. `combine()` 테스트 헬퍼가 산술평균을 실제로 계산하고, `tier()` 헬퍼가 `simpleSum`·`itemCount`를
   인자로 받는다
4. **가중과 산술의 결과가 서로 다른** 픽스처로, 두 방식을 각각 검증하는 테스트가 있다
5. 섹터 제외를 켰을 때와 껐을 때 TOP2 결과가 달라지는 것을 검증하는 테스트가 있다
6. 두 발송기가 캡처 URL과 캡션 계산에 **같은** 값을 쓴다
7. 프로퍼티를 `WEIGHTED` / `sector-filter=false`로 바꾸면 **캡처 URL이 따라 바뀐다** (발송기 테스트)
8. 같은 값으로 **캡션 계산이 따라 바뀐다** (`MarketMapQueryServiceTest`)
9. `TelegramPropertiesBindingTest`가 새 필드 두 개를 단언한다

**이 지시서 파일은 삭제하지 마라.** 이 파일은 백엔드 레포에 있는데 5절(프론트)을 프론트 레포
작업이 읽어야 한다. 백엔드 PR이 먼저 병합되면서 파일이 사라지면 프론트 쪽이 근거를 잃는다. 삭제는
두 PR이 모두 끝난 뒤 문서 역할이 한다.

7번과 8번을 하나로 합치지 마라. 발송기 테스트는 `MarketMapQueryService`를 Mockito로 목킹해서
(`SectorTelegramReportSenderTest.java:38`, `MarketMapAlbumReportSenderTest.java:36`) 실제 계산이
일어나지 않는다. 거기서 확인 가능한 건 "인자가 전달됐다"까지다.

---

## 8. 커밋 분리

백엔드와 프론트는 **저장소가 다르므로 PR도 둘**이다. 각 저장소 안에서는 아래처럼 나눈다.

**백엔드**

1. `AverageMode` enum + `MarketMapQueryService` 시그니처·계산 분기·섹터 제외 필터.
   **이 커밋에서는 세 발송기가 `AverageMode.SIMPLE`과 `true`를 직접 넘긴다.**
2. `TelegramProperties` + `application.properties` + 두 발송기의 캡처 URL.
   1번에서 하드코딩한 값을 프로퍼티로 교체한다

1번에서 상수를 직접 넘기는 이유: 그 세 public 메서드의 **유일한 호출자가 발송기들**이라, 시그니처만
늘리면 `telegramProperties.averageMode()`가 아직 없어서 컴파일이 안 된다. 커밋을 합치지 말고 이
순서를 지켜라 — 1번만 들어가도 빌드가 서고, 동작도 기본값(산술·제외 적용)이라 화면과 맞는다.

**프론트**

1. `useGlobalSettings` setter 두 개 노출
2. `CategoryChangeRatePage` 쿼리 판정 추가
3. `MarketMapCustomPage` effect 재구성 + 쿼리 판정 추가

되돌릴 단위로 나눈 것이다.
