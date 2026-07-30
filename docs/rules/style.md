# 코드 스타일 가이드

리팩터링 history(`docs/refactor/history/`)를 종합한 최종본. 상반되는 규칙이 있으면 이 문서가 기준이다.

---

## 1. 패키지 구조

### 최상위

```
dev.eolmae.marketmonitor/
 ├─ MarketMonitorApplication   메인 클래스만
 ├─ config/    앱 전역 Spring 배선(@Configuration 빈)
 ├─ handler/   앱 전역 예외 처리(@RestControllerAdvice)
 ├─ runner/    앱 라이프사이클 훅(ApplicationRunner/CommandLineRunner)
 ├─ common/    비즈니스가 import하는 공유 라이브러리 — cache · enums · event · exception · util
 └─ domain/    기능 도메인 — view · stock · notification · krx · renderer
```

### 도메인 하위 표준 템플릿

```
domain/<feature>/
 ├─ entity/      JPA 엔티티
 ├─ repository/  Spring Data 리포지토리
 ├─ service/     @Service 비즈니스 로직 + 캐시 서비스
 ├─ controller/  REST 컨트롤러         ┐ 인바운드 진입점: 도메인 하위 동격,
 ├─ scheduler/   @Scheduled cron 트리거 ┘ collector 밑에 중첩 금지
 ├─ dto/         요청/응답 타입(KiwoomRequest/KiwoomResponse 베이스 포함)
 ├─ enums/       도메인 enum
 ├─ client/      외부 API 호출자만
 ├─ collector/   배치 수집기(실제 일)
 ├─ config/      도메인 종속 @Configuration
 ├─ properties/  도메인 @ConfigurationProperties
 └─ exception/   도메인 특화 예외
```

### 규칙

- **외부 클라이언트는 소비 도메인에 귀속** — `KiwoomApiClient` → `domain.stock.client`, `TelegramClient` → `domain.notification.client`. `client`엔 HTTP 호출자만, 요청/응답 타입은 `dto`.
- **common → domain 역의존 금지** — 역의존이 생기면 이벤트 디커플링 or 기능 삭제. 구조를 비틀면서까지 stopgap 기능을 유지하지 않는다.
- **진입점(controller·scheduler)은 도메인 하위 동격** — collector 밑 중첩 금지. 앱 전역 runner는 top-level `runner`.
- **전역 vs 도메인 config 분리** — 앱 전역 빈(RestClient·CacheManager)은 top-level `config`, 도메인 종속(키움 RateLimiter)은 `domain.<feature>.config`.
- **패키지명 단수** — `entity`·`dto`·`repository`·`service`… 단, `enums`만 예약어(`enum`) 회피로 복수.
- **`common`은 공유 라이브러리만** — enums·event·exception·util·cache. 프레임워크 인프라(config·handler·runner)는 top-level.
- **루트 직속 0** — `domain.stock` 루트에 엔티티/서비스가 흩어지지 않도록 전부 서브패키지로.

---

## 2. 네이밍

- **API/TR 번호 기반 이름 금지** — `Ka20001Request` ❌ → `SectorCurrentPriceRequest`. 의미 기반으로.
- **약어 지양, 풀네임 사용** — `prevTotalMarketCap` → `prevMarketCapitalization`, `master` → `stockInfo`.
- **매직 리터럴 → 명명 상수** — 역할이 드러나는 이름으로. `"_"` → `SUFFIX_SEPARATOR`, `"-"` → `NO_DATA_MARKER`.
- **메서드명은 동사 시작, 역할 명확하게** — `stripSuffix` → `removeSuffix`, `message()` → `createMessage()`.
- **클래스/타입명은 책임 중심으로 정밀하게** — `AlertService` → `EscalationService`.
- **패키지 이동 시 이름도 같이 정리** — `dashboard` → `view` 이동과 동시에 `DashboardController` → `MarketController`.
- **저장용 enum ≠ API 파라미터/쿼리용 enum** — DB에 저장되는 enum(`IntradayInvestorType`)에 "API 파라미터 전용" 값(`FOREIGN_TOTAL`)을 끼우지 않는다. 합산/확장 조회 의미는 Query enum(`MarketQuery`, `IntradayInvestorQuery`)으로 분리.
- **"ALL/통합"은 enum 상수가 아니라 부재로 표현** — `Market.ALL` ❌. 부재 or Query enum의 `COMBINED`로.
- **공용 enum은 정체성만** — KOSPI/KOSDAQ. TR별 요청 코드는 수집기 내 `private enum`으로 국소화.
- **확장성 있는 enum은 per-constant 필드 유지** — `Zone.KST`가 현재 하나여도 `id` 필드 유지. 평탄화로 상수가 무의미해지는 변경은 거부.
- **`valueOf`와 혼동되는 이름 지양** — enum static factory는 `codeOf()`, `from()` 등 사용.

---

## 3. 제어 흐름 / 가독성

- **`!조건` prefix 가시성 문제** — `!`은 눈에 잘 안 띈다. `if (!exists) { 본문 } else { skip }` 대신 `if (exists) { skip; return; } 본문`으로 순서 반전. `!=`는 별개 토큰이라 해당 없음.
- **단순 표현식은 인라인** — 한 줄짜리 자기 설명적 표현식은 메서드로 추출하지 않는다. 2회 중복까지 허용.
  ```java
  LocalDateTime snapshotTime = LocalDateTime.now(Zone.KST.zoneId()).truncatedTo(ChronoUnit.HOURS);
  ```
- **불필요한 변수 추출 금지** — 단순 변환(`Enum.from(x)`)을 로컬 변수로 분리하지 않는다. 형제 호출들이 인라인이면 동일하게.
- **삼항 연산자** — 연산이 복잡하거나 줄이 길어지면 if-else로 대체.
  ```java
  // 지양
  BigDecimal x = a.signum() != 0 ? b.divide(a).multiply(c) : BigDecimal.ZERO;
  // 선호
  BigDecimal x = BigDecimal.ZERO;
  if (a.signum() != 0) { x = b.divide(a).multiply(c); }
  ```
- **멀티라인 메서드 호출 일관성** — "모든 파라미터 한 줄" 또는 "파라미터마다 한 줄" 중 하나. 같은 블록 내 형제 호출과 반드시 같은 스타일.
  - 눈대중이 아니라 **포매터(120자 컬럼, `palantirJavaFormat`)가 빌드 시 자동으로 정리해주는 결과** 레코드 선언도 동일하게 적용
- **반복 사용 표현식은 로컬 변수로** — 동일 표현식 2회 이상이면 추출.
- **`for` 루프 사용** — `IntStream.range` 사용 금지. 인덱스가 필요하면 `int index = 0; index++` 패턴.
- **시간 절단은 `truncatedTo`** — `.withMinute(0).withSecond(0).withNano(0)` 체인 대신 `.truncatedTo(ChronoUnit.HOURS)`.

---

## 4. 컬렉션 / 스트림

- **이미 가진 객체 그대로 활용, 파생 컬렉션 변수 신설 지양** — `List<HoldingItem>`이 있으면 `Set<String> stockCodes`를 따로 만들지 않고 호출부에서 `.stream().map(...).toList()`로 인라인. `Map`도 `.keySet().contains()` 대신 `.containsKey()` 직접 사용.
- **`Function.identity()`** — `s -> s` 대신.
- **단일 IN 쿼리 + 자바 합산 우선** — market/investor별 개별 쿼리를 반복 호출하지 않는다. `List<...>`를 받는 단일 IN 쿼리 + 자바 스트림에서 합산·정렬.
- **N+1 제거** — `findAll()` 단일 조회 + `Map<code, Entity>` 구성 + `remove()`로 매칭. 루프 내 개별 조회 금지.
- **Map 사용은 필요한 경우만** — O(1) 조회가 실제로 필요할 때. 단순 단일 엔티티 조회는 평범한 객체/switch.
- **컬렉션 반환은 null 대신 빈 컬렉션** — `return null` → `return List.of()`.
- **`List.of()` guard** — `if (list == null || list.isEmpty()) { return; }` 후 사용.
- **제네릭 응답 래퍼는 `empty()` 정적 팩토리 제공** — `SnapshotResponse<T>`처럼 "데이터 없음"을 표현하는 값이 반복적으로 필요한 제네릭 래퍼 레코드는, 호출부마다 `new Wrapper<>(null, List.of())`를 직접 조립하지 않고 `public static <T> Wrapper<T> empty()` 정적 팩토리를 두고 재사용한다.

---

## 5. null / 빈 값 처리

- **발생하지 않는 케이스에 방어 코드 추가 금지** — 잘못된 암시를 줄 수 있다. "조용히 틀리느니 시끄럽게 터지는 쪽."
- **외부 API 응답 null** — `Optional.ofNullable(...).orElseThrow(() -> new BusinessException(ErrorCode.XXX, ...))`.
- **단순 null 체크는 `== null`** — `ObjectUtils.isEmpty()` 남용 금지. null 가능성만 있는 경우는 `== null`로 명시적으로.
- **빈 컬렉션/응답 → skip** — 예외가 아닌 정상 케이스.
- **연산 불가 상태** — `EscalateException`으로 에스컬레이션(예: 시가총액 0).

---

## 6. 예외 모델

- **서브타입 3개만** — `BusinessException` / `EscalateException`(개발자 알림 카테고리) / `KiwoomRateLimitException`(@Retryable 전용).
- **ErrorCode는 throw 지점에서 명시** — `new BusinessException(ErrorCode.KIWOOM_HTTP_ERROR, e, apiId)`.
- **오버로드 2개** — `(ErrorCode, String... args)` + `(ErrorCode, Throwable, String... args)`. `null` 명시 전달 금지.
- **메시지 조립은 예외 자신이** — `BusinessException.createMessage()` = `[CODE] 메시지 | context : a|b`.
- **ErrorCode 메시지는 문장형** — `"~실패."` ❌ → `"~에 실패했습니다."`.
- **내부 코드에서 `IllegalStateException` 직접 사용 금지** — `EscalateException` + `ErrorCode` 사용.

---

## 7. 엔티티 / JPA

- **JPA 더티 체킹 활용** — 루프 내 `save()` 대신 트랜잭션 내 update 메서드 호출로 DB 왕복 최소화.
- **`@Transactional`/`@Cacheable`은 구현체에만** — 인터페이스는 순수 계약. Spring 프록시는 인터페이스 애노테이션을 상속하지 않음.
- **`existsBy` 패턴 사용** — `findBy(...).isEmpty()` 금지.
- **중복 체크 후 저장** — `if (exists) return;` early return 패턴.
- **bulk delete** — 루프 내 개별 `delete()` 대신 단일 `@Modifying @Query`로.
- **최신 스냅샷 조회** — `findLatestSnapshotTime()` + 후속 조회 2-step 대신 `WHERE s.snapshotTime = (SELECT MAX(...))` 서브쿼리 단일 쿼리.

---

## 8. 메서드 구조

- **오케스트레이션은 명명된 step 메서드로 추출** — `run()`을 `loadStockInfoCache()` / `syncHoldings()` / `getWatchStockCache()` 단계로. 메서드명 동사 시작.
- **반복 try-catch → 작업별 private 메서드** — 오케스트레이션 메서드는 "무엇을 하는지"만 표현.
- **예외 격리 헬퍼 네이밍** — `runSafely(name, task)`. "안전 실행" 의미가 드러나야 함. `runTask`·`collect`처럼 의미가 좁아지는 이름 지양.
- **불필요한 중간 오버로드 제거** — 삭제 전 grep으로 호출부 확인. 바로 아래 단계 외 다른 호출자 없으면 병합.
- **게이트웨이 오버로드 vs 비즈니스 로직 오버로드 분리**
  - 게이트웨이: Query enum → 구체 타입 변환만 수행 후 위임
  - 비즈니스 로직: `List<Market>` 등 구체 타입만 받아 실제 조회 + 응답 생성
  - 설명 주석은 비즈니스 로직 오버로드 위에만
- **`toEntity()` 패턴** — 루프 내 `repository.save(toEntity(...))`. 단일 엔티티는 `toEntity()`, 복수 엔티티는 `toXxxEntity()` 이름 분리.
- **enum의 switch 캡슐화** — 시장별 코드 매핑은 호출부가 아닌 enum의 static factory(`codeOf()`)에.
- **1회성 라벨 enum화 금지** — 비교/분기 없이 로그 출력에만 쓰이는 라벨은 리터럴 유지.
- **파라미터 순서 일관성** — 컨트롤러 `@RequestParam` 순서는 서비스 메서드 파라미터 순서(중요도 기준)와 정확히 일치.
- **기존 패턴 재사용** — 한 메서드에서 확립된 구조 패턴은 유사 메서드에도 동일하게 적용.

---

## 9. 컨트롤러 / API

- **GET** — `@RequestParam`/`@PathVariable` 개별 파라미터.
- **POST/PUT** — 파라미터 개수와 무관하게 Request 객체(record)로 받음.
- **요청 모양 공유 + 소수 필드로 갈리는 변형 동작은 컨트롤러에서 분기** — 요청 DTO 모양이 거의 같고 핵심 로직도 대부분 공유하는데 필드 하나(주로 nullable)로 동작이 갈리는 경우(예: 카테고리 생성 시 `parentId` 유무로 대분류/하위분류 분기), 엔드포인트는 하나로 유지하고 컨트롤러가 그 필드를 보고 서로 다른 서비스 메서드(`createRoot`/`createChild`)를 호출한다. 서비스 메서드는 각자의 케이스만 처리하고 내부에 분기를 두지 않는다. 반면 요청 파라미터 구성 자체가 다르거나 서로 다른 인증/도메인 경로를 타는 경우는 엔드포인트 자체를 분리한다(`AllowedIpController.register` vs `AdminTokenController.registerIp`).
- **미사용 코드 처리** — 즉시 삭제 금지. 주석 처리 + `// TODO 화면 점검 후 이상 없으면 삭제`.
- **`@ConditionalOnProperty`는 실제 호출 경로 기준으로만** — 상위에서 이미 가드되면 하위 중복 금지. 도메인별로 프로퍼티 분리(예: `krx.enabled`).

---

## 10. 캐시

- **`CacheService<T>` 인터페이스 통일** — `getCache()` / `evict()`. 구현이 T를 박음. `I` 접두사 없음.
- **애노테이션 value는 `static final String`** — `@Cacheable` value는 컴파일타임 상수만. `CacheKey` 상수 클래스로 분리.

---

## 11. BigDecimal

- **동등 비교** — `signum() == 0` 사용. `BigDecimal.equals()`는 scale까지 비교하므로 동등 비교에 사용 금지.

---

## 12. 로그 / 코멘트

### 로그

- **구조화된 포맷** — `[메시지] | context : a|b`. 대괄호 + 라벨로 grep 용이하게.
- **유효하지 않은 데이터 skip 시 `log.warn`** + 컨텍스트 값 명시.
- **API 호출 라인** — TR 코드 주석: `// ka20001`.
- **고정값 의도** — `// 금액 기준 고정`, `// KRX+NXT 합산`.

### 코멘트

- **WHY가 비자명한 경우에만 작성** — 코드가 명확히 설명하는 내용은 생략.
- **순회 구간 시작 전** — 해당 루프의 행위 설명 코멘트.
- **조건의 기능이 아닌 의도 설명** — "API 응답 품질 문제로 필수 필드 없는 종목 제외", "ELW/ETF 등 주권 외 종목 제외".
- **미결 설계 이슈** — `// TODO` + 구체적 설계 선택지까지.
- **deferred 개선** — 알고 있는 후속 정리를 코드에 `// TODO`로 즉시 표식.

---

## 13. 라이브러리

- **Apache Commons Lang3 미사용** — 내부 유틸(`common.util.Strings`)로 대체.
  - `StringUtils.isBlank(v)` → `v == null || v.isBlank()`
  - `ObjectUtils.isEmpty(list)` → `list == null || list.isEmpty()`
  - `StringUtils.trimToEmpty(s)` → `Strings.trimToEmpty(s)` (내부 유틸)
- **static 유틸 클래스 로그** — `LoggerFactory.getLogger(ClassName.class)`로 직접 선언.
- **상수 공유 금지** — 동일 상수(`EMPTY` 등)가 여러 클래스에 있어도 각 클래스가 자신의 `private static final` 선언. 유틸 클래스의 private 상수를 public으로 노출해 공유하지 않음.

---

## 14. Lombok

- **`@Getter` 우선** — 수기 getter 지양. 예외 클래스에도 적용.
- **`@RequiredArgsConstructor` 선호** — 명시적 생성자는 추가 배선이 필요한 경우에만.
- **애노테이션 스택은 길이 오름차순** — 짧은 게 위.

---

## 15. 내부 전용 타입

- **private nested record** — 수집기 내 중간 표현(API 응답 파싱 결과 등)은 `dto` 패키지(외부 경계 전용)가 아닌 private nested record로 정의.
  ```java
  private record FetchStockInfo(
          String stockCode, String stockName, Market market, ...) {}
  ```
