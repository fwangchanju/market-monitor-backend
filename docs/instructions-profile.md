# 지시서 — 프로필 닉네임과 사진

이 파일 하나만 읽고 작업할 수 있게 썼다. 근거가 필요하면 인용한 코드를 직접 열어 확인하면 된다.
main에서 새 브랜치로 시작한다. **작업은 세 PR로 나눈다(5절). 한 PR에 섞지 않는다.**

---

## 1. 무엇을 만드나

로그인한 사용자가 프로필 페이지에서 **닉네임**과 **프로필 사진**을 등록·수정·삭제한다. 등록한 값은
**본인 화면에만** 보인다. 다른 사용자에게 보이는 곳은 만들지 않는다.

지금은 사용자별 사진·닉네임이 어디에도 없다. 프런트 `NavBar.tsx`, `MarketMapCustomPage.tsx`,
`SectorChangeRatePage.tsx`가 모두 같은 기본 이미지(`src/assets/account_avatar.png`)를 쓴다.
`UserAccount`(`users` 테이블)에는 `email`만 있다.

---

## 2. 확정된 결정

| # | 결정 | 이유 |
|---|---|---|
| 1 | 사진은 **DB(`bytea`)에 저장**한다. 파일 볼륨·외부 저장소는 쓰지 않는다 | 256px JPEG는 한 장에 10~30KB라 사용자 1만 명이어도 약 200MB다. 운영 서버 설정 변경이 필요 없다 |
| 2 | 저장 전에 서버가 **256×256 JPEG로 다시 만든다**(가운데 정사각형 자르기, 품질 0.85, 투명 배경은 흰색). 원본은 저장하지 않는다 | 저장 용량 고정, 형식 통일 |
| 3 | 허용 형식은 **JPEG, PNG**. 서버가 파일 앞부분 바이트(매직 바이트)로 판별한다. `Content-Type` 헤더·파일 확장자는 믿지 않는다 | 위조 방지 |
| 4 | 사용자가 고를 수 있는 원본은 **2MB 이하**다. **브라우저가 업로드 전에 256×256 JPEG로 줄여서** 올린다. 서버가 받는 파일은 **512KB 이하**다 | 운영 nginx(`infra/nginx.conf`)에 `client_max_body_size`가 없어서 기본값 1MB가 적용된다. 줄여서 올리면 nginx 설정을 바꾸지 않아도 된다 |
| 5 | 닉네임은 **2~12자**, **한글·영문·숫자·밑줄(`_`)만** 허용한다. 공백·특수문자·이모지는 거부한다. 앞뒤 공백은 잘라서 검사한다 | 단순하고 안전 |
| 6 | 닉네임은 **대소문자를 구분하지 않고 중복 불가**다. `Abc`와 `abc`는 같은 닉네임이다 | 사칭 방지 |
| 7 | 닉네임 욕설·금칙어 필터는 **하지 않는다** | 범위 밖. `docs/backlog.md`에 남긴다(9절) |
| 8 | 사진과 닉네임은 **본인에게만** 내려준다. 조회 API는 전부 로그인 필수다 | 다른 사용자에게 노출하지 않기로 했다 |
| 9 | 지도 상단 바 가운데 표시는 이번에 **사진만** 사용자 사진으로 바꾼다. 거래소·MARKETRY 히트맵을 볼 때는 지금처럼 히트맵 이름을 보여준다. **"내 히트맵"(사용자가 직접 만든 히트맵)을 볼 때는 사진은 그대로 두고 히트맵 이름 자리에 그 사용자의 닉네임을 보여준다(프사 + 닉네임). 닉네임을 아직 안 정했으면 "내 히트맵"이라고 보여준다.** 단 "내 히트맵"은 지금 설정창에서 "준비 중"(비활성)이라 이번 작업에서는 이 표시를 만들지 않고 `docs/backlog.md`에 남긴다 | 홈페이지가 제공하는 히트맵은 거래소·MARKETRY 둘이고, 사용자가 만든 히트맵에서는 닉네임만 뜨는 것이 사용자 결정이다 |

---

## 3. 데이터

### 3-1. 새 테이블 — `V6__user_profile.sql`

기존 `V5__stock_info_nxt_enabled.sql` 옆(`src/main/resources/flyway/`)에 만든다. **새 테이블 추가뿐**이라
하위호환이다(`docs/rules/process.md` "DB 마이그레이션 규칙").

```sql
CREATE TABLE user_profile (
    user_id          BIGINT       PRIMARY KEY REFERENCES users (id),
    nickname         VARCHAR(12),
    image            BYTEA,
    image_updated_at TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX uk_user_profile_nickname_lower ON user_profile (lower(nickname));
```

- 닉네임이 `NULL`인 행은 여러 개 있어도 된다(Postgres 유니크 인덱스는 `NULL`끼리 충돌하지 않는다).
- 프로필 행은 처음 저장할 때 만든다. 로그인·가입 시 만들지 않는다.
- 타임스탬프 컬럼은 `V1__create_schema.sql`의 `users.created_at`(`TIMESTAMP`)과 같은 타입이다.

### 3-2. 엔티티

`domain/auth/entity/UserProfile`(새 파일). `UserAccount`를 고치지 않는다.
`@Id`는 `Long userId` 하나이고 `@GeneratedValue`를 쓰지 않는다(값은 `users.id`를 그대로 넣는다. `UserAccount`는 IDENTITY를
쓰지만 이 엔티티는 다르다). 필드 순서는 `docs/rules/style.md`의 엔티티 규칙(id → UK 컬럼 → 나머지 → createdAt → updatedAt)을
따르므로 `nickname`이 `userId` 다음이다. 사진 필드는 `byte[]`이고
`@Column(columnDefinition = "bytea")`로 선언한다(`@Lob`은 쓰지 않는다). `ddl-auto=validate`가 통과해야 한다.

---

## 4. API

모두 `/api/profile` 아래. 컨트롤러는 `domain/auth/controller/ProfileController`(새 파일), 사용자 ID는
`CurrentUser.requireId()`로 얻는다.

| 메서드·경로 | 요청 | 성공 | 실패 |
|---|---|---|---|
| `GET /api/profile` | — | `200 { nickname: string\|null, hasImage: boolean, imageVersion: number\|null }` | 비로그인 `401` |
| `PUT /api/profile/nickname` | JSON `{ "nickname": "..." }` | `200` 위와 같은 본문 | 규칙 위반 `400`, 중복 `409`, 비로그인 `401` |
| `PUT /api/profile/image` | `multipart/form-data`, 파트 이름 `file` | `200` 위와 같은 본문 | 512KB 초과 `413`, JPEG·PNG가 아니거나 해석 불가 `415`, 크기 이상(아래) `400`, 비로그인 `401` |
| `DELETE /api/profile/image` | — | `204` | 비로그인 `401` |
| `GET /api/profile/image` | — | `200`, 본문 JPEG, `Cache-Control: private, max-age=86400` | 사진 없음 `404`, 비로그인 `401` |

- `imageVersion`은 `image_updated_at`의 epoch 밀리초다. 프런트가 `GET /api/profile/image?v={imageVersion}`로
  불러서 사진이 바뀌면 브라우저 캐시가 갱신되게 한다. 서버는 `v` 값을 검사하지 않는다.
- 사진 서버 처리 순서: ① 크기 512KB 이하 확인 → ② 매직 바이트 확인 → ③ `ImageIO`의 `ImageReader`로
  **픽셀을 읽기 전에 가로·세로를 먼저 확인**해서 한 변이 4096px을 넘으면 `400`(압축 폭탄 방지) → ④ 디코드 →
  ⑤ 가운데 정사각형으로 자르고 256×256으로 줄임, 투명 배경은 흰색 → ⑥ JPEG(품질 0.85)로 인코딩 → ⑦ 저장.
- 서버는 이미지 처리에 JDK 기본 `javax.imageio`만 쓴다. 새 라이브러리를 추가하지 않는다.
- 같은 사용자의 중복 요청이 동시에 와도 유니크 인덱스 위반은 `409`로 바꿔서 응답한다(예외를 `500`으로 새게 하지 않는다).
- 응답에 사진 바이트·이메일 등 다른 정보를 섞지 않는다.

### 4-1. 보안 설정 — 반드시 확인

`AuthConfiguration.java`의 필터 체인은 `GET /**`를 모두 `permitAll`로 연다. 그냥 두면 `GET /api/profile`,
`GET /api/profile/image`가 **로그인 없이 열린다.** `/api/custom/**`가 `.authenticated()`인 줄 **앞에**
`.requestMatchers("/api/profile", "/api/profile/**").authenticated()`를 추가한다(하위 경로가 없는 `/api/profile`도 포함한다). 순서가 중요하다(Spring Security는 먼저
일치한 규칙을 쓴다). 소유자 캡처 토큰(`AuthTokenFilter`)은 GET이면 `/api/profile/**`도 읽을 수 있다 — 소유자 본인 데이터이므로
이번에는 막지 않고 PR 설명에 한 줄 남긴다. 이 규칙이 동작한다는 **테스트**(비로그인 `GET /api/profile`·`GET /api/profile/image`가 `401`)를 반드시 만든다.

### 4-2. 세션 응답 확장

`AuthSessionResponse`(`record`)에 두 필드를 **끝에** 추가한다: `String nickname`, `Long profileImageVersion`
(둘 다 없으면 `null`). `anonymous()`는 둘 다 `null`이다. `AuthService.session(principal)`이 프로필을 조회해
채운다(`AuthService.java` 158행 부근이 `AuthSessionResponse`를 만드는 곳이다). `AuthControllerTest`가
`new AuthSessionResponse(true, 42L, ...)`를 세 곳에서 4개 인자로 만들고 있어서, 필드를 추가하면 컴파일이 깨진다 —
이 테스트의 생성자 호출을 새 시그니처에 맞춰 고친다(새 두 인자는 `null`).
이렇게 해서 프런트가 세션 한 번으로 닉네임과 사진 버전을 안다.

### 4-3. 구현 세부 (확정)

**오류 응답**
- 닉네임 규칙 위반·사진 크기 이상은 `BadRequestException`, 닉네임 중복은 `ConflictException`으로 던진다. `ErrorCode`에 `PROFILE_*` 항목을 추가한다.
- 413(512KB 초과)과 415(형식 불가·해석 불가)는 `ResponseStatusException`으로 던진다. `GlobalExceptionHandler`가 `ErrorResponse`를 그대로 다시 던지므로 상태 코드가 유지되고 텔레그램 알림도 가지 않는다. 새 예외 타입은 만들지 않는다.
- 512KB는 서비스가 `file.getSize()`로 검사한다. Spring Boot 기본 업로드 제한(파일 1MB)은 그대로 둔다. 1MB를 넘는 요청은 컨테이너가 먼저 거부하며 이 경로는 자동 테스트로 확인할 수 없다.
- `GET /api/profile`은 프로필 행이 없으면 `{ nickname: null, hasImage: false, imageVersion: null }`을 돌려준다.

**닉네임**
- 입력은 `Normalizer.Form.NFC`로 정규화한 뒤 `String.strip()`(전각 공백까지 자른다)으로 앞뒤를 자르고 검사한다.
- 글자 수는 코드포인트 기준 2~12자다. 허용 문자는 한글 완성형(`가-힣`), 영문 `A-Za-z`, 숫자 `0-9`, `_`뿐이다(자음·모음 단독, 악센트 문자 불가).
- 빈 값·공백만 입력은 `400`이다. 닉네임을 비우는 API는 만들지 않는다.
- 같은 사용자가 자기 닉네임을 같은 값으로 다시 저장하면 `200`이다(중복 검사에서 본인 행은 제외한다).

**저장과 동시성**
- 프로필 행이 없으면 처음 저장할 때 만든다. 저장은 `saveAndFlush`로 하고, 닉네임 유니크 인덱스(`uk_user_profile_nickname_lower`) 위반만 `409`로 바꾼다. 위반한 제약 이름으로 구분한다.
- 같은 사용자의 닉네임 저장과 사진 저장이 동시에 와서 기본키(`user_id`) 위반이 나면 **한 번만 다시 시도**한다(행을 다시 읽고 저장). 그래도 실패하면 `500`이다.
- 중복 사전 확인 쿼리는 빠른 응답용일 뿐이다. 최종 방어는 DB 유니크 인덱스 위반을 변환하는 것이다.

**사진 처리 세부**
- 투명 PNG는 `TYPE_INT_RGB` 흰 배경 이미지에 그려서 합성한다(ARGB를 그대로 JPEG로 쓰지 않는다).
- JPEG 품질 0.85는 `ImageWriteParam`으로 지정한다. `ImageIO.write(...)` 기본 호출은 0.75라서 쓰지 않는다.
- `ImageReader`와 `ImageInputStream`은 반드시 닫는다(`dispose`/`close`). 디코드 실패(`IIOException`, 손상된 파일, 지원하지 않는 JPEG, 다중 프레임 불가)는 `415`다.
- 한 변 상한은 4096px로 둔다.

**저장 값**
- `imageVersion`은 `image_updated_at`을 `Zone.KST.zoneId()` 기준 epoch 밀리초로 바꾼 값이다.
- 사진 삭제 시 `image`와 `image_updated_at`을 둘 다 `NULL`로 만든다. 닉네임만 바꿀 때는 `image_updated_at`을 건드리지 않는다.

**세션 조회는 사진 바이트를 읽지 않는다**
- 세션 응답용 조회는 `nickname`과 `image_updated_at`만 가져온다. `UserProfileRepository`에 Spring Data **프로젝션 인터페이스**(`ProfileSummary`: `getNickname()`, `getImageUpdatedAt()`)를 반환하는 파생 메서드를 둔다. 엔티티 전체를 읽으면 `/api/auth/session`이 호출될 때마다 사진 10~30KB를 읽게 된다.
- `AuthService`에는 새 `UserProfileService`를 주입한다. `AuthService`의 생성자 인자가 늘어서 `AuthServiceTest.java:38-46`의 `new AuthService(...)`도 새 인자(`mock(UserProfileService.class)`)를 넣어 고쳐야 한다.
- `AuthService.session()`은 `AuthController`의 `session`·`refresh`, `DevLoginController`가 함께 부르므로 한 곳을 고치면 세 경로에 같이 반영된다.

---

## 5. 작업 순서 — 세 PR

`docs/rules/process.md`의 마이그레이션 규칙(예외 없음)에 따라 나눈다.

1. **PR 1 — 마이그레이션만.** 시작 직전에 `origin/main`과 열린 PR의 `src/main/resources/flyway/`에 `V6`이 이미 있는지 확인한다(있으면 다음 번호를 쓰고 지시서의 번호를 같이 고친다). `V6__user_profile.sql`만 넣는다. 병합 → 백엔드 배포 → 운영 DB에 적용 확인.
2. **PR 2 — 백엔드 기능.** 엔티티, 서비스, 컨트롤러, 보안 규칙, 세션 응답 확장, 테스트. 병합 → 백엔드 배포.
3. **PR 3 — 프런트엔드.** 백엔드(PR 2)가 배포된 뒤에 시작하고 배포한다.

프런트가 먼저 나가면 없는 API를 부르므로 순서를 바꾸지 않는다.

---

## 6. 프런트엔드 (PR 3)

- `src/types/api.ts`: `AuthSessionResponseSchema`에 `nickname`, `profileImageVersion`을 `.nullish()`로 추가한다.
- `src/api/profile.ts`(새 파일): 4절의 `/api/profile` 다섯 API 호출. 응답은 zod로 검증한다.
- `ProfilePage.tsx`: 계정 정보 아래에 "닉네임"과 "프로필 사진" 구역을 만든다.
  - 닉네임: 입력칸 + 저장 버튼. 2~12자·허용 문자는 입력 즉시 검사해서 안내한다. `409`는 "이미 사용 중인 닉네임입니다"로 안내한다.
  - 사진: 현재 사진(없으면 기본 이미지) 미리보기, "사진 선택", "삭제" 버튼.
    - 파일 선택 시 JPEG·PNG가 아니거나 2MB를 넘으면 **서버에 보내지 않고** 안내한다.
    - 통과하면 `canvas`로 가운데 정사각형 자르기 + 256×256 + JPEG(품질 0.85)로 줄인 뒤 올린다.
    - 줄인 결과가 512KB를 넘으면 올리지 않고 안내한다.
- 사진·닉네임을 저장하거나 삭제하면 세션 쿼리(`authKeys.session()`)를 무효화해서 화면 전체가 새 값을 쓰게 한다.
- 표시 위치:
  - `NavBar.tsx` 우상단 프사 버튼, 계정 메뉴 상단: 사용자 사진(없으면 기본 이미지). 계정 메뉴에는 닉네임도 보여준다(없으면 지금처럼 "내 계정").
  - `MarketMapCustomPage.tsx`, `SectorChangeRatePage.tsx`의 상단 바 가운데 프사: 사용자 사진(없거나 비로그인이면 기본 이미지).
- 사진 주소는 `/api/profile/image?v={profileImageVersion}` 한 곳에서 만드는 훅(`useProfileImageSrc` 같은 이름)으로 모아서 위 세 곳이 같이 쓴다.
- 기존 프사 모양(둥근 사각형, 크기)은 바꾸지 않는다.
- **업로드 요청**: `src/api/client.ts`의 axios 기본 헤더가 `Content-Type: application/json`이라서 `FormData`를 그대로 보내면 axios가 JSON으로 바꿔 보내 파일이 사라진다. 사진 업로드 호출은 요청 단위로 `Content-Type`을 `undefined`로 덮어써서 브라우저가 `multipart/form-data` 경계를 붙이게 한다.
- **세션 객체 리터럴**: 새 필드를 `src/api/client.ts`(`markSessionAnonymous`), `src/hooks/useSession.ts`(`ANONYMOUS_SESSION`), `src/mocks/handlers.ts`(`anonymousSession`, `authenticatedSession`)에도 `null`로 넣는다. 사진 주소 훅은 `undefined`와 `null`을 모두 "사진 없음"으로 처리한다.
- **mock 모드**: `src/mocks/handlers.ts`에 `/api/profile` 다섯 개 핸들러를 추가한다(조회·닉네임·사진 올리기·삭제·사진 받기). 이미지 응답은 `src/assets/account_avatar.png`와 같은 정적 이미지를 쓴다. 핸들러가 없으면 `onUnhandledRequest: 'bypass'` 때문에 요청이 백엔드로 나가 오류가 난다.
- **사진 로딩 실패**: `<img>`는 axios 인터셉터를 타지 못해 인증 쿠키가 만료된 순간에는 401이 나서 깨진 이미지가 보일 수 있다. 사진을 그리는 `<img>` 모두에 `onError`를 걸어서 기본 이미지로 바꾼다.
- **파일 선택칸**: 같은 파일을 다시 고를 수 있게 선택 직후 `input.value`를 비운다. 이미지 디코드에 실패하면(예: 확장자만 `.jpg`인 HEIC) "사진을 읽을 수 없습니다"로 안내한다. 미리보기용 `objectURL`은 쓰고 나면 `URL.revokeObjectURL`로 해제한다.
- 프로필 데이터는 세션 응답에서만 읽는다. 별도의 프로필 조회 쿼리는 만들지 않는다.

---

## 7. 완료 기준과 검증

**백엔드 (자동 테스트, PR 2)** — CI(`ci.yml`)는 DB 없이 `spotlessCheck compileJava compileTestJava test`만 돌린다. DB가 필요한 항목은 아래 "로컬 수동 확인"으로 나눈다. 테스트용 이미지는 코드에서 `BufferedImage`로 만든다.
- 닉네임 검증(단위): 1자·13자·공백 포함·특수문자·이모지·빈 값 → `400`. 앞뒤 공백(전각 포함)은 잘려서 통과. NFD로 입력한 한글은 NFC로 바뀌어 통과.
- 닉네임 중복(`UserProfileRepository`를 목으로): 중복이면 `409`. 본인이 같은 값을 다시 저장하면 `200`. 닉네임 인덱스 위반 예외가 `409`로 바뀌고, 다른 제약 위반은 한 번 다시 시도한다.
- 사진(단위): 정상 JPEG·PNG → 결과가 256×256 JPEG. 투명 PNG → 흰 배경. 직사각형 사진 → 가운데 정사각형. 손상된 JPEG(앞부분만 있는 파일) → `415`.
- 사진 거부: 512KB 초과 `413`(앱 수준 검사만 검증된다), 텍스트 파일·GIF·확장자만 `.jpg`인 가짜 파일 `415`, 한 변 4096px 초과 `400`.
- `GET /api/profile/image`: 사진 없으면 `404`, 있으면 `image/jpeg`이고 `Cache-Control: private, max-age=86400` 헤더가 있다.
- 보안(`AuthConfigurationTest` 방식 필터 체인 테스트, 서블릿 경로 설정 포함): 비로그인으로 다섯 API를 호출하면 전부 `401`. **쓰기 요청(`PUT`·`DELETE`)에는 `Origin` 헤더를 허용 목록 값으로 넣는다** — 없으면 `OriginCheckFilter`가 `403`을 내서 `401` 기준과 어긋난다.
- 다른 사용자의 값이 내려가지 않는다(서비스 단위: A로 저장 후 B 조회는 B의 값).
- 세션 응답: 프로필이 없으면 `nickname`·`profileImageVersion`이 `null`, 저장 후에는 값이 있다.
- `AuthControllerTest`, `AuthServiceTest`가 새 시그니처로 컴파일되고 통과한다.

**로컬 수동 확인 (PR 2, 결과를 PR 설명에 적는다)** — 로컬 DB로 앱을 띄워서 확인한다.
- `ddl-auto=validate`와 `bytea` 매핑이 통과한다(앱이 정상 기동한다).
- 대소문자만 다른 닉네임을 두 번째로 저장하면 `409`다(`lower()` 유니크 인덱스).
- 1MB를 넘는 파일을 올리면 `413`이다(컨테이너 제한).

**프런트엔드 (PR 3)**
- `tsc -b`, `npm run lint` 통과(프런트에는 테스트 러너가 없다).
- 로컬 화면에서 닉네임 저장·중복 오류·사진 올리기·삭제·새로고침 후 유지를 눈으로 확인한다. 우상단, 계정 메뉴, 지도 상단 바, 그룹 상단 바에 사진이 바뀐다.

**배포 후 수동 확인**
- PR 1 배포 뒤: 운영 DB에 `user_profile` 테이블과 인덱스가 생겼다(배포 로그의 Flyway 적용 기록).
- PR 2 배포 뒤: 기존 로그인 상태에서 세션 응답이 깨지지 않는다.
- PR 3 배포 뒤: 운영 화면에서 닉네임·사진 등록이 되고, 새로고침해도 유지된다.

---

## 8. 안 할 것

- 다른 사용자에게 사진·닉네임을 보여주는 화면·API
- 닉네임 욕설·금칙어 필터, 닉네임 변경 횟수 제한
- GIF·WebP·HEIC 지원, 사진 자르기 편집 화면(확대·이동)
- 외부 저장소·파일 볼륨·CDN
- `users` 테이블 변경, 기존 컬럼 수정
- 로그인·가입 시 프로필 행 자동 생성
- 지도 상단 바에 닉네임 표시("내 히트맵" 기능이 생길 때 같이 만든다)
- nginx 설정(`infra/nginx.conf`) 변경

---

## 9. 회수 (작업이 끝난 뒤)

- `docs/decisions.md`: 결정 1(DB 저장), 4(브라우저에서 줄여 올림 — nginx 기본 1MB 때문)와 접은 선택지(파일 볼륨, 외부 저장소)
- `docs/backlog.md`: 닉네임 욕설·금칙어 필터, 다른 사용자에게 공개하는 프로필, "내 히트맵"을 볼 때 지도 상단 바 가운데 이름 자리를 닉네임으로 바꾸기(배경: 사용자 결정 — 홈페이지가 제공하는 거래소·MARKETRY는 히트맵 이름, 사용자가 만든 히트맵은 닉네임만 표시)
- 이 지시서는 작업이 끝나면 삭제한다.
