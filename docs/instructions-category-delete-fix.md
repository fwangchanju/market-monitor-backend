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

**`active`는 영구 딱지가 아니다.** "키움 ka10099 목록에 지금 있는가"이고, 다시 나타나면 되살아난다.

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

실측 목록의 `125 거래정지` 카테고리가 그 위험을 보여준다. 거래재개되면 배정이 날아간 채로 돌아온다.

**그리고 곧 비대칭이 된다.** `docs/backlog.md`의 「가입 비용 — 미러 대신 sparse override」대로 가면
`market_map_stock_category`는 "기본값에서 벗어나게 손댄 것"만 담는 override 테이블이 된다. 신규 상장
종목을 자동으로 넣지 않기로 한 마당에 상장폐지 종목만 자동으로 빼는 것은 앞뒤가 안 맞는다. 지금 넣으면
그 작업에서 걷어내야 한다.

### 결정 3 — 종목 관리 페이지는 이번에 손대지 않는다

`MarketMapStockCategoryService.toStockCategoryListItem`이 배정 null을 못 견디는 자리가 있다.

```java
MarketMapCategory category = categoryById.get(stockCategory.getCategoryId());   // stockCategory가 null이면 NPE
```

지금은 `MarketMapCategoryService.onStockInfoSynced`(`StockInfoSyncedEvent` 수신)가 신규 상장 종목을
자동 배정해 미러 불변식을 떠받치므로 null이 오지 않는다. **sparse override로 바꾸는 작업에서
`MarketMapQueryService` 쪽과 함께 고친다**(사용자 결정). `docs/backlog.md`에 두 곳이라고 적어뒀다.

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

---

## 4. 무엇을 고치나

### 4-1. 차단 판정

`MarketMapCategoryService`에는 **`stockInfoCacheService`가 이미 주입돼 있다**(37행). 새 의존성이
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

필터링된 목록만 넘어오면 이 자리는 자연히 안전해진다. **그래도 그 전제에 기대지 말고**, 이 메서드가
받는 목록이 이미 걸러진 것임을 주석으로 남겨라.

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

**카테고리를 참조하는 FK는 넷이고 전부 처리된다.** 빠뜨린 것이 없는지 확인용으로 적어둔다.

```
parent_id                         → 하위부터 depth 역순 삭제 (기존)
version_id                        → ON DELETE SET NULL, 방향도 반대라 무관
market_map_stock_category         → 이번에 추가하는 1번
change_rate_snapshot.category_id  → 기존 deleteByCategoryIdIn
```

---

## 5. 함정

### 5-1. 기존 테스트 다섯 개가 이 동작을 고정하고 있다 ★

`MarketMapCategoryServiceTest`의 아래 테스트들이 **"배정 행이 있으면 무조건 막는다"를 전제로 짜여
있다.** 스텁이 `stock_info` 캐시를 안 채우고 있으면 고친 코드에서 "활성 주권 0건"이 되어 판정이
뒤집힌다.

```
163행  deletePreview_배정된_종목이_있으면_종목_목록과_함께_차단된다
181행  deletePreview_배정된_종목이_없으면_삭제_가능하고_하위카테고리_목록을_반환한다
196행  deletePreview_존재하지_않는_카테고리는_404를_반환한다
203행  delete_배정된_종목이_있으면_409로_차단된다
213행  delete_성공하면_대상과_하위카테고리가_삭제된다
```

**기대값을 지우지 말고 스텁을 채워라.** "배정된 종목이 있으면 막는다"는 여전히 참이어야 하고, 그
종목이 활성 주권일 때만 그렇다는 조건이 붙을 뿐이다.

### 5-2. 새로 추가해야 하는 테스트

- **비활성 종목만 배정된 카테고리는 삭제가 막히지 않는다** — 이번 버그의 재현이다. 이것이 없으면
  고쳤는지 알 수 없다
- **주권이 아닌 종목(`market_code`가 `"0"`/`"10"`이 아님)도 막지 않는다** — 지금 운영에 사례가
  없지만 판정이 `isActiveAndOrdinary`라 같이 덮인다. 나중에 사례가 생겼을 때 걸리도록
- **삭제할 때 비활성 배정 행도 같이 지워진다** — FK 위반을 막는 그 동작이다. 이것이 없으면
  "판정은 통과했는데 DB에서 터지는" 상태를 테스트가 못 잡는다
- **카테고리에 활성 주권 종목이 하나라도 있으면 여전히 막힌다** — 과잉 수정 방지

### 5-3. `stock_info` 캐시에 없는 종목

`stockInfoCache.get(stockCode)`가 `null`을 돌려줄 수 있다(`market_map_stock_category`에 있는데
`stock_info`에서 사라진 경우). 실측에서는 0건이었지만 방어가 필요하다.

**"막지 않음"으로 친다.** 화면에 안 보이는 종목이므로 판정 기준과 일관된다. 그리고 `NullPointer`로
터지지 않게 한다.

### 5-4. `docs/`는 수정하지 않는다

작업 중 알게 된 것은 PR 설명에 남긴다. 아래 완료 기준의 마지막 항목만 예외다.

---

## 6. 완료 기준

1. `./gradlew spotlessApply build` 통과 (`compileJava`에 `-Werror`가 걸려 있어 경고가 빌드를 깬다)
2. 5-1의 기존 테스트 다섯 개가 기대값을 유지한 채 통과한다
3. 5-2의 테스트 넷이 있다
4. 비활성 종목만 배정된 카테고리를 지울 때, 배정 행 → 스냅샷 → 카테고리 순으로 삭제된다
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
52 더테크놀로지 / 54 시스웍 / 55 신세계푸드     ← 비활성 종목 하나뿐이라 확실히 풀린다
49 엔터 / 62 석유·화학 / 83 SPAC / 107 생명보험 / 125 거래정지
                                                ← 활성 종목이 같이 있으면 여전히 막힌다(정상)
```

뒤쪽이 막히면 버그가 아니다. 그 종목은 화면에 보이므로 옮기고 나서 지우면 된다.
