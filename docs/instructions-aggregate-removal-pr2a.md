# 지시서 — 카테고리 집계 테이블 물리 삭제 (구획 1, PR 2a 백엔드)

이 파일 하나만 읽고 작업할 수 있게 썼다. PR 1(#121)이 집계 테이블을 읽고 쓰는 곳을 전부 끊었고,
프론트 #61이 `tierBreakdown`을 더 이상 읽지 않는다. 둘 다 운영에 배포됐다. **이 PR은 이제 아무도
쓰지 않는 것을 지운다.** 동작은 바뀌지 않는다.

---

## 0. ★ 배포 전 — 사용자가 직접 한다 (구현자는 하지 않는다)

사용자 데이터가 든 테이블을 **병합·배포 전에** 백업한다. 이 PR이 지우는 집계 테이블과 `_old`는 코드가
읽지 않는 데이터라 백업하지 않는다(되돌릴 때 필요한 건 빈 테이블 구조뿐이고 V1에 있다). 집계 백업은
2026-09-24에 이미 떠뒀으니 남겨둔다.

```sql
-- 다음 PR(2b)이 이름을 바꾸거나 지우는 것 — 사용자 데이터
CREATE TABLE bak_20260924_excluded_stock       AS TABLE market_map_excluded_stock;
CREATE TABLE bak_20260924_category             AS TABLE market_map_category;
CREATE TABLE bak_20260924_stock_category       AS TABLE market_map_stock_category;
CREATE TABLE bak_20260924_category_version     AS TABLE market_map_category_version;
CREATE TABLE bak_20260924_scale_threshold      AS TABLE market_map_scale_threshold;
CREATE TABLE bak_20260924_tier_threshold       AS TABLE market_value_tier_threshold;
```

그리고 두 가지를 확인한다.

```sql
-- 1) 원본과 백업의 행 수가 같은가 (6쌍 각각)
SELECT (SELECT count(*) FROM market_map_category) AS src, (SELECT count(*) FROM bak_20260924_category) AS bak;

-- 2) market_map_category_old 를 가리키는 FK 가 없는가 — 다른 테이블에서 오는 것은 0건이어야 한다.
--    자기 참조(fk_market_map_category_parent_old, conrelid = _old 자신)는 DROP 과 함께 사라지므로 괜찮다.
--    그 외가 있으면 배포하지 말고 알려줄 것 (DROP 이 실패한다. CASCADE 는 쓰지 않는다)
--    [2026-09-24 확인 완료: 자기 참조 1건뿐, 6쌍 행 수 일치, flyway V1 까지]
SELECT conname, conrelid::regclass FROM pg_constraint
WHERE confrelid = 'market_map_category_old'::regclass;
```

**백업 테이블(`bak_*`)과 사용자가 예전에 뜬 날짜 백업(`*_20260820`, `*_20260823`)은 이 PR도, 2b도
절대 건드리지 않는다.** 2b까지 배포가 안정된 뒤 사용자가 직접 지운다.

---

## 1. 무엇을 하나

```
지운다                                              남긴다 (지금도 쓰인다)
────────────────────────────────────                ────────────────────────────────────
market_map_category_change_rate_snapshot 테이블      CategoryTierAggregationService
  엔티티 / 리포지토리 / Custom / Impl                CategoryTierBreakdown (새 합산 경로가 쓴다)
MarketMapCategoryChangeRateSnapshotService           CategoryChangeRateItem / ...MarketRanking (텔레그램)
  와 그 테스트                                        SectorPriceSnapshot* 의 MarketSnapshotTime·
CategoryTierAggregationComparisonManualTest            SnapshotRetentionSummary (이름만 같은 별개 record)
MarketMapCategoryService.delete 의 집계 행 삭제 호출
MarketMapCategoryNode.tierBreakdown 필드
market_map_category_old 테이블 (코드는 안 쓴다)
시퀀스 이름의 '1' 꼬리 3개
```

---

## 2. 코드 변경

### 2-1. 파일째 지운다

- `domain/marketmap/entity/MarketMapCategoryChangeRateSnapshot.java`
- `domain/marketmap/repository/MarketMapCategoryChangeRateSnapshotRepository.java`
- `domain/marketmap/repository/MarketMapCategoryChangeRateSnapshotRepositoryCustom.java`
- `domain/marketmap/repository/MarketMapCategoryChangeRateSnapshotRepositoryImpl.java`
- `domain/marketmap/service/MarketMapCategoryChangeRateSnapshotService.java`
- 테스트: `MarketMapCategoryChangeRateSnapshotServiceTest.java`, `CategoryTierAggregationComparisonManualTest.java`

**주의:** `MarketSnapshotTime`, `SnapshotRetentionSummary` record는 `SectorPriceSnapshotRepositoryCustom`에도
**따로** 정의돼 있고 섹터 가격 스냅샷 정리가 그쪽을 쓴다. 지우는 건 집계 쪽 Custom 안의 것뿐이다.
`SectorPriceSnapshot*`은 한 줄도 건드리지 않는다.

### 2-2. `MarketMapCategoryService.delete`

`marketMapCategoryChangeRateSnapshotRepository.deleteByCategoryIdIn(subCategoryIds);` 한 줄과 그 필드·import를
지운다. 바로 위 `marketMapStockCategoryRepository.deleteByCategoryIdIn`(비활성 배정 행 삭제)은 **남긴다** —
이름이 같은 별개 메서드다.

`MarketMapCategoryServiceTest`: 집계 리포지토리 mock과 그 `verify`/`inOrder.verify` 줄을 지운다. 남은
검증(배정 행 삭제 → 카테고리 삭제 순서)은 유지한다.

### 2-3. `tierBreakdown` 필드 제거

- `MarketMapCategoryNode`: `tierBreakdown` 컴포넌트와 위쪽 주석을 지운다. `leaf(...)`의 `List.of()` 인자도.
- `MarketMapQueryService`:
  - `buildCategoryTree(markets, time, Map)` 오버로드와 `toCategoryNode`의 `tierBreakdownByCategoryId`
    파라미터를 지우고, 인자 두 개짜리 `buildCategoryTree` 하나로 합친다
  - `buildCustomMarketMap` 위 "tierBreakdown은 항상 빈 배열이다…" 주석을 지운다
  - `CategoryTierBreakdown` import는 **남는다** (랭킹·캡션 합산이 여전히 쓴다)
- `MarketMapQueryServiceTest`: `getCustomMarketMap_tierBreakdown은_항상_빈_배열이다` 테스트를 지운다.
- `CategoryTierAggregationServiceTest`: `new MarketMapCategoryNode(...)`로 노드를 직접 만든다. 생성자
  인자에서 `tierBreakdown` 자리 하나만 뺀다. 검증 내용은 바꾸지 않는다.

### 2-4. 주석 정리

`CategoryTierAggregationService`의 javadoc이 지워지는 서비스 이름
(`MarketMapCategoryChangeRateSnapshotService.findTierBreakdownsByCategoryId`)을 언급한다. 그 문장만
"카테고리 id → 구간별 원시 합계 맵을 돌려준다" 정도로 바꾼다. 로직은 건드리지 않는다.

다 지운 뒤 `grep -rn "ChangeRateSnapshot\|tierBreakdown\|category_change_rate" src/main/java src/test/java`
결과가 0건이어야 한다. Flyway SQL(V1의 CREATE, V2의 DROP)은 검색 범위에서 뺀다 — 테이블 이름이 남는 게
정상이다. (`CategoryTierBreakdown`은 대문자 T라 이 패턴에 걸리지 않는다.)

---

## 3. Flyway — `src/main/resources/flyway/V2__drop_category_aggregate.sql`

운영은 `baseline-version=1`이라 V2부터 실행된다. `ddl-auto=validate`라 엔티티 삭제와 DROP이 같은 PR이어야
한다.

```sql
-- 1) 집계 테이블. market_map_category 와 market_value_tier_threshold 를 FK 로 가리키는 쪽이라 먼저 지워도 된다
DROP TABLE IF EXISTS market_map_category_change_rate_snapshot;

-- 2) 옛 카테고리 테이블. 코드는 쓰지 않는다. 이 테이블이 market_map_category_id_seq 이름을 쥐고 있어서
--    지금 market_map_category 가 _seq1 을 쓰고 있다. CASCADE 는 쓰지 않는다 — 누가 가리키고 있으면 실패해야 한다
DROP TABLE IF EXISTS market_map_category_old;

-- 3) '1' 꼬리 시퀀스 이름 바로잡기. 이름만 바뀌고 identity 연결·현재값은 그대로다.
--    로컬·새 DB에는 _seq1 이 없으므로 IF EXISTS
ALTER SEQUENCE IF EXISTS market_map_category_id_seq1              RENAME TO market_map_category_id_seq;
ALTER SEQUENCE IF EXISTS program_trading_ranking_snapshot_id_seq1 RENAME TO program_trading_ranking_snapshot_id_seq;
ALTER SEQUENCE IF EXISTS sector_price_snapshot_id_seq1            RENAME TO sector_price_snapshot_id_seq;
```

순서가 중요하다. 2)가 3)보다 먼저여야 `market_map_category_id_seq` 이름이 비어 있다.

**`bak_*` 와 `*_20260820`, `*_20260823` 테이블은 이 스크립트에 절대 넣지 않는다.**

`V1__create_schema.sql`은 고치지 않는다 (이미 적용된 스크립트다 — 고치면 체크섬이 깨진다).

---

## 4. 하지 않는 것

- `market_map_excluded_stock` 삭제, `custom_*` 이름 변경 → PR 2b
- `docs/` 수정 (지시서 파일 삭제만 예외) — 문서 반영은 설계 쪽이 한다
- `SectorPriceSnapshot*`, 텔레그램 캡션 경로, `CategoryTierAggregationService` 로직

---

## 5. 완료·검증

- `./gradlew spotlessApply build` 통과 (테스트 포함)
- 2-4의 grep 0건
- CI는 DB 없이 돌아서 V2 스크립트는 빌드에서 검증되지 않는다. PR 설명에 스크립트 전문을 붙이고
  "운영 적용 전 0절 백업·FK 확인 필요"를 맨 위에 적는다

배포 뒤 사용자 확인:
- 앱이 뜨는가 (`validate` 통과 = 엔티티와 스키마가 맞다)
- `\ds` 에 `_seq1` 이 없는가
- 지도·섹터 화면, 커스텀 카테고리 삭제가 정상인가
- 다음 날 텔레그램 섹터 캡션이 정상인가

---

## 6. 브랜치·커밋

- 브랜치: `claude/refactor/drop-category-aggregate`
- 첫 커밋에 이 지시서를 포함하고, 마지막 커밋에서 이 지시서 파일만 지운다
- Flyway 스크립트는 `migrate:` 타입으로 따로 커밋한다
