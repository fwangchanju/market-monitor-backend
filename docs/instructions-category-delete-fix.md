# 지시서 — 비활성 종목 때문에 카테고리가 안 지워지는 문제

이 파일 하나만 읽고 작업할 수 있게 썼다. 근거가 필요하면 인용한 코드를 직접 열어 확인하면 된다.

---

## 1. 무엇이 문제인가

**화면에 보이지도 않는 종목 때문에 카테고리 삭제가 막힌다.**

두 경로가 서로 다른 기준을 본다.

```java
// ① 카테고리 삭제 판정 — MarketMapCategoryService.deletePreview / delete
List<MarketMapStockCategory> stockCategories =
        marketMapStockCategoryRepository.findByCategoryIdIn(subCategoryIds);
if (!stockCategories.isEmpty()) → 차단
```

`market_map_stock_category` **행의 존재만** 본다. `stock_info`와 대조하지 않는다.

```java
// ② 종목 관리 페이지 — MarketMapStockCategoryService.getStockCategories
stockInfoCacheService.getCache().values().stream()
        .filter(StockInfo::isActiveAndOrdinary)      // ← 여기서 먼저 거른다
```

`stock_info`에서 출발해 **활성 + 주권(코스피/코스닥)**만 남긴다. 캐시는 `stockInfoRepository.findAll()`
이라 비활성 종목도 다 들고 있어서 저 필터가 실제로 일을 한다.

그래서 **①이 세는 종목을 ②는 안 보여준다.** 사용자는 "왜 못 지우지" 하고 종목 관리 페이지를 열지만
그 종목이 목록에 없다.

### 운영 DB 실측 (2026-09-18)

```
category_id | category_name | stock_code | stock_name      | active | market_code
         49 | 엔터          | 299900     | 위지윅스튜디오   | f      | 10
         52 | 더테크놀로지  | 043090     | 더테크놀로지     | f      | 10
         54 | 시스웍        | 269620     | 시스웍           | f      | 10
         55 | 신세계푸드    | 031440     | 신세계푸드       | f      | 0
         62 | 석유·화학     | 121850     | 코이즈           | f      | 10
         83 | SPAC          | 465320 / 472220 / 471050 (스팩 3건)
        107 | 생명보험      | 082640     | 동양생명         | f      | 0
        125 | 거래정지      | 096610     | 알에프세미       | f      | 10
```

10건 전부 `active = false`다. `market_code`(주권 아님) 쪽은 지금 걸리는 것이 없다. 다만 판정은
`isActiveAndOrdinary` 하나로 하므로 둘 다 자연히 덮인다.

`52`·`54`·`55`는 **카테고리 이름이 종목 이름과 같다** — 종목 하나짜리 카테고리다. 그 종목이 비활성이
되면서 **텅 빈 것처럼 보이는데 삭제도 안 되는** 상태가 됐다.

### 왜 쌓이나

배정을 지우는 경로가 사실상 없다. `MarketMapCategoryTreeService.restore`의 `deleteAllInBatch()`
하나뿐인데 그건 버전 복원 시 통째로 갈아엎는 것이고, 개별 배정을 빼는 기능은 없다. 그리고 그 메서드의
주석이 불변식을 이렇게 적어놨다.

> "활성 주권 종목은 항상 `market_map_stock_category`에 배정돼 있다"

**한 방향뿐이다.** 역방향(`배정 있음 → 활성 주권`)은 아무도 안 지킨다. 그래서 배정된 뒤에 상장폐지된
종목의 행이 영원히 남는다. 2주에 10건 페이스다.

---

## 2. 확정된 결정

이 절은 이미 결정된 사항이다. 더 나은 방법이 떠올라도 그대로 따른다.

### 결정 1 — 판정과 삭제의 기준을 분리한다

| | 기준 | 이유 |
|---|---|---|
| **막을까 말까 판정** | 활성 주권만 센다 | 화면과 일치시킨다 |
| **실제 삭제** | 그 카테고리의 배정을 **전부** 지운다 | FK 때문에 필수 |

```sql
CONSTRAINT fk_market_map_stock_category_category
    FOREIGN KEY (category_id) REFERENCES market_map_category (id)
```

**판정만 고치면 삭제가 통과했다가 DB에서 FK 위반으로 터진다.** 비활성 배정 행이 남아 있기 때문이다.
둘은 반드시 한 세트로 간다.

### 결정 2 — 비활성 종목의 배정을 자동으로 지우지 않는다

`StockInfoCollector.sync()`(평일 07:00)가 비활성이 된 종목의 배정을 같이 지우는 안을 검토했고
**택하지 않았다.** 이유가 둘이다.

**첫째, 추가는 수동인데 삭제만 자동인 것이 앞뒤가 안 맞는다**(사용자 결정). 곧 진행할 작업에서
`market_map_stock_category`는 "기본값에서 벗어나게 손댄 것"만 담는 override 테이블이 되고, 가입 시
전 종목 초기 적재는 사라진다. 신규 상장 종목을 자동으로 넣지 않기로 한 마당에 상장폐지 종목만 자동으로
빼면 지금 넣은 코드를 그 작업에서 도로 걷어내야 한다.

**둘째, `active`는 영구 딱지가 아니다.** "키움 ka10099 목록에 지금 있는가"이고, 다시 나타나면
되살아난다.

```java
// StockInfoCollector.sync()
if (fetched == null) {
    if (existing.isActive()) existing.markInactive();   // 목록에 없다 → false
} else {
    existing.update(...);                                // 목록에 있다
}

// StockInfo.update()
this.active = true;                                      // ← 되살아난다
```

실측 목록의 `125 거래정지` 카테고리가 그 경우다. **다만 배정이 영영 사라지지는 않는다** —
`MarketMapCategoryService.findMissingAssignments`(65행)가 다음 sync에서 "활성 주권인데 배정 행이 없는
종목"을 찾아 다시 채운다. 실제로 잃는 것은 **사용자가 손으로 옮겨둔 카테고리와 `alias`**다. 거래재개된
종목이 키움 업종명 기준 카테고리로 되돌아가 버린다. 첫째 이유보다 약하지만 공짜로 생기는 손실은 아니다.

### 결정 3 — 종목 관리 페이지는 이번에 손대지 않는다

`MarketMapStockCategoryService.toStockCategoryListItem`이 배정 null을 못 견디는 자리가 있다.

```java
MarketMapCategory category = categoryById.get(stockCategory.getCategoryId());   // stockCategory가 null이면 NPE
```

지금은 `MarketMapCategoryService.onStockInfoSynced`(`StockInfoSyncedEvent` 수신)가 신규 상장 종목을
자동 배정해 미러 불변식을 떠받치므로 null이 오지 않는다. **sparse override로 바꾸는 작업에서
`MarketMapQueryService.java:467`과 함께 고친다**(사용자 결정). 미러를 전제하는 자리는 그 두 곳뿐이다.

단, 이번 변경이 그 전제에 금을 낸다. **5-3을 반드시 읽어라.**

---

## 3. 범위

### 할 것

- `deletePreview`·`delete`의 차단 판정을 활성 주권 기준으로
- `deletePreview`가 차단 시 돌려주는 종목 목록도 같은 기준으로
- `delete`가 카테고리를 지우기 전에 그 카테고리(+하위)의 배정 행을 **전부** 지운다

### 안 할 것

- **`StockInfoCollector`를 건드리지 않는다** (결정 2)
- **`MarketMapStockCategoryService`를 건드리지 않는다** (결정 3)
- **고아 행을 일괄 정리하는 배치나 SQL을 만들지 않는다.** 위 실측 10건은 해당 카테고리를 지울 때
  같이 딸려 나가고, 계속 쓰는 카테고리에 붙은 것은 아무것도 막지 않는다
- **`market_code`를 별도로 다루지 않는다.** `isActiveAndOrdinary` 하나가 둘 다 판정한다
- **버전 복원이 지운 카테고리를 되살리는 것을 막지 않는다.** `MarketMapCategoryTreeService.buildTree`가
  스냅샷에 배정을 필터 없이(비활성 포함) 담고 `restore`가 그대로 다시 넣으므로, 삭제 전에 저장해둔
  버전을 복원하면 그 카테고리가 비활성 배정과 함께 돌아온다. 새 규칙에서는 **다시 지울 수 있으므로**
  잠기지 않는다. 알고 감수한다
- **삭제되는 배정 건수를 미리보기에 표시하지 않는다.** 비활성 배정만 있는 카테고리를 지우면 그 행들과
  `alias`가 조용히 같이 사라진다. 고지 UI는 이번 범위 밖이다 — 대신 삭제 시 건수를 로그로 남긴다

---

## 4. 무엇을 고치나

### 4-1. 차단 판정

`MarketMapCategoryService`에는 **`stockInfoCacheService`가 이미 주입돼 있다**(43행). 새 의존성이
필요 없다.

`deletePreview`(214행)와 `delete`(230행)가 같은 판정을 쓰므로, "이 카테고리들을 막는 활성 주권 배정"을
돌려주는 private 헬퍼 하나로 모아 양쪽이 부르게 한다. 두 곳에 같은 필터를 복사하지 마라.

```java
// 지금
List<MarketMapStockCategory> stockCategories =
        marketMapStockCategoryRepository.findByCategoryIdIn(subCategoryIds);

// 바꾼 뒤 — 그 행이 가리키는 종목이 stock_info에서 활성 주권인 것만
//          (캐시에 아예 없는 종목도 "막지 않음"으로 친다)
```

`toBlockingStockCategoryItems`(287행)는 이미 `stockInfoCacheService.getCache()`를 쓰고 있는데,
캐시에 없는 종목에서 NPE가 난다.

```java
stockInfoCache.get(stockCategory.getStockCode()).getStockName()   // ← 캐시에 없으면 NPE
```

필터링된 목록만 넘어오면 이 자리는 자연히 안전해진다. **그래도 실제 null 가드를 넣어라** — 주석만으로는
안 된다. 없는 종목은 종목코드를 그대로 이름 자리에 넣으면 된다(5-3).

같은 줄의 `categoryById.get(...)`도 가드 없는 `.get()`이다. 이쪽은 같은 스냅샷에서 온 맵이라 지금은
도달 불가능하므로 **건드리지 마라.** 둘을 같이 고치려 들면 범위가 번진다.

### 4-2. 삭제 순서

```java
public void delete(Long categoryId) {
    // ... subCategoryIds 수집, 판정(활성 주권만)

    // 1. 배정 행 — 활성 여부와 무관하게 전부. FK가 카테고리 삭제를 막기 때문이다
    // 2. 등락률 스냅샷 (이미 있는 deleteByCategoryIdIn)
    // 3. 카테고리 (하위부터 depth 역순, 이미 있는 로직)
}
```

1번을 위해 `MarketMapStockCategoryRepository`에 `deleteByCategoryIdIn`이 필요할 수 있다. 없으면
Spring Data 파생 메서드로 추가한다 — `market_map_category_change_rate_snapshot` 쪽에 같은 이름의
메서드가 이미 있으니 그 모양을 따른다.

**`market_map_category`를 가리키는 FK는 셋이고 전부 처리된다.** 빠뜨린 것이 없는지 확인용으로 적어둔다.
`V1__create_schema.sql` 기준이다.

```
52행   market_map_category.parent_id               → 하위부터 depth 역순 삭제 (기존)
64행   market_map_stock_category.category_id       → 이번에 추가하는 1번
277행  change_rate_snapshot.category_id            → 기존 deleteByCategoryIdIn
```

53행 `market_map_category.version_id`는 **카테고리에서 나가는** FK다. 카테고리 삭제와 무관하니 찾지
마라. `ON DELETE SET NULL`도 그쪽에 붙은 것이다.

---

## 5. 함정

### 5-1. 기존 테스트 중 깨지는 것은 **하나뿐이다** ★

`MarketMapCategoryServiceTest` 203행.

```
203행  delete_배정된_종목이_있으면_409로_차단된다
```

이 테스트는 `findByCategoryIdIn`만 스텁하고 **`stockInfoCacheService.getCache()`는 스텁하지 않는다.**
Mockito가 `Map` 반환형에 빈 맵을 돌려주므로 `cache.get("005930")`이 `null` → 5-3 규칙에 따라 "막지
않음" → `ConflictException`이 안 던져져 실패한다.

**고치는 법은 스텁 한 줄 추가다.** 기대값(`ConflictException`)은 그대로 둔다.

```java
when(stockInfoCacheService.getCache()).thenReturn(Map.of("005930",
        StockInfo.create("005930", "삼성전자", Market.KOSPI, "0", "반도체", 100L, BigDecimal.TEN)));
```

163행 테스트는 이미 같은 스텁을 갖고 있어 그대로 통과한다(169행). **손대지 마라.** 181·196·213행은
배정 목록이 비어 있거나 404로 먼저 빠져서 판정에 도달하지 않는다. 역시 그대로다.

**테스트를 돌려 하나만 실패하는 것이 정상이다.** 다섯 개가 다 깨질 것으로 예상하고 접근하면 멀쩡한
스텁을 건드리게 된다.

### 5-2. 새로 추가해야 하는 테스트

- **비활성 종목만 배정된 카테고리는 삭제가 막히지 않는다** — 이번 버그의 재현이다. 이것이 없으면
  고쳤는지 알 수 없다
- **주권이 아닌 종목(`market_code`가 `"0"`/`"10"`이 아님)도 막지 않는다** — 지금 운영에 사례가
  없지만 판정이 `isActiveAndOrdinary`라 같이 덮인다. 나중에 사례가 생겼을 때 걸리도록
- **삭제할 때 비활성 배정 행도 같이 지워진다** — FK 위반을 막는 그 동작이다. 단, 이 레포에는 DB
  테스트가 없으므로(`docs/rules/testing.md`) **이 테스트가 잡는 것은 FK 위반이 아니라 서비스의 호출
  순서다.** `verify(marketMapStockCategoryRepository).deleteByCategoryIdIn(...)` 또는 `InOrder`까지가
  얻을 수 있는 최대치다. 실제 SQL 순서는 Hibernate의 `ActionQueue`가 정한다. FK 위반 여부는 배포 후
  8절로만 확인된다 — 그 이상을 시도하지 마라
- **카테고리에 활성 주권 종목이 하나라도 있으면 여전히 막힌다** — 과잉 수정 방지

### 5-3. 캐시가 판정의 유일한 기준이다 — 그 대가를 알고 간다 ★

먼저 오해를 하나 걷어낸다. **"`market_map_stock_category`에 있는데 `stock_info`에서 사라진 종목"은
존재할 수 없다.** FK가 막는다.

```sql
-- V1__create_schema.sql:63
CONSTRAINT fk_market_map_stock_category_stock
    FOREIGN KEY (stock_code) REFERENCES stock_info (stock_code)
```

게다가 `StockInfoCollector.sync()`는 종목을 **지우지 않는다.** `markInactive()`만 한다.

그러니 캐시 miss가 나는 경로는 하나뿐이다 — **캐시가 낡은 경우.** `StockInfoCacheService.getCache()`는
TTL이 없고(`ApplicationConfig`의 `CaffeineCacheManager`에 `expireAfterWrite` 없음), 평일 07:00 sync의
`afterCommit` 훅 한 곳에서만 비워진다.

**그래도 캐시를 기준으로 삼는다.** 종목 관리 페이지가 바로 그 캐시로 목록을 만들기 때문이다
(`MarketMapStockCategoryService:90`). 리포지토리를 직접 읽으면 판정이 화면보다 엄격해져서, **사용자가
볼 수 없는 종목 때문에 다시 막히는** 지금 그 버그가 작은 규모로 되살아난다.

대가는 이것이다. DB에서는 활성인데 캐시가 비활성으로 알고 있으면(수동 DB 편집, 또는 sync 커밋과 evict
사이) 그 종목의 배정 행이 삭제되고, 캐시가 갱신된 뒤 **종목 관리 페이지 첫 로드에서 NPE가 난다**
(결정 3에서 인용한 자리다). 자동 복구는 다음 sync의 `findMissingAssignments`까지 기다려야 한다.

수동 DB 편집을 전제하지 않으면 이 창은 sync 커밋과 `afterCommit` evict 사이뿐이라 사실상 없다.
**알고 받는다.** 구획 2에서 null을 견디게 고치면 이 대가도 같이 사라진다.

그리고 캐시 miss 자체는 **"막지 않음"으로 친다.** 화면에 안 보이는 종목이므로 판정 기준과 일관된다.
`toBlockingStockCategoryItems`에서도 `NullPointer`로 터지지 않게 한다(4-1).

### 5-4. `docs/`는 수정하지 않는다

작업 중 알게 된 것은 PR 설명에 남긴다. 아래 완료 기준의 마지막 항목만 예외다.

---

## 6. 완료 기준

1. `./gradlew spotlessApply build` 통과 (`compileJava`에 `-Werror`가 걸려 있어 경고가 빌드를 깬다)
2. 5-1의 203행 테스트에 스텁을 추가해 통과한다. 163·181·196·213행은 **변경 없이** 통과한다
3. 5-2의 테스트 넷이 있다
4. 비활성 종목만 배정된 카테고리를 지울 때, 서비스가 배정 행 → 스냅샷 → 카테고리 순으로 호출한다
   (실제 SQL 순서는 테스트 범위 밖 — 5-2)
5. 활성 주권 종목이 있는 카테고리는 여전히 차단된다
6. 이 지시서 파일(`docs/instructions-category-delete-fix.md`)을 마지막 커밋에서 삭제한다

---

## 7. 커밋 분리

1. 차단 판정을 활성 주권 기준으로 (`deletePreview`·`delete`·차단 목록)
2. 삭제 시 배정 행 선삭제
3. 지시서 파일 삭제

1번만 들어가면 FK 위반이 나므로 **두 커밋을 같은 PR로 올린다.** 되돌릴 단위는 PR이다.

---

## 8. 배포 후 확인

이 PR이 배포되면 아래 카테고리들이 **수동 SQL 없이** 화면에서 삭제 가능해진다.

```
52 더테크놀로지 / 54 시스웍 / 55 신세계푸드     ← 이름이 종목명과 같다. 풀릴 가능성이 높다
49 엔터 / 62 석유·화학 / 83 SPAC / 107 생명보험 / 125 거래정지
```

**단정하지 마라.** 1절의 실측 쿼리는 `active = false` 행만 뽑은 것이라, 그 카테고리에 활성 배정이
**함께** 있는지는 저 결과로 알 수 없다. 8건 중 무엇이 풀릴지는 배포해봐야 안다.

**막히는 것은 버그가 아니다.** 활성 종목이 같이 있다는 뜻이고, 그 종목은 화면에 보이므로 옮기고 나서
지우면 된다. 차단 화면에 종목이 뜨는지로 구분된다 — 뜨면 정상, 빈 목록인데 막히면 그때가 버그다.
