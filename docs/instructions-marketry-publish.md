# 지시서 — MARKETRY 고정본 발행과 내 히트맵(MYMAP) 분리

이 파일 하나만 읽고 작업할 수 있게 썼다. 근거가 필요하면 인용한 코드를 직접 열어 확인하면 된다.
main에서 새 브랜치로 시작한다. **서버(이 저장소)와 프런트(`marketry-frontend`)는 저장소가 다르므로 PR을 따로 올린다(6절).
이 지시서가 다루는 구현은 서버 PR 하나다. 프런트는 5절의 약속(API 모양)만 맞추면 따로 진행한다.**

이 지시서는 독립 검증(`grill-me`)을 거치지 않았다. 사용자가 "복원할 수만 있으면 된다"는 조건으로 건너뛰기로 했다.
그 대신 아래 모든 주장은 작성자가 코드를 열어 확인했다. 틀린 곳을 발견하면 임의로 바꾸지 말고 멈추고 확인한다.

---

## 1. 무엇을 만드나

지금 `MARKETRY`는 **로그인한 사용자 각자의 분류**(`isCustom=true`)다. 이걸 둘로 나눈다.

| 이름 | 코드 값 | 데이터 | 누가 보나 | 누가 고치나 |
|---|---|---|---|---|
| MARKETRY | `marketry` | 운영자가 "올리기"한 **고정본** | 로그인 없이 누구나, 읽기 전용 | 운영자가 올릴 때만 바뀐다 |
| 내 히트맵 | `mymap` | 사용자 각자의 분류 — **지금 `isCustom=true`가 읽는 데이터 그대로** | 로그인한 본인 | 본인 |
| 한국거래소 | `krx` | 거래소 분류 | 누구나 | — |

화면에 보이는 이름은 한글 "내 히트맵"이고, 주소·코드 값은 `mymap`이다.

운영자도 일반 사용자처럼 자기 "내 히트맵"을 만들고 고친다. 운영자가 **"올리기"**를 하면 그 시점의 자기 분류가
새 MARKETRY 고정본이 된다. 운영자가 내 히트맵을 고쳐도, 올리기 전에는 MARKETRY가 바뀌지 않는다.

---

## 2. 확정된 결정

| # | 결정 | 이유 |
|---|---|---|
| 1 | 고정본은 **발행용 시스템 사용자 한 명(`users.id = 900000`)의 분류 데이터**로 저장한다. 올릴 때 운영자의 분류를 이 사용자에게 복사한다 | 지도 계산 코드(`MarketMapQueryService.buildCustomMarketMap`)가 이미 사용자 ID를 받아 그 사용자의 분류로 지도를 만든다. 사용자 ID만 바꿔 넣으면 계산 코드를 하나도 고치지 않는다 |
| 2 | 복사는 **기존 스냅샷 저장·복원 코드를 그대로 쓴다**: `CustomSectorTreeService.serializeCurrentSnapshot(운영자)` → `CustomSectorTreeService.restore(json, 900000, 스냅샷id)` | 새 복사 로직을 만들지 않는다 |
| 3 | 올릴 때마다 `custom_snapshot` 행을 **발행 사용자 소유로 하나 더 저장**한다. 이게 버전 목록이다. 이전 버전으로 되돌리는 것도 같은 복원 코드다. **삭제 API는 만들지 않는다** | 사용자 조건: 잘못되면 복원할 수 있어야 한다 |
| 4 | 고정본에는 **약칭을 넣지 않는다**. 올릴 때 스냅샷 JSON의 `aliases`를 빈 목록으로 바꿔서 저장·복원한다 | 약칭은 쓰지 않기로 했다. 지도 박스 이름은 `alias ?? stockName`이라 약칭이 남으면 고정본에 그대로 보인다 |
| 5 | 올리기·버전 목록·되돌리기는 **`/api/admin/marketry/publications` 아래**에 둔다. `AuthConfiguration`의 기존 규칙 `/api/admin/**` = `hasRole("ADMIN")`이 그대로 적용된다. 권한 코드를 새로 쓰지 않는다 | 권한 부여는 기존 `ADMIN` 역할을 쓴다 |
| 6 | `GET /api/map`에 선택 파라미터 **`source`**(`krx`·`marketry`·`mymap`)를 더한다. **`source`가 없으면 지금 동작 그대로**다(`isCustom=true`면 내 분류, 아니면 거래소). `source`가 있으면 `isCustom`은 무시한다 | 옛 프런트(배포돼 있는 것)가 그대로 동작해야 서버를 먼저 배포하고 롤백도 안전하다 |
| 7 | `source=marketry`는 **로그인 없이** 읽는다. 이 경우 사용자별 값(제외 업종 등)은 읽지 않는다. `GET /**`는 이미 `permitAll`이라 보안 설정을 바꾸지 않는다 | 비로그인 공개 |
| 8 | `source=mymap`은 지금 `isCustom=true`와 같다 — 로그인 필수(`CurrentUser.requireId()`) | 기존 동작 |
| 9 | 텔레그램 발송·랭킹 경로의 폴백(로그인 사용자가 없을 때)을 **운영자(`ownerUserId`)에서 발행 사용자로 바꾼다**. `MarketryProperties.userIdOrOwner`를 `userIdOrPublished`로 이름을 바꾸고 폴백 값을 발행 사용자 ID로 한다 | 안 바꾸면 운영자가 내 히트맵을 고치는 즉시 텔레그램 캡션이 바뀐다. 지도 이미지(MARKETRY=고정본)와 캡션이 어긋난다 |
| 10 | 발행 사용자 ID는 설정 `marketry.published-user-id`로 두고 기본값은 `900000`이다. 마이그레이션이 이 행을 만든다 | 환경별로 다른 값이 필요 없다 |
| 11 | 첫 고정본은 **마이그레이션 SQL로 만들지 않는다**. 배포 뒤 운영자가 "올리기"를 한 번 누른다. 그 전에는 고정본이 비어 있어 `source=marketry`가 **빈 지도**를 돌려준다 | 운영 데이터를 건드리는 SQL을 쓰지 않는다. 운영자 ID는 환경마다 달라 SQL로 못 박을 수 없다 |

---

## 3. 데이터

### 3-1. 마이그레이션 — `V8__published_user.sql`

`src/main/resources/flyway/`에 만든다(`V7__rename_new_listing_sector.sql` 다음). **행 하나 추가뿐**이라 하위호환이다
(`docs/rules/process.md` "DB 마이그레이션 규칙"). 기존 데이터·스키마를 바꾸지 않는다.

```sql
INSERT INTO users (id, issuer, sub, role)
OVERRIDING SYSTEM VALUE
VALUES (900000, 'marketry-system', 'published', 'USER')
ON CONFLICT (id) DO NOTHING;
```

- `users.id`는 `GENERATED ALWAYS AS IDENTITY`라서 `OVERRIDING SYSTEM VALUE`가 필요하다(`V1__create_schema.sql` 1~3행).
- `issuer`·`sub`는 로그인으로는 절대 만들어질 수 없는 값이다. 이 사용자는 로그인할 수 없다.
- `role`은 `USER`다(`ADMIN` 아님).
- 이 행이 없으면 `custom_sector.user_id` 등의 외래키 때문에 복사가 실패한다.

### 3-2. 설정

- `MarketryProperties`(`domain/notification/properties/`)에 세 번째 컴포넌트 `Long publishedUserId`를 더한다.
  이 record를 직접 만드는 곳이 **테스트 두 곳**이다 — `ScreenshotClientManualTest.java:32`,
  `DevLoginControllerContextTest.java:26`. 둘 다 세 번째 인자로 `900000L`을 넘기게 고친다.
- `application.properties`에 `marketry.published-user-id=${PUBLISHED_USER_ID:900000}`을 더한다(32행 `marketry.owner-user-id` 바로 아래).
  `application-local.properties`·`application-prod.properties`는 건드리지 않는다.
- `userIdOrOwner(Long currentUserId)`를 `userIdOrPublished(Long currentUserId)`로 바꾼다: `currentUserId == null`이면
  `publishedUserId`, 아니면 `currentUserId`. 호출부는 `MarketMapQueryService.customDataUserId()`(718행)와
  `SectorTierAggregationService.aggregateBySector`(44행) 둘뿐이다(`grep userIdOrOwner`로 확인).
  `ownerUserId` 필드는 그대로 둔다 — `ScreenshotClient`(63행)와 `DevLoginController`(47행)가 쓴다.

---

## 4. 서버 API

### 4-1. 올리기·버전·되돌리기

컨트롤러: `domain/custom/controller/MarketryPublishController`(새 파일). 서비스: `domain/custom/service/MarketryPublishService`(새 파일).
요청 본문은 기존 `SnapshotLabelRequest`를 그대로 쓴다. 응답은 기존 `SnapshotItem`(id, label, createdAt, updatedAt)이다.

| 메서드·경로 | 요청 | 성공 | 동작 |
|---|---|---|---|
| `POST /api/admin/marketry/publications` | `{ "label": "..." }` | `200 SnapshotItem` | 호출한 운영자의 분류를 고정본으로 올린다(아래) |
| `GET /api/admin/marketry/publications` | — | `200 SnapshotItem[]` | 발행 사용자 소유 스냅샷을 최신순으로. 현재 형식(`isCurrentSnapshotFormat`)이 아닌 것은 빼낸다 |
| `POST /api/admin/marketry/publications/{id}/restore` | — | `200` | 그 버전을 고정본으로 되돌린다 |

**올리기(`publish`) 순서 — 한 트랜잭션이다.**

1. `adminId = CurrentUser.requireId()`.
2. `json = customSectorTreeService.serializeCurrentSnapshot(adminId)`.
3. `json`의 `aliases`를 빈 목록으로 바꾼다 — `CustomSectorTreeService`에 `public String withoutAliases(String snapshotJson)`을 더한다
   (`parseSnapshot`으로 읽어 `CustomSnapshotPayload`의 `aliases`만 `List.of()`로 바꿔 `toJson`으로 다시 쓴다. `toJson`은 지금 private이라
   같은 클래스 안에서 쓴다).
4. `saved = customSnapshotRepository.save(CustomSnapshot.create(publishedUserId, label, json))`.
5. `customSectorTreeService.restore(json, publishedUserId, saved.getId())`.
6. `saved`를 `SnapshotItem`으로 돌려준다.

운영자 본인 데이터는 읽기만 한다. 발행 사용자 데이터만 바뀐다. `label`은 `SnapshotLabelRequest`가 이미 `@NotBlank`로 검증하므로
서비스에서 기본 이름을 만들지 않는다(프런트가 항상 보낸다). 현재 시각을 읽는 코드가 없다.

**되돌리기(`restoreVersion`)**: `findByIdAndUserId(id, publishedUserId)`로 찾고(없으면 `NotFoundException(ErrorCode.SNAPSHOT_NOT_FOUND, id)`),
현재 형식이 아니면 `BadRequestException(ErrorCode.SNAPSHOT_FORMAT_UNSUPPORTED)`, 맞으면 `restore(json, publishedUserId, id)`.
기존 `CustomSnapshotService.restore`(`CurrentUser`를 쓴다)를 고치지 않는다. 새 서비스가 같은 협력 객체를 직접 쓴다.

### 4-2. 지도 조회에 `source` 더하기

`MarketMapController.getMarketMap`(`domain/view/controller/`, 34~48행):

- `@RequestParam boolean isCustom`을 `@RequestParam(defaultValue = "false") boolean isCustom`으로 바꾼다.
- `@RequestParam(required = false) String source`를 더한다. `docs/rules/style.md` 9절에 따라 서비스 메서드의 파라미터 순서와
  컨트롤러 `@RequestParam` 순서를 맞춘다.
- 새 enum `domain/view/enums/ClassificationSource { KRX, MARKETRY, MYMAP }`을 만든다. 정적 팩토리 두 개:
  - `from(String source)` — `krx`·`marketry`·`mymap`(대소문자 무시)이면 해당 값, 그 외는 `BadRequestException`(새 `ErrorCode`가
    필요하면 문장형 메시지로 `CLASSIFICATION_SOURCE_INVALID`를 더한다).
  - `resolve(String source, boolean isCustom)` — `source`가 `null`이면 `isCustom ? MYMAP : KRX`, 아니면 `from(source)`.
- 컨트롤러는 `switch`로 분기한다: `KRX` → `getDefaultMarketMap`, `MYMAP` → `getCustomMarketMap`(지금 그대로),
  `MARKETRY` → 새 `getPublishedMarketMap`.
- `MarketMapQueryService`에 `getPublishedMarketMap(MarketQuery, LocalDateTime snapshotTime, boolean nxtOnly, ChangeRateBasis basis)`를 더한다.
  `getCustomMarketMap`(162~170행)과 똑같되 `userId`를 `CurrentUser.requireId()`가 아니라 `marketryProperties.publishedUserId()`로 쓴다.
  중복을 줄이려면 두 메서드가 공통 private 메서드를 부르게 하되 `getCustomMarketMap`의 동작·시그니처는 바꾸지 않는다.

---

## 5. 프런트와의 약속(프런트는 별도 PR)

프런트가 따를 API 모양이다. 서버 PR은 이것만 보장하면 된다.

- 지도 요청: `GET /api/map?market=...&source=krx|marketry|mymap&...`. 로그인 없이 `source=marketry`가 200이다.
  `source=mymap`을 로그인 없이 부르면 지금 `isCustom=true`와 같이 401이다.
- 올리기·되돌리기는 `ADMIN`만. 프런트가 `GET /api/auth/session`의 `role`로 버튼을 보일지 정한다(이미 있다).
- 코드 값 이름은 프런트도 `mymap`으로 맞춘다(`HeatmapKey`, 지도 늘리기 주소 인자 `stretchMymap` 등). 화면 표시는 한글 "내 히트맵".

---

## 6. 안 하는 것

- 사용자별 지도 설정(제외 업종·가격 구간 등)을 고정본에 적용하는 것. `source=marketry`는 발행 사용자의 값만 쓴다.
- 고정본 삭제 API, 버전 이름 바꾸기, 자동 예약 발행.
- `custom_snapshot`·`custom_*` 테이블 구조 변경.
- 기존 사용자 데이터 이전. 다른 사용자(현재 운영자 본인 계정 둘)의 분류는 그대로 그 사용자의 "내 히트맵"이 된다 — 옮길 게 없다.
- 약칭 API(`/api/custom/stock-sectors/*/alias`)와 `custom_stock_alias` 테이블 삭제. 일단 둔다.
- 프런트 구현(별도 저장소·별도 PR).
- 서버 코드 외 운영 설정 변경(환경 변수 추가 없음).

---

## 6-1. 프런트 PR 요약(참고, 이 지시서의 구현 범위 아님)

서버 배포 후 별도 PR: `HeatmapKey`에 `mymap` 추가, 설정창 업종 분류 3칸 활성화, `MARKETRY` 선택 시 `source=marketry`로
요청하고 읽기 전용 처리, 운영자(`role === 'ADMIN'`)에게 "올리기" 버튼.

---

## 7. 완료 기준과 검증

**테스트로 확인한다**(`docs/rules/testing.md` — 단위 테스트, Mockito, DB·Spring 컨텍스트 없음):

- `ClassificationSourceTest`: `from`의 세 값(대소문자 포함)과 잘못된 값, `resolve`의 네 경우(`source` 없음 + `isCustom` true/false, `source` 있음 + `isCustom`이 무엇이든).
- `MarketryPublishServiceTest`:
  - 올리기가 운영자 ID로 직렬화하고, 약칭을 뺀 JSON을 **발행 사용자 ID**로 `save`하고, 같은 JSON으로 `restore(json, 발행 ID, 저장된 id)`를 부른다.
  - 되돌리기: 없는 id → `NotFoundException`, 옛 형식 → `BadRequestException`, 정상 → `restore` 호출.
  - 목록이 발행 사용자 소유만, 최신순, 현재 형식만 돌려준다.
- 기존 `CustomSectorTreeServiceTest`에 추가: `withoutAliases`가 `aliases`만 비우고 나머지(섹터·배정·구간·설정)를 그대로 둔다.
- `MarketryPropertiesTest`: `userIdOrPublished(null)` → 발행 ID, `userIdOrPublished(7L)` → 7.

**하지 않는 테스트**: 컨트롤러 위임, JPA 매핑, 마이그레이션 SQL(DB 테스트가 없다).

**끝났다고 보는 조건**

1. `./gradlew build` 통과(포맷 검사·테스트 포함).
2. 위 테스트가 모두 있고 통과.
3. PR 설명에 적는다: 이 지시서가 독립 검증을 거치지 않았다는 것, 지시서에 없는 판단을 한 곳.

**배포 후 수동 확인**(운영자가 한다. 구현 세션이 하지 않는다)

1. 서버 배포 전에 DB 마이그레이션이 운영에 실행됨을 사용자에게 알리고 확인받는다.
2. 배포 뒤 `GET /api/map?market=ALL_STOCK&source=marketry` — 로그인 없이 200. 첫 올리기 전이라 빈 지도다.
3. 운영자가 `POST /api/admin/marketry/publications`로 올린 뒤 같은 요청이 운영자 분류의 지도를 돌려준다.
4. 옛 프런트(`source` 없이 `isCustom=true|false`)가 그대로 동작한다.
5. 되돌리기: `POST .../{id}/restore` 후 2번 요청이 그 버전의 지도를 돌려준다.

**롤백**: `docs/operations.md`의 롤백(Release → target `rollback`)으로 이미지를 되돌린다. `V8`은 행 하나 추가라 되돌린 옛 이미지도
뜬다(옛 코드는 그 사용자를 쓰지 않는다). 고정본이 잘못 올라갔다면 이미지 롤백이 아니라 4-1의 되돌리기 API를 쓴다.

---

## 8. 작업이 끝나면

- 이 지시서는 **삭제한다**(휘발성 문서).
- 참고: 스냅샷 정리 배치(`SnapshotRetentionScheduler`)는 `sector_price_snapshot`(시세)만 지운다. `custom_snapshot`은 건드리지 않아서
  고정본 버전이 배치로 사라지지 않는다.
- `docs/decisions.md`에 반영할 것(설계 역할이 한다): 고정본을 발행 사용자 한 명으로 저장한 이유와 접은 선택지(별도 `published_*` 테이블 — 계산 코드를
  새로 써야 해서 접었다), 텔레그램 폴백을 발행 사용자로 바꾼 이유.
- `docs/backlog.md`에 남길 것: 고정본 되돌리기 화면, 고정본 삭제, 자동 예약 발행, 약칭 API·테이블 정리.
