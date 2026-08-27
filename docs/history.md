# 프로젝트 히스토리

대화 중 반복 설명하기 번거로운 배경·맥락·의사결정을 기록하는 문서.
새로운 중요한 맥락이 쌓이면 이 문서에 계속 추가한다.

## 마켓맵 등락률 범례 커스텀 기능 (2026-08-27)

마켓맵 종목 박스 색상(등락률 임계값별)을 사용자가 직접 구성할 수 있게 만드는 기능. 상세 설계(UX 플로우, 프론트 라이브 프리뷰, 보간 로직 등)는 market-monitor-frontend의 `docs/history.md` 같은 날짜 섹션 참고 — 프론트 위주 논의였고 엔티티 설계도 거기서 같이 정했음.

**백엔드 구현 완료.** 최초 설계 초안엔 `side`(+/-) 필드가 있었으나, 최종적으로는 별도 필드 없이 `thresholdPercent`의 부호로만 side를 표현하는 쪽으로 정리됨(음수=하락/0=기준/양수=상승) — "side와 부호가 서로 다른 값을 가리키는" 상태 자체를 구조적으로 불가능하게 만들기 위함. `displayOrder`도 없음(`thresholdPercent`가 곧 정렬 기준).

- **명명**: "Stop"이 아니라 "Threshold"로 통일(`MarketMapScaleThreshold` 엔티티, `market_map_scale_threshold` 테이블, `ScaleThresholdItem`/`ScaleThresholdRequest` DTO, `MarketMapScaleThresholdRepository`) — 초안 단계에서 "stop"(그라데이션 색상 정지점)이라는 이름을 썼었는데, 이 도메인에서 실제로 의미 있는 건 등락률 기준값이라 "threshold"가 더 정확함.
- **테이블**: `market_map_scale_threshold`(`V2__add_market_map_scale_tables.sql`) — `threshold_percent NUMERIC(5,2)`(−30~30, `ScaleThresholdRequest`의 `@DecimalMin/@DecimalMax`로 검증), `color VARCHAR(20) NOT NULL`, `color_label VARCHAR(50)`(nullable — 프론트에서 톤을 아직 안 고른 "미지정" 행도 그대로 표현 가능해야 해서 필수값 아님), `created_at`/`updated_at`. KOSPI/KOSDAQ 구분 없이 앱 전체 단일 설정.
- **API — 개별 CRUD로 설계(전체 교체 방식에서 전환)**: `GET /api/market-map/scale`(공개, `MarketMapController`에 추가, 응답은 `{ thresholds: [...] }`) / `POST /api/admin/market-map/scale`(단건 생성) / `PUT /api/admin/market-map/scale/{id}`(단건 수정) / `DELETE /api/admin/market-map/scale/{id}`(단건 삭제). 전부 `MarketMapScaleController`, 다른 `/api/admin/market-map/**` 컨트롤러들과 동일하게 경로 기반으로만 admin 게이팅(별도 `@PreAuthorize` 없음, 기존 컨벤션과 동일).
  - **처음엔 "PUT으로 전체 배열을 통째로 교체"(delete-all-then-recreate) 방식으로 만들었다가 되짚어서 폐기함** — 삭제 하나 하려고 안 건드린 row까지 매번 다 지웠다 다시 만드는 게 낭비였고, 결정적으로 id를 프론트에 내려주는 순간 그 id가 다음 PUT마다 전부 새로 발급돼버려서 "id로 개별 삭제"가 애초에 불안정해짐. 그래서 `id` 있는 row는 그 row만 update, 없는(새로 만든) row만 insert하는 개별 CRUD로 전환.
  - `MarketMapScaleService`: `getScale()`(id 포함해서 반환) / `createThreshold(request)` / `updateThreshold(id, request)` / `deleteThreshold(id)`(둘 다 없는 id면 `ErrorCode.SCALE_THRESHOLD_NOT_FOUND`로 `NotFoundException`). 엔티티에 `update(thresholdPercent, color, colorLabel)` 메서드 추가(다른 엔티티들의 `rename`/`tagVersion` 같은 의미 있는 mutator 패턴과 동일).
  - **`threshold_percent`에 `UNIQUE` 제약 추가**(`uk_market_map_scale_threshold_percent`) — 세션 안에서 임계값이 겹치는 새 row들끼리는 여전히 프론트가 적용 직전에 마지막 값 우선으로 정리(중복 생성 방지)하지만, 세션과 무관한 기존 row와 우연히 겹치는 경우는 이제 DB/서비스 레벨에서 막는다. `createThreshold`/`updateThreshold`(자기 자신은 제외 — `existsByThresholdPercentAndIdNot`) 둘 다 저장 전에 `existsByThresholdPercent` 검사 후 겹치면 `ConflictException(SCALE_THRESHOLD_DUPLICATE)`(409). 굳이 sign enum을 되살릴 필요는 없었음 — `threshold_percent` 하나가 이미 부호+크기를 전부 담는 유일한 값이라 그 컬럼 하나에 UNIQUE만 걸면 충분.
  - **`color`/`colorLabel` 검증도 함께 정리**: `color`는 자유 텍스트가 아니라 hex라서 `@Size(20자 이하)` 대신 `@Pattern("^#[0-9a-fA-F]{6}$")`로, `colorLabel`은 프론트 톤 프리셋 버튼(빨강/주황/노랑/초록/파랑/네이비/보라/회색)과 1:1 대응하는 `ColorLabel` enum(RED/ORANGE/YELLOW/GREEN/BLUE/NAVY/PURPLE/GRAY)으로 바꿔서, 임의 문자열이 들어가 프리셋 하이라이트 로직이 조용히 깨지는 걸 구조적으로 막음(컬럼명이 `color_label`이라 enum 타입명도 `ColorLabel`로 맞춤 — `ColorTone`으로 지었다가 컬럼명과 안 맞아서 수정). 다만 `color`(hex) 자체는 없애지 않았음 — 명도(lightness) 슬라이더로 같은 톤 안에서도 값이 계속 달라져서 `colorLabel`(어느 버튼을 눌렀나) 하나만으로는 최종 렌더 색을 못 구하기 때문.
- 프론트 `MarketMapScaleThreshold`(zod) 스키마와 필드명 1:1 일치(`id`/`thresholdPercent`/`color`/`colorLabel`), `./gradlew compileJava`/`compileTestJava` 클린 확인.

## 예외 에스컬레이션(EscalateException/텔레그램 노티) 기준

`EscalateException`(HTTP 500 + 텔레그램을 통한 개발자 즉시 알림) 사용 여부는 "`IllegalStateException`의 대체 여부"가 아니라 다음 기준으로 판단한다.

- 노티가 필요한 핵심 케이스는 다음 하나로 수렴한다: 완전한 실시간은 아니더라도, 해당 시간대에 맞지 않는 잘못된 데이터가 사용자에게 전달되어 잘못된 판단을 유도할 위험이 있는 경우. 수집기(collector) 실패가 대표적인 예로, 수집이 실패하면 과거 스냅샷이 최신 데이터인 것처럼 노출될 수 있어 즉시 인지가 필요하다.
- 그 외의 경우(관리자 화면 조작 중 발생하는 오류, 특정 기능이 그 자리에서 동작하지 않는 경우 등)는 에스컬레이션 대상이 아니다. 사용자가 오류 발생 시점에 이를 인지하고 수정하면 되는 수준이며, 잘못된 데이터로 잘못된 판단을 유도할 위험이 없기 때문이다.
- `style.md` 6장의 "내부 코드에서 `IllegalStateException` 직접 사용 금지 → `EscalateException` 사용"은 모든 `IllegalStateException` 대체 상황에 일괄적으로 `EscalateException`을 적용하라는 의미가 아니라, 위 기준에 해당하는 경우에 한해 `ErrorCode`를 가진 `EscalateException`을 사용하라는 취지로 해석한다. 해당하지 않는 경우는 일반 `BusinessException`으로 처리한다.
- 어떤 케이스가 노티 대상인지는 코드 패턴이 아닌 판단의 영역이므로 `style.md`에 규칙으로 명문화하지 않고, 맥락으로서 이 문서에만 기록한다.
- 참고로 현재 `EscalateException` 발생 시 텔레그램을 통해 개발자(`DEVELOPER_CHAT_ID`)에게 직접 알림을 전송하는 구조이나, 추후 슬랙 등 팀 채널 형태로 이관할 계획이 있어 현재는 이 기준을 엄격하게 적용하지 않고 진행하고 있다.

---

## Flyway V1 마이그레이션 파일을 계속 고쳐쓰는 이유 / 향후 전환 계획

- 지금은 새 버전 파일을 추가하지 않고 `V1__create_schema.sql` 자체를 계속 고쳐쓰는 방식을 쓰고 있다. 이는 "관행"이라서가 아니라, 스키마에 손대는 자잘한 수정이 워낙 잦아서 매번 새 버전으로 쪼갰다면 지금쯤 스크립트 파일이 상당히 많이 쌓였을 거라는 실용적인 이유 때문이다.
- `release/**` 브랜치를 통한 자동 배포 파이프라인이 이미 마련되어 있고, 어느 정도 기능이 가닥을 잡으면 릴리즈 브랜치 운용을 시작할 예정이다. **그 시점부터는 V1을 계속 엎어쓰지 않고**, 개발 중 생기는 자잘한 수정(예: 어제 `isLocked`가 필요하다고 판단해 추가했다가 오늘 다시 보니 불필요해서 제거하는 식의 변경)들을 매번 새 버전으로 쪼개는 대신 **릴리즈 브랜치를 실제로 배포하는 시점마다 그때까지의 변경을 묶어 버전을 확정**하는 방식으로 갈 계획이다 (예: 두 번째 릴리즈 브랜치 배포 시 V2 확정). 즉 릴리즈 브랜치 개수와 Flyway 버전 스크립트 개수를 대략 일치시키는 방향.

---

## 백엔드 배포 롤백 플로우

프론트는 "브랜치 push → 배포해서 확인 → 문제 있으면 main으로 재배포해서 롤백"이 가능한데, 백엔드는 DB(Flyway 마이그레이션)가 껴있어서 그대로 가져올 수 없었다. 논의 끝에 프론트와 동일한 모델로 통일하기로 하고, `release.yml`/`infra/scripts/*`에 구현 완료.

### 플로우

1. 코드 작성 → 특정 브랜치로 push → **자동으로 빌드만 진행**(배포는 안 함). `changes` job의 경로 필터(`Dockerfile`, `src/main/**`, `build.gradle` 등)에 걸리는 변경일 때만 빌드되고, 아니면 스킵. 이미지는 `:sha-<커밋SHA>`(고정 참조용)와 `:branch-<브랜치명>`(편의용, `/`는 `-`로 치환) 두 태그로 GHCR에 푸시됨. 이 단계는 GitHub Actions 러너에서만 일어나고 서버는 전혀 안 건드림(서버 무영향).
2. 그 브랜치를 `workflow_dispatch`(`deploy_ref` 입력)로 지정해서 배포(시험 배포) — main이 아니어도 프로덕션에 바로 띄워서 확인. 빌드는 이미 1번에서 끝났으므로 이 단계는 이미지 pull + 컨테이너 재기동뿐.
3. 문제 발견 시 → `deploy_ref=main`으로 재배포해서 롤백. 시험 배포 동안 main은 건드리지 않았으므로 "되돌린다"기보다 그냥 원래 있던 안전한 상태를 다시 트는 것에 가깝다. 이 배포도 빌드 없이 이미 존재하는 `:main` 이미지를 pull하는 것뿐이라 빠름.
4. 문제없으면 → main으로 PR 머지. main push(브랜치 보호 규칙으로 항상 머지 커밋)를 감지해 `promote-main` job이 **재빌드 없이** 그 머지 커밋의 두 번째 부모(`HEAD^2`, 즉 머지된 브랜치의 tip)에 해당하는 `:sha-<그SHA>` 이미지를 찾아 `docker buildx imagetools create`로 레지스트리 안에서만 `:main`으로 재태깅(레이어를 러너로 내려받았다 다시 올리는 게 아니라 레지스트리 API 레벨 처리)하고 배포까지 자동 진행. `:sha-<HEAD^2>` 이미지가 없으면(그 PR이 application과 무관한 변경이었으면) 조용히 스킵.

**main 브랜치 보호 설정 필요**(GitHub Rulesets): "Require a pull request before merging"(직접 push 금지) + 저장소 Pull Requests 설정에서 "Allow merge commits"만 켜고 Squash/Rebase merge는 끔. 이 두 조건이 있어야 main에 올라오는 커밋이 항상 머지 커밋이라는 게 보장되고, `HEAD^2` 기반 승격 로직이 예외 처리 없이 항상 성립한다.

- 배포 스크립트(`deploy-application.sh`)는 하나로 통합, "어떤 이미지 태그를 pull할지"를 `IMAGE_TAG` 환경변수로 받는다. 기본값(`latest` 등)은 두지 않고 `: "${IMAGE_TAG:?...}"`로 미지정 시 즉시 실패 — 모든 호출 경로(Actions)가 항상 명시적으로 태그를 넘기게 설계했으므로, 값이 없다는 건 호출부 버그라 조용히 넘어가지 않고 바로 드러내는 게 맞다고 판단. `docker-compose.yml`의 이미지 태그도 `${IMAGE_TAG}`로 변수화.
- 서버에서 매 배포마다 하는 `git fetch && git reset --hard origin/main`은 **앱 코드를 가져오는 게 아니다** — 앱 코드는 Docker 이미지 안의 jar가 100% 결정하고, 이 git 체크아웃은 오직 배포 스크립트/`docker-compose.yml` 파일 자체를 최신으로 유지하기 위한 것. 그래서 이 fetch/reset 단계는 일반 배포든 롤백이든 항상 동일하게 origin/main 최신을 가리키면 되고, 바뀌는 건 오직 `IMAGE_TAG` 값뿐이다.
- 기존 `application` job(수동 전체 재빌드+배포, `target=application`/`all`)은 push 트리거 없이 긴급/복구용으로 남겨둠 — 브랜치/main push 흐름과 별개.
- **이미지 정리**: 서버 로컬은 배포 스크립트가 매번 "지금 실행 중인 것 + 생성 시각 기준 최근 2개"만 남기고 나머지 삭제(서버 디스크가 넉넉하지 않아서 타이트하게). GHCR 쪽은 `promote-main` 성공 후 `actions/delete-package-versions`로 최근 5개 버전만 유지(레지스트리 저장 공간은 여유 있어서 로컬보다 넉넉하게). 지금까지 실제 작업 패턴이 "한 브랜치 작업 → 트라이얼 → main 머지, 그 다음에야 새 브랜치 시작"인 순차적 흐름이라 "트라이얼 중인 이미지가 오래됐다는 이유로 삭제되는" 위험은 없다고 판단하고 결정한 수치.
- **헬스체크**: Spring Boot Actuator(`spring-boot-starter-actuator`) 도입, `/actuator/health`를 배포 직후 폴링(`infra/scripts/health-check.sh`). 컨테이너가 `restart: always`라 크래시해도 즉시 재시작되어 "Exited" 상태를 붙잡기 어려우므로, `docker inspect`의 `RestartCount`가 3 이상이면 크래시 루프로 간주해 타임아웃(10분) 전에 조기 실패 처리. 실패 시 `docker logs`(태그 새로 배포할 때마다 컨테이너가 재생성되므로 이번 배포의 첫 줄부터) 출력 — SSH 스크립트의 stdout이 Actions 로그에 그대로 찍히므로, 실패 원인 확인을 위해 서버에 직접 SSH로 들어가 로그를 볼 필요가 없어짐. Actions 화면에서 배포 step과 분리해서 보이도록 별도 `appleboy/ssh-action` step(= 별도 SSH 연결)으로 구성.
- 헬스체크가 `curl localhost:8081`로 서버 로컬에서 직접 확인해야 해서, `docker-compose.yml`의 `market-monitor` 서비스를 `expose`(컨테이너 간 통신만 허용)에서 `ports: "127.0.0.1:8081:8081"`(호스트 루프백에만 바인딩, 외부 노출 없음)로 변경. nginx는 도커 네트워크 안에서 서비스명(`market-monitor:8081`)으로 접근하므로 이 변경과 무관.
- **nginx도 `restart: always`라 같은 크래시 루프 위험이 있어서**, `infra/scripts/nginx-health-check.sh`로 RestartCount(3 이상) 감지만 별도 추가(`release.yml`의 `nginx` job에도 분리된 Health check step, 타임아웃 30초 — nginx는 워밍업 없이 즉시 뜨거나 즉시 실패하는 이진적 실패 모드라 짧아도 충분). 다만 config 문법 오류면 즉시 기동 실패하는 구조라 스프링만큼 다양한 "일부만 고장" 상태가 없고, HTTPS(도메인+인증서) 응답 확인까지 하는 건 SNI/인증서 처리 번거로움 대비 실익이 적다고 판단해 RestartCount 체크만 넣고 HTTP 응답 내용 확인은 생략함. nginx는 상태(DB 마이그레이션 등)가 없어 롤백이 항상 깨끗하고 변경 빈도도 낮아, 브랜치 트라이얼/SHA 사전 태깅 같은 무거운 구조는 필요 없다고 판단해 도입 안 함 — main push에만 반응하는 기존 방식 유지.

### Flyway 마이그레이션 규칙 (필수, 예외 없음)

이 플로우에서 가장 위험한 지점은 마이그레이션이다. Flyway는 기본 설정(`spring.flyway.ignore-missing-migrations`가 기본값 `false`)상, DB의 `flyway_schema_history`에 "적용됨"으로 기록된 마이그레이션 파일이 지금 앱의 classpath(`src/main/resources/flyway/`)에 없으면 **시작 자체를 거부**한다.

그래서 브랜치에서 새 마이그레이션까지 같이 시험 배포했다가 문제를 발견해 main으로 롤백하면, main의 jar엔 그 마이그레이션 파일이 없으니 앱이 아예 못 뜬다 — "롤백하려다 전체 장애"가 되는 최악의 시나리오. 이를 막기 위한 규칙:

1. **마이그레이션이 포함된 작업은 항상 2단계로 나눠 진행한다.** ① 마이그레이션 스크립트만 먼저 main에 머지하고 실제로 배포해서 DB에 적용해둔다. ② 그 스키마를 사용하는 기능 코드는 그 다음에 별도 브랜치에서 개발/시험배포한다. 이러면 브랜치를 아무리 굴리다 main으로 롤백해도, main은 항상 그 마이그레이션을 이미 알고 있는 상태라 위 실패가 발생하지 않는다.
2. **마이그레이션은 항상 하위호환(추가만)되게 작성한다.** nullable 컬럼 추가, 새 테이블 추가는 안전. 기존 컬럼 삭제, 타입 변경, 기존 컬럼에 기본값 없이 NOT NULL 강제하는 것은 금지 — 이런 변경이 섞이면 어떤 롤백 방식을 쓰든(브랜치든 SHA든) 예전 코드가 깨진다.
   - 근거: JPA/Hibernate는 엔티티가 매핑한 컬럼만 명시적으로 `SELECT`하므로(`SELECT *` 안 씀), DB에 엔티티가 모르는 컬럼이 더 있어도 조회는 완전히 무해하다. `ddl-auto=validate`도 "엔티티가 필요로 하는 컬럼이 DB에 있는지"만 검사하지 그 반대는 안 본다. 문제가 생기는 유일한 지점은 쓰기(INSERT)인데, 새 컬럼이 NOT NULL이면서 기본값이 없을 때만 예전 코드의 INSERT가 제약 위반으로 실패한다. 그래서 "nullable/기본값 있게 추가"만 지키면 이 위험이 사라진다.

이 두 규칙(마이그레이션 선-머지, 하위호환 추가만)은 이번 배포 플로우가 성립하기 위한 전제 조건이라 예외를 두지 않는다.

---

## KrxCrawler(KRX 데이터마켓 크롤링) 미채용 배경

- `domain/krx/crawler/KrxCrawler`는 로그인 테스트 정도까지만 진행됐고, 실제 데이터 수집 경로로는 채용되지 않았다. `krx.enabled=true`로 기본 비활성화 상태.
- 미채용 이유는 크롤링 방식 자체에 대한 막연한 거부감 — 예를 들어 KRX 쪽 응답 데이터 구조가 바뀌면 우리 코드는 아무것도 안 건드렸는데도 오류가 발생할 수 있는 것처럼, 크롤링 특유의 취약성 때문.
- 다만 구현에 시간이 꽤 들어간 상태라 폐기하기엔 아깝고, 나중에 API만으로는 기능 구현에 어려움이 있을 때 대안 수집 수단으로 쓰기 위해 기능만 남겨두고 있다.
