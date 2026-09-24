# 지시서 — 남은 category 컬럼 두 개의 이름을 바꾼다 (구획 1, PR 2f)

이 파일 하나만 읽고 작업할 수 있게 썼다. 테이블·클래스 이름은 2b·2c에서 끝났고, DB 컬럼 두 개에
`category` 가 남아 있다. 이것만 맞춘다. **동작은 바뀌지 않는다.**

| 테이블 | Before | After | 뜻 |
|---|---|---|---|
| `custom_stock_sector` | `category_id` | `sector_id` | 종목이 배정된 커스텀 섹터 |
| `stock_info` | `category_name` | `industry_name` | 키움 원본 업종명. 커스텀 섹터(`sector`)와 헷갈리지 않게 `industry` 로 부른다 |

마이그레이션은 **새 V2를 만들지 않고 V1을 직접 고친다** (2e와 같은 방식, 사용자 결정). 운영 DB는 사용자가
`ALTER` 와 `flyway repair` 로 맞춘다(4절).

---

## 1. Flyway

`src/main/resources/flyway/V1__create_schema.sql` 을 `docs/instructions-column-rename-pr2f.V1.sql` 내용으로
**통째로 교체**한다. 설계 쪽이 만들고 검증했다 — 한 글자도 고치지 않는다.

검증: 빈 PostgreSQL 17에서 (a) 지금 V1 + 4절의 `ALTER` 두 줄, (b) 새 V1 의 `pg_dump -s` 결과가 동일했다.

---

## 2. Java — 엔티티 필드와 그에 묶인 것만

### 2-1. `domain/custom/entity/CustomStockSector.java`

- 필드 `categoryId` → `sectorId`, `@Column(name = "category_id", …)` → `@Column(name = "sector_id", …)`
- 그 필드를 받는 `create(...)`, `reassign(...)` 파라미터 이름도 `sectorId`
- getter 는 Lombok 이 만든다 → 호출부 `getCategoryId()` 를 `getSectorId()` 로

### 2-2. `domain/custom/repository/CustomStockSectorRepository.java`

파생 쿼리 메서드는 필드 이름에 묶여 있어서 **반드시 같이 바꿔야 기동한다.**

| Before | After |
|---|---|
| `findByCategoryId(Long categoryId)` | `findBySectorId(Long sectorId)` |
| `findByCategoryIdIn(List<Long> categoryIds)` | `findBySectorIdIn(List<Long> sectorIds)` |
| `deleteByCategoryIdIn(List<Long> categoryIds)` | `deleteBySectorIdIn(List<Long> sectorIds)` |

호출부와 테스트의 `verify(...)`·`when(...)` 도 따라 바꾼다.

### 2-3. `domain/stock/entity/StockInfo.java`

- 필드 `categoryName` → `industryName`. 지금 `@Column(length = 50)` 에 이름이 없어 필드 이름에서 컬럼 이름이
  나온다 — **`@Column(name = "industry_name", length = 50)` 으로 이름을 명시한다**
- `create(...)`, `update(...)` 의 해당 파라미터 이름도 `industryName`
- 호출부 `getCategoryName()` → `getIndustryName()`
  (`StockInfoCollector`, `CustomSectorService`, `CustomSectorTreeService`, `CustomStockSectorService`, `MarketMapQueryService`, 테스트)

---

## 3. ★ 바꾸지 않는 것

| 바꾸지 않는 것 | 왜 |
|---|---|
| **dto record 컴포넌트** (`categoryId`, `categoryName`, `originCategoryName` 등) | JSON 키다. 가입·로그인 작업에서 프론트와 같이 바꾼다 (`docs/backlog.md`) |
| 내부 record 컴포넌트 (`StockInfoSyncedEvent.NewStock.categoryName`, `StockInfoCollector.FetchStockInfo.categoryName`, `MarketMapItem` 등) | 이번 범위 밖. 컬럼·엔티티 필드만 맞춘다 |
| 메서드·지역 변수 이름 (`normalizeCategoryName`, `stockCategories` 등) | 이번 범위 밖 |
| 키움 응답 파싱 (`StockInfoResponse` 등 외부 API 필드) | 외부 계약 |
| `ErrorCode`, API URL, 로그 태그 | 2c 와 같은 이유 |
| `docs/` | 이 지시서와 `.V1.sql` 삭제만 예외 |

---

## 4. ★ 운영 반영 — 사용자 몫 (구현자는 하지 않는다)

`stock_info` 는 지도·수집기 전부가 읽는다. **`ALTER` 를 실행한 순간부터 새 앱이 뜰 때까지 옛 앱은 이 컬럼을 못
찾아 에러를 낸다.** 그래서 장 마감(20:00) 뒤, 아래를 **끊지 않고 연달아** 한다.

```
1. PR 병합 (이미지가 :main 으로 준비될 때까지 기다린다)
2. 서버 psql:  ALTER 두 줄 실행 (아래)
3. 서버:       ~/scripts/flyway-cli.sh repair
4. 곧바로 배포 (application)
5. 설계 쪽에 확인 쿼리 결과 전달
```

2번 SQL:

```sql
ALTER TABLE custom_stock_sector RENAME COLUMN category_id TO sector_id;
ALTER TABLE stock_info RENAME COLUMN category_name TO industry_name;
```

둘 다 이름만 바뀌는 메타데이터 작업이라 즉시 끝나고, FK(`fk_custom_stock_sector_sector`)는 그대로 붙어 있다.

로컬 개발 DB는 지우고 새로 만든다.

---

## 5. 완료·검증

- `cmp docs/instructions-column-rename-pr2f.V1.sql src/main/resources/flyway/V1__create_schema.sql` 같음
- `./gradlew spotlessApply build` 통과. **테스트의 검증 내용은 이름 외에 바꾸지 않는다**
- 아래 grep 결과를 PR 설명에 붙인다

```
grep -rnE "getCategoryId\(|getCategoryName\(|ByCategoryId|\"category_id\"|\"category_name\"" src/main/java src/test/java   → 0건
grep -n "category" src/main/resources/flyway/V1__create_schema.sql                                                        → 0건
```

  첫 grep 이 dto record 접근자(`request.categoryId()` 등)를 잡지 않게 짰다. 걸리면 무엇인지 보고한다.
- PR 설명 맨 위: **"★ 배포 직전 서버에서 ALTER 2줄 + flyway repair 필요 (지시서 4절). 장 마감 뒤, 끊지 않고 연달아"**

---

## 6. 브랜치·커밋

- 브랜치: `claude/migrate/rename-category-columns`
- 첫 커밋 `docs:` (이 지시서 + `.V1.sql`) → `migrate:` V1 교체 + 엔티티·리포지토리·호출부 (한 커밋이어야 중간
  커밋도 기동한다) → 마지막 커밋에서 이 지시서와 `.V1.sql` 만 삭제
- push 하고 PR 까지 올린다
