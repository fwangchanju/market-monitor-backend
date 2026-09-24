# 지시서 — 커스텀 테이블 이름을 custom_* 으로 바꾸고 죽은 제외 종목 테이블을 지운다 (구획 1, PR 2b)

이 파일 하나만 읽고 작업할 수 있게 썼다. 배경은 `docs/backlog.md`의 「커스텀 테이블 이름을
`custom_*`으로 바꾼다」에 있다. **이 지시서는 그 절의 일부만 한다(2절). 둘이 어긋나면 이 지시서를 따른다.**

---

## 0. ★ 배포 전 — 사용자 몫 (구현자는 하지 않는다)

- 사용자 데이터 백업은 2026-09-24에 끝났다(`bak_20260924_*` 6개, 행 수 일치 확인).
  **`bak_*` 와 `*_20260820`, `*_20260823` 테이블은 이 PR 어디에도 넣지 않는다.** 사용자가 나중에 직접 지운다.
- 운영 DB의 제약·인덱스·시퀀스 이름은 2026-09-24에 조회했고 3절 스크립트가 쓰는 이름과 전부 일치한다.

---

## 1. 무엇을 하나

```
DB                                              코드
──────────────────────────────────              ──────────────────────────────────
테이블 5개 이름 변경                              엔티티 5개의 @Table(name=…) 만 새 이름으로
그 테이블의 PK·UK·FK 이름 변경 (인덱스 따라감)      주석 속 옛 테이블 이름 → 새 이름
identity 시퀀스 4개 이름 변경, FK 컬럼 1개 이름 변경
market_map_excluded_stock DROP                   제외 종목 API·서비스·엔티티·리포지토리·DTO 삭제
```

| 지금 | 바뀐 뒤 |
|---|---|
| `market_map_category` | `custom_sector` |
| `market_map_stock_category` | `custom_stock_sector` |
| `market_map_category_version` | `custom_snapshot` |
| `market_map_scale_threshold` | `custom_scale_threshold` |
| `market_value_tier_threshold` | `custom_value_tier_threshold` |
| `market_map_excluded_stock` | **삭제** |

---

## 2. 하지 않는 것 (PR 2c로 미룸)

- **Java 클래스·패키지·필드·메서드 이름 변경 금지.** `MarketMapCategory` 는 `MarketMapCategory` 그대로다.
  `category` → `sector`, `marketMap` → `custom` 이름 정리는 DB를 안 건드리는 별도 PR(2c)에서 한다
- **컬럼 이름 변경 금지** (`category_id`, `parent_id` 등 그대로). **예외 하나:** `custom_sector.version_id` →
  `snapshot_id` (3절 4번, 4-1절). Java 필드 `versionId`·메서드 `tagVersion` 이름은 그대로 둔다 (2c)
- API 경로 변경 금지 (`excluded-stocks` 삭제만 예외)
- `V1`, `V2` 수정 금지 (이미 적용된 스크립트다 — 고치면 체크섬이 깨진다)
- `docs/` 수정 금지 (이 지시서 파일 삭제만 예외)

---

## 3. Flyway — `src/main/resources/flyway/V3__rename_custom_tables.sql`

아래를 **그대로** 쓴다. `IF EXISTS` 를 붙이지 않는다 — 이름이 하나라도 다르면 마이그레이션이 실패하고
Flyway가 한 트랜잭션으로 돌리므로 전체가 롤백되는 편이 안전하다. 전부 이름만 바뀌는 메타데이터 작업이라
데이터는 옮겨지지 않는다.

```sql
-- 1) 죽은 테이블. 등록·해제 API만 있고 지도 트리 경로도 프론트 화면도 이 테이블을 읽지 않는다.
--    stock_info 를 가리키는 쪽이라 먼저 지워도 된다
DROP TABLE market_map_excluded_stock;

-- 2) 테이블 이름
ALTER TABLE market_map_category         RENAME TO custom_sector;
ALTER TABLE market_map_stock_category   RENAME TO custom_stock_sector;
ALTER TABLE market_map_category_version RENAME TO custom_snapshot;
ALTER TABLE market_map_scale_threshold  RENAME TO custom_scale_threshold;
ALTER TABLE market_value_tier_threshold RENAME TO custom_value_tier_threshold;

-- 3) 제약 이름. PK·UK 는 같은 이름의 인덱스도 함께 바뀐다
ALTER TABLE custom_sector RENAME CONSTRAINT pk_market_map_category         TO pk_custom_sector;
ALTER TABLE custom_sector RENAME CONSTRAINT uk_market_map_category_name    TO uk_custom_sector_name;
ALTER TABLE custom_sector RENAME CONSTRAINT fk_market_map_category_parent  TO fk_custom_sector_parent;
ALTER TABLE custom_sector RENAME CONSTRAINT fk_market_map_category_version TO fk_custom_sector_snapshot;

ALTER TABLE custom_stock_sector RENAME CONSTRAINT pk_market_map_stock_category          TO pk_custom_stock_sector;
ALTER TABLE custom_stock_sector RENAME CONSTRAINT fk_market_map_stock_category_stock    TO fk_custom_stock_sector_stock;
ALTER TABLE custom_stock_sector RENAME CONSTRAINT fk_market_map_stock_category_category TO fk_custom_stock_sector_sector;

ALTER TABLE custom_snapshot RENAME CONSTRAINT pk_market_map_category_version TO pk_custom_snapshot;

ALTER TABLE custom_scale_threshold RENAME CONSTRAINT pk_market_map_scale_threshold         TO pk_custom_scale_threshold;
ALTER TABLE custom_scale_threshold RENAME CONSTRAINT uk_market_map_scale_threshold_percent TO uk_custom_scale_threshold_percent;

ALTER TABLE custom_value_tier_threshold RENAME CONSTRAINT pk_market_value_tier_threshold       TO pk_custom_value_tier_threshold;
ALTER TABLE custom_value_tier_threshold RENAME CONSTRAINT uk_market_value_tier_threshold_label TO uk_custom_value_tier_threshold_label;

-- 4) 컬럼 이름. 저장본 테이블을 가리키는 FK 컬럼 (저장본이 버전 이력이 아니라 이름 붙인 스냅샷이라서)
ALTER TABLE custom_sector RENAME COLUMN version_id TO snapshot_id;

-- 5) identity 시퀀스 이름. 연결·현재값은 그대로다 (custom_stock_sector 는 PK 가 stock_code 라 시퀀스가 없다)
ALTER SEQUENCE market_map_category_id_seq         RENAME TO custom_sector_id_seq;
ALTER SEQUENCE market_map_category_version_id_seq RENAME TO custom_snapshot_id_seq;
ALTER SEQUENCE market_map_scale_threshold_id_seq  RENAME TO custom_scale_threshold_id_seq;
ALTER SEQUENCE market_value_tier_threshold_id_seq RENAME TO custom_value_tier_threshold_id_seq;
```

`ddl-auto=validate` 라서 **엔티티 `@Table` 변경과 이 스크립트는 같은 PR·같은 배포여야 한다.** Flyway가 앱
기동 때 먼저 돌므로 같은 PR에 있으면 자연히 맞는다.

---

## 4. 코드 변경

### 4-1. `@Table` 이름 5개

| 파일 (`domain/marketmap/entity/`) | `@Table(name = …)` |
|---|---|
| `MarketMapCategory.java` | `"custom_sector"` |
| `MarketMapStockCategory.java` | `"custom_stock_sector"` |
| `MarketMapCategoryVersion.java` | `"custom_snapshot"` |
| `MarketMapScaleThreshold.java` | `"custom_scale_threshold"` |
| `MarketValueTierThreshold.java` | `"custom_value_tier_threshold"` |

`@Table` 안의 `name` 값만 바꾼다. 클래스 이름·그 밖의 어노테이션은 건드리지 않는다.

**`@Column` 예외 하나:** `MarketMapCategory.java`의 `@Column(name = "version_id")` → `@Column(name = "snapshot_id")`.
필드 이름 `versionId`는 그대로 둔다. 이것을 빠뜨리면 `ddl-auto=validate`에서 앱이 뜨지 않는다.

### 4-2. 주석 속 테이블 이름

아래 파일의 **주석**에 적힌 옛 테이블 이름을 새 이름으로 바꾼다. 코드는 건드리지 않는다.
`MarketMapCategoryService`, `MarketMapCategoryTreeService`, `MarketMapStockCategoryService`,
`MarketValueTierThresholdService`, `MarketMapQueryService`, `MarketMapCategoryServiceTest`, `MarketMapQueryServiceTest`

### 4-3. 제외 종목 삭제

파일째 지운다:
- `domain/stock/entity/MarketMapExcludedStock.java`
- `domain/stock/repository/MarketMapExcludedStockRepository.java`
- `domain/stock/service/MarketMapExcludedStockService.java`
- `domain/view/dto/ExcludedStockItem.java`

고친다:
- `MarketMapController`:
  - `GET/POST/DELETE /excluded-stocks…` 핸들러 4개와 `marketMapExcludedStockService` 필드·import를 지운다
  - `DELETE /reset` 핸들러는 **남기고**, 안의 `marketMapExcludedStockService.deleteAll();` 한 줄만 지운다
    (`marketMapCategoryService.resetExcludes();` 는 남는다)
  - `excluded-categories` 핸들러는 **건드리지 않는다** — 이름이 비슷하지만 실제로 쓰이는 섹터 제외다
- `MarketMapQueryService`: `listExcludedStocks()` 와 `marketMapExcludedStockRepository` 필드·import를 지운다.
  `listExcludedStocks` 만 쓰던 private 헬퍼 `resolveStockName` 도 같이 지운다(다른 호출부 없음)
- 테스트: `MarketMapControllerTest`, `MarketMapQueryServiceTest` 의 생성자 인자·mock에서 제외 종목 쪽을 뺀다.
  제외 종목 전용 테스트가 있으면 지운다

### 4-4. 검증 grep

```
grep -rn "market_map_\|market_value_tier_threshold\|\"version_id\"" src/main/java src/test/java → 0건
grep -rn "ExcludedStock\|excluded-stocks\|excludedStock" src/main/java src/test/java → 0건
```

Flyway SQL(V1·V2·V3)은 검색 범위에서 뺀다 — 옛 이름이 남는 게 정상이다.
`excluded-categories`, `ExcludedCategor…` 는 두 번째 패턴에 걸리지 않는다(대소문자·철자가 다르다).
걸리면 멈추고 보고한다.

---

## 5. 완료·검증

- `./gradlew spotlessApply build` 통과 (테스트 포함)
- 4-4 grep 둘 다 0건
- CI는 DB 없이 돌아서 V3 는 빌드에서 검증되지 않는다. PR 설명에 V3 전문을 붙인다

배포 뒤 사용자 확인 (설계 쪽이 쿼리를 준다):
- `flyway_schema_history` 에 3 이 `success = t`
- 새 이름의 테이블·제약·시퀀스가 있고 옛 이름이 없다
- 지도·섹터 화면, 관리자 섹터·종목 페이지(`/admin/sector`, `/admin/stock`) 조회·수정이 정상
- 커스텀 섹터 하나를 새로 만들어 insert가 되는지 (시퀀스 이름 변경 확인)

---

## 6. 브랜치·커밋

- 브랜치: `claude/migrate/rename-custom-tables`
- 첫 커밋에 이 지시서를 포함하고, 마지막 커밋에서 이 지시서 파일만 지운다
- 커밋은 나눈다: `refactor:` 제외 종목 삭제 / `migrate:` V3 + `@Table` 변경 (같은 커밋이어야 중간 커밋도
  기동 가능하다) / `docs:` 주석 정리는 `refactor:`에 합쳐도 된다
- **push 하고 PR 까지 올린다.** PR 설명 맨 위에 "운영 이름은 2026-09-24 조회로 확인 완료"를 적는다

---

## 7. 프론트 (별도 PR, 순서 무관)

프론트는 제외 종목 API를 부르는 곳이 없다(함수만 있다). 백엔드와 어느 쪽을 먼저 배포해도 된다.
프론트 구현 프롬프트는 설계 쪽이 따로 준다.
