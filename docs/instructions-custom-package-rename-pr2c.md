# 지시서 — 커스텀 패키지·클래스 이름을 custom / sector 로 바꾼다 (구획 1, PR 2c)

이 파일 하나만 읽고 작업할 수 있게 썼다. PR 2b(#123)가 DB 테이블을 `custom_*` 로 바꿨다. 이 PR은
**Java 쪽 이름만** 따라 바꾼다. **동작은 한 줄도 바뀌지 않는다** — IDE의 rename 리팩터링으로 할 수 있는
일만 한다.

---

## 1. 무엇을 하나

### 1-1. 패키지

`dev.eolmae.marketmonitor.domain.marketmap` → `dev.eolmae.marketmonitor.domain.custom`
(하위 `controller`, `dto`, `entity`, `enums`, `repository`, `service` 구조는 그대로. 테스트 패키지도 같이 옮긴다)

### 1-2. 클래스 (파일 이름도 같이)

| Before | After |
|---|---|
| **entity** | |
| `MarketMapCategory` | `CustomSector` |
| `MarketMapStockCategory` | `CustomStockSector` |
| `MarketMapCategoryVersion` | `CustomSnapshot` |
| `MarketMapScaleThreshold` | `CustomScaleThreshold` |
| `MarketValueTierThreshold` | `CustomValueTierThreshold` |
| **repository** | |
| `MarketMapCategoryRepository` | `CustomSectorRepository` |
| `MarketMapStockCategoryRepository` | `CustomStockSectorRepository` |
| `MarketMapCategoryVersionRepository` | `CustomSnapshotRepository` |
| `MarketMapScaleThresholdRepository` | `CustomScaleThresholdRepository` |
| `MarketValueTierThresholdRepository` | `CustomValueTierThresholdRepository` |
| **service** | |
| `MarketMapCategoryService` | `CustomSectorService` |
| `MarketMapCategoryTreeService` | `CustomSectorTreeService` |
| `MarketMapCategoryVersionService` | `CustomSnapshotService` |
| `MarketMapStockCategoryService` | `CustomStockSectorService` |
| `MarketMapScaleService` | `CustomScaleService` |
| `MarketValueTierThresholdService` | `CustomValueTierThresholdService` |
| `CategoryTierAggregationService` | `SectorTierAggregationService` |
| **controller** | |
| `MarketMapCategoryController` | `CustomSectorController` |
| `MarketMapCategoryVersionController` | `CustomSnapshotController` |
| `MarketMapStockCategoryController` | `CustomStockSectorController` |
| `MarketMapScaleController` | `CustomScaleController` |
| **dto** | |
| `CategoryDeletePreview` | `SectorDeletePreview` |
| `CategoryIdRequest` | `SectorIdRequest` |
| `CategoryItem` | `SectorItem` |
| `CategoryNameRequest` | `SectorNameRequest` |
| `CategoryTreeNode` | `SectorTreeNode` |
| `CreateCategoryRequest` | `CreateSectorRequest` |
| `StockCategoryItem` | `StockSectorItem` |
| `StockCategoryListItem` | `StockSectorListItem` |
| `VersionItem` | `SnapshotItem` |
| `VersionLabelRequest` | `SnapshotLabelRequest` |
| `MarketMapScaleResponse` | `CustomScaleResponse` |
| `MarketValueTierItem` | `CustomValueTierItem` |
| **테스트** | |
| `MarketMapCategoryServiceTest` | `CustomSectorServiceTest` |
| `MarketMapCategoryTreeServiceTest` | `CustomSectorTreeServiceTest` |
| `CategoryTierAggregationServiceTest` | `SectorTierAggregationServiceTest` |

표에 없는 클래스(`AliasRequest`, `BulkAssign*`, `ReparentRequest`, `ScaleThreshold*`, `ColorLabel`)는 패키지만
옮기고 이름은 그대로다.

### 1-3. 따라 바뀌는 것

- 위 클래스를 **주입받는 필드·생성자 파라미터 이름**: 클래스 이름을 따라 바꾼다
  (예: `marketMapCategoryRepository` → `customSectorRepository`). 패키지 밖(`view`, `notification`, `stock`,
  `config` 등)의 주입 필드도 포함
- QueryDSL Q클래스(`QMarketMapCategory` → `QCustomSector`)는 빌드가 다시 만든다. 그걸 쓰는 코드의 참조만 고친다
- **주석·javadoc에 적힌 옛 클래스 이름**: 새 이름으로 바꾼다 (`ApplicationConfig` 89행 주석 포함)

---

## 2. ★ 바꾸지 않는 것 — 어기면 운영이 깨진다

| 바꾸지 않는 것 | 왜 |
|---|---|
| **dto record 의 컴포넌트 이름** (`categoryId`, `categoryName`, `versionId` 등) | JSON 키가 된다. 바꾸면 프론트가 깨진다. `SectorTreeNode` 의 컴포넌트는 `custom_snapshot.snapshot_json` 에 저장된 JSON 키이기도 하다 |
| **엔티티 필드 이름** (`categoryId`, `versionId`, `parentId` 등) | Spring Data 파생 쿼리 메서드(`findByCategoryIdIn`, `deleteByCategoryIdIn` 등)가 필드 이름에 묶여 있다 |
| **`@Table`·`@Column` 값** | 2b에서 DB와 맞췄다 |
| **API URL** (`/api/admin/market-map/...`) | 프론트 계약. 나중에 `/api/custom` 으로 옮길 때 프론트와 같이 한다 |
| **`ErrorCode` 상수** (`CATEGORY_NOT_FOUND`, `VERSION_NOT_FOUND` 등) | 응답에 코드 문자열로 나갈 수 있다 |
| **메서드 이름·지역 변수 이름** (`getCategoryMaps`, `tagVersion` 등) | 이번 범위가 아니다 (주입 필드만 예외, 1-3) |
| **로그 태그** (`[카테고리삭제]` 등) | 운영 로그 검색어다 |
| **지도 기능 쪽 이름** — `view` 패키지의 `MarketMapController`, `MarketMapQueryService`, `MarketMapItem`, `MarketMapResponse`, `MarketMapCategoryNode`, `Category*` dto 들, `notification` 패키지 | 이건 실제로 "지도"다. 이 PR의 대상이 아니다 |
| `docs/` | 설계 쪽이 반영한다 (이 지시서 파일 삭제만 예외) |
| Flyway SQL | DB는 이미 끝났다 |

**문자열로 클래스 이름을 참조하는 곳**(JPQL `@Query`, `@Qualifier("…")`, 빈 이름 문자열 등)은 2026-09-25
기준 코드에 없다. 작업 중 발견하면 멈추고 보고한다.

---

## 3. 완료·검증

- `./gradlew spotlessApply build` 통과 (테스트 포함)
- 아래 grep 결과를 PR 설명에 붙인다

```
# 1) 옛 클래스 이름이 코드에 남지 않았다 → 0건
grep -rnE "MarketMap(Category|StockCategory|CategoryVersion|ScaleThreshold|Scale)(Repository|Service|Controller|TreeService|VersionService|Response|ServiceTest|TreeServiceTest)?([^A-Za-z0-9_]|$)|MarketValueTierThreshold|CategoryTierAggregation" src/main/java src/test/java

# 2) 옛 패키지가 남지 않았다 → 0건
grep -rn "domain\.marketmap" src/main/java src/test/java

# 3) dto record 컴포넌트가 그대로다 → main 과 diff 없음
git diff main -- src/main/java | grep -E "^\+.*record " 
#    나온 줄마다 괄호 안 컴포넌트 이름이 main 의 같은 record 와 같은지 PR 설명에 확인 결과를 적는다
```

1)은 `MarketMapCategoryNode`(view dto, 바꾸지 않음)에 걸리지 않게 짰다. 걸리면 무엇이 걸렸는지 보고한다.

- **동작 변화가 없어야 한다.** 테스트 코드는 이름·import·패키지 외에 **검증 내용을 한 줄도 바꾸지 않는다**

배포 뒤 사용자 확인: 앱 기동, 관리자 섹터·종목 페이지 조회·수정, 스냅샷 저장·복원, 지도·섹터 화면.

---

## 4. 브랜치·커밋

- 브랜치: `claude/refactor/rename-custom-package`
- 첫 커밋에 이 지시서를 포함하고, 마지막 커밋에서 이 지시서 파일만 지운다
- rename은 `git mv` 로 해서 히스토리가 이어지게 한다. 커밋은 `refactor:` 로, 패키지 이동 / 클래스 이름 변경을
  나눠도 되고 하나로 해도 된다 (각 커밋이 빌드되기만 하면 된다)
- push 하고 PR 까지 올린다
