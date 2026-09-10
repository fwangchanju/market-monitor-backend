# 백엔드 정비 작업 지시서

> **임시 문서다.** 마지막 단계까지 끝나면 이 파일을 삭제한다.
>
> 시니어 리뷰 결과 + 사용자가 확정한 내용 + 별도 검증 에이전트의 지적을 반영한 문서다.
> **순서대로** 진행한다. 단계를 건너뛰지 않는다.

> **진행 상황**
>
> | 단계 | 상태 |
> |---|---|
> | 1A 배포 워크플로 | 완료 (PR #85) |
> | 1B 안전망 | 완료 (PR #87) |
> | 2 버그 7건 | 완료 (PR #88) |
> | 3 운영 | 완료 (PR #94) |
> | 4 정리 | 완료 (PR #97) |
> | 5 텔레그램 발송 주기 재구성 | 다음 |
> | 6 예외 로그 정리 | |
> | 마지막 문서 마무리 | |
>
> **완료된 단계의 본문은 지웠다.** 다시 할 일이 없는데 읽을 양만 늘리기 때문이다. 필요하면 git
> 히스토리나 위 표의 PR에서 본다. 아래 남은 규칙과 5·6단계만 읽으면 된다.

> **⚠️ 라인 번호는 참고용이다.** 위치는 **메서드명·식별자 문자열**로 찾는다. 라인 번호가 어긋나
> 있어도 그것만으로 "지시서와 코드가 모순"이라고 판단하지 않는다.

---

## 이 작업의 배경

지금까지 이 프로젝트는 **사용자가 코드를 한 줄씩 읽으면서** 진행됐다. 이제 그러지 않는다.

> **사람이 리뷰를 안 하면, 그 자리를 자동 검증이 메워야 한다.**

안전망(CI·테스트)을 버그 수정보다 먼저 만드는 이유가 이것이다. 안전망 없이 코드를 고치면 고쳤는지
망가뜨렸는지 아무도 모른다.

---

## 역할

| 역할 | 현재 담당 | 하는 일 |
|---|---|---|
| **설계·문서·리뷰** | Opus | 문서 작성, PR 리뷰. `docs/**`와 `CLAUDE.md`는 이쪽만 수정한다 |
| **구현** | Sonnet | 이 지시서를 읽고 **코드만** 작성. 문서는 건드리지 않는다 |
| **결정** | 사용자 | PR 병합, 배포 시점 |

**구현 세션은 `docs/` 아래 문서를 수정하지 않는다.** 작업 중 알게 된 것, 지시서와 실제가 달랐던
부분은 전부 **PR 설명에** 적는다. 문서 반영은 리뷰 세션이 한다.

예외: 이 파일(`work-plan.md`)의 마지막 삭제 작업만 마지막 단계에서 수행한다.

---

## 작업 규칙

- **한 PR = 하나의 단계.** 통으로 끝내고 PR을 올린 뒤 **멈춘다**
- 다음 단계는 **직전 PR이 main에 병합된 뒤** 최신 main에서 브랜치를 따서 시작한다
- 단계 중간에 사용자에게 확인을 구하지 않는다
- **PR 병합은 하지 않는다.** 사용자가 한다
- 리뷰 코멘트가 달리면 같은 PR에 반영해서 push한다. 왕복은 1회

### 판단 범위 — 이건 중요하다

**이 지시서에 조치가 적혀 있으면 그대로 따른다. 더 나은 방법이 떠올라도 바꾸지 않는다.**

여기 적힌 조치들은 대안을 검토한 끝에 정해진 것이고, 근거가 `docs/decisions.md`에 있다. 구현 시점에
다시 판단하면 이미 접은 선택지로 되돌아간다.

- **설계 판단**(무엇을 어떤 방식으로) — 이 지시서가 정한다. **재판단하지 않는다**
- **구현 세부**(변수명, 메서드 분리, 테스트 케이스 구성) — 스스로 정한다. PR에 설명 안 해도 된다
- **지시서에 없는 것** — 기존 코드의 같은 패턴을 따르고, PR 설명에 한 줄 적는다
- **지시서대로 하면 안 될 것 같으면** — 임의로 바꾸지 말고 **멈추고 확인한다**

**멈추고 확인해야 하는 경우 (둘뿐):**
- 지시서와 실제 코드가 **구조적으로** 모순돼서 진행이 불가능할 때(라인 번호 불일치는 해당 없음)
- 데이터 삭제·마이그레이션 등 되돌리기 어려운 작업의 범위가 지시서에 적힌 것보다 커질 때

### 완료 기준 (모든 단계 공통)

1. `./gradlew compileJava compileTestJava` 통과
2. `./gradlew test` 통과 (매뉴얼 테스트 제외)
3. `./gradlew spotlessApply` 실행 후 `spotlessCheck` 통과
4. 컴파일 경고가 작업 전보다 **늘지 않았을 것**
5. PR 설명에 변경 내용·이유·검증 방법이 적혀 있을 것

### 커밋

`docs/rules/process.md`의 커밋 규칙을 따른다. 개수를 목표로 잡지 않는다.

- 따로 되돌릴 이유가 있는 것만 따로 커밋한다. A를 되돌릴 때 B도 반드시 같이 되돌려야 한다면 둘은
  한 커밋이다
- **이 지시서의 항목 번호를 커밋 단위로 착각하지 않는다.** 5-1~5-6은 설명의 단위이지 커밋의 단위가
  아니다
- **`spotlessApply`로 인한 포맷 변경은 반드시 별도 커밋으로 분리한다.** 섞이면 실제 작업 diff가
  묻혀서 리뷰가 불가능해진다
- squash merge는 쓸 수 없다(`promote-main`이 `HEAD^2`를 사용). 정리는 **PR 올리기 전 브랜치 안에서**

---

## 절대 건드리지 말 것

**아래는 "죽은 코드"가 아니다. 의도적으로 남긴 것이다. 삭제하거나 "정리"하지 마라.**

| 대상 | 위치 |
|---|---|
| 주석 처리된 메서드 본문 4개 | `MarketQueryService` |
| 주석 처리된 스케줄 메서드 | `CollectionScheduler.collectMarketDataHourly` 등 |
| 주석 처리된 startup 단계 | `StartupRunner.run()` |
| `ImageStitcher` 클래스 | `domain/notification/service/` |
| `okhttp` / `okhttp-urlconnection` 의존성 | `build.gradle` |
| `domain/krx` 전체 | — |
| `CollectionChecker`, `CollectionScheduler.isHoliday()` | 지연 감지 기능용. `docs/backlog.md` 참고 |

**⚠️ 테스트 중 태깅하면 안 되는 것**

`domain/stock/collector/StockInfoCollectorTest`는 **CI에서 반드시 돌아야 하는 단위 테스트**다.
매뉴얼 테스트가 아니다. `@Tag("manual")`을 붙이지 마라. 이름이 비슷해 헷갈리기 쉬운데, 매뉴얼
테스트는 아래 여섯 개뿐이다.

```
collector/KiwoomApiVerificationTest          collector/FullDataCollectionTest
api/KrxLoginTest                             domain/renderer/client/ScreenshotClientManualTest
domain/notification/service/MarketMapTelegramReportSenderManualTest
domain/notification/service/TelegramReportCycleManualTest
```

**`domain/access`(IP 화이트리스트·관리자 토큰)는 이번 정비 대상이 아니다.** 로그인 기능으로 통째로
대체될 예정이라 지금 다듬으면 낭비다(`docs/backlog.md`).

---

# 5단계 — 텔레그램 발송 주기 재구성

> **아직 확정 전이다. 이 상태로 구현을 시작하지 않는다.**
>
> `grill-me` 검증(`docs/rules/process.md` 5번)은 돌렸고, 지적 중 설계 역할이 혼자 고칠 수 있는
> 것은 이 문서에 반영했다. 사실 오류 정정(5-2의 before 서술, 5-3의 지수 등락률·위임 구조),
> 빠진 호출부·테스트 목록, "둘 중 판단해서" 남겨둔 곳의 확정, 판정식과 기동 검증의 구멍이다.
>
> 사용자 판단이 필요했던 네 가지도 전부 확정해서 본문에 반영했다. 백엔드 선배포, 6단계 자동 검증
> 추가, 15분 주기 유지, 5-4 가드를 `sectorAvailable`이 참일 때만 도는 것이다.
>
> **남은 것은 PR #96 병합뿐이다.** 병합되면 이 블록을 지우고 구현을 시작한다.

브랜치명 예: `claude/refactor/telegram-schedule`

**백엔드와 프론트 두 레포를 모두 바꾼다.** PR은 레포별로 하나씩 올리고, **백엔드를 먼저 배포한다.**

백엔드가 먼저 나가도 깨지는 것이 없다. 캡처 URL에 `&beforeMinutes=N`이 붙어도 아직 그 파라미터를
모르는 프론트는 무시하고 지금처럼 30분 기준으로 그린다. 응답에 `depth`와 `categoryName`이 실려도
zod가 모르는 키를 버리므로 화면은 아무것도 느끼지 않는다. 지금 상태가 그대로 유지될 뿐이다.

반대 순서는 깨진다. 5-3은 백엔드가 `depth`와 `categoryName`을 내려줘야만 성립하는데, 배포는 병합과
분리되어 사용자가 임의 시점에 수동 실행하므로(`docs/rules/process.md`) 프론트만 나가 있는 구간이
며칠일 수 있다. 그 구간에 zod 필수 필드면 parse가 실패해 "데이터를 불러오지 못했습니다"가 뜨고,
옵셔널이면 `depth`가 undefined라 대분류 필터가 전부 걸러 "데이터가 없습니다"가 뜬다. 그 화면을
렌더러가 그대로 캡처해 텔레그램으로 보낸다.

## 이 단계가 하는 일

발송 주기를 15분짜리 하나에서 "짧은 주기 + 긴 주기" 둘로 늘린다. 각 주기는 화면에서 그만큼의
"N분 전 대비"를 선택한 상태로 캡처된다. 주기 값과 겹칠 때의 정책은 프로퍼티로 바꿀 수 있어야 한다.

이 과정에서 PR #84, #90, #91이 남긴 것들을 함께 정리한다. 세 PR은 정비 작업 중간에 급하게 들어가
리뷰를 거치지 않았고, 발송이 안 되는 버그 하나와 같은 조회를 두 번 하는 구조가 남아 있다.

## 5-1. 발송 주기를 둘로 나눈다

### 별도 스케줄로 나누지 않는다

발송은 지금처럼 수집 tick(`collectMarketData`) 안에서 게이팅한다. `@Scheduled`를 하나 더 만들면
안 된다. 수집이 스냅샷을 쓰고 발송이 그것을 읽는데, 같은 시각에 뜨는 두 트리거의 실행 순서는
보장되지 않는다. 지금 코드가 한 메서드에 묶어둔 이유가 이것이고 그 판단은 유지한다.

### 지금 판정식은 60분을 넘는 주기를 표현하지 못한다

```java
int offset = minute - sendMinute;
boolean onSchedule = offset >= 0 && offset % sendIntervalMinutes == 0;
```

`minute`은 0~59라 주기가 60을 넘으면 매시 걸리거나 아예 안 걸린다. 기준점을 그날의 한 시각으로
잡고 거기서부터 경과한 분으로 판정해야 한다.

### 새 규칙

```
기준점     그날 startHour:sendMinute (예: 08:10)
정규 발송   shouldCollect 이고, 경과분 >= 0 이고, 경과분 % 주기 == 0
마감 리포트  shouldCollect 가 꺼졌고, 지금이 endHour:sendMinute (예: 20:10). 하루 한 번
그 밖      발송하지 않는다
```

**`경과분 >= 0`을 빠뜨리지 않는다.** cron은 `0 0/5 8-20`이라 08:00과 08:05에도 tick이 뜬다.
기준점보다 이르면 경과분이 음수이고, 자바의 `%`는 음수에 음수를 돌려주므로 대부분은 우연히
걸리지 않는다. 그러나 `sendMinute`이 주기의 배수인 조합(예: sendMinute=30, 주기 15)에서는
`-30 % 15 == 0`이 되어 08:00에 발송된다. 지금 코드의 `offset >= 0`이 하던 역할이 이것이다.

15분 주기는 08:10, 08:25, 08:40, …, 2시간 주기는 08:10, 10:10, 12:10, … 이 된다.

마감 리포트를 시각 하나로 고정하는 이유는 마감 이후엔 `shouldCollect`가 꺼져 `dataTime`이 마감
정각에 묶이기 때문이다. 그 뒤로는 몇 번을 보내도 내용이 같다. 주기 격자에 맡기면 20:25, 20:40,
20:55에 같은 메시지가 반복된다. 마감 리포트의 before는 가장 긴 주기를 쓴다. 그날 마지막 메시지에
15분 델타는 의미가 적다.

### before는 주기에서 파생시킨다

주기가 15분이면 before=15, 2시간이면 before=120이다. 별도 프로퍼티로 두지 않는다. 둘이 어긋나야
할 이유가 생기면 그때 나눈다.

### 프로퍼티

```properties
telegram.send-minute=10
telegram.send-interval-minutes=15,120
telegram.overlap=longest-only
```

- `send-interval-minutes`는 쉼표로 구분된 목록이다. `TelegramProperties`의 필드 타입을
  `int`에서 `List<Integer>`로 바꾸면 별도 변환 없이 바인딩된다
- `overlap`은 `all`과 `longest-only` 두 값을 갖는 enum이다. 두 주기가 같은 tick에 걸렸을 때
  둘 다 보낼지, 주기가 긴 쪽만 보낼지를 정한다. 사용자가 아직 정하지 않았고 운영하면서 판단할
  것이라 코드 수정 없이 뒤집을 수 있어야 한다

`TelegramProperties`는 record라 필드를 바꾸면 생성자 시그니처가 바뀐다. 아래가 함께 깨지므로
같은 커밋에서 고친다.

| 파일 | 위치 |
|---|---|
| `EscalationNotifierTest` | `new TelegramProperties(...)` 2곳 |
| `MarketMapAndSectorTelegramReportSenderTest` | `new TelegramProperties(...)` 1곳 |

### 판정을 객체로 뺀다

인자 여섯 개짜리 `isSendCycle` static 메서드를 없애고, 발송 시각 판정을 담당하는 객체를 만든다.

```
due(지금 시각, shouldCollect) → 이번 tick에 발송할 주기 목록
```

- 겹침 정책은 이 목록을 거르는 마지막 단계다. `longest-only`면 가장 큰 주기 하나만 남긴다
- `CollectionScheduler`의 `startHour` 필드는 이 객체로 옮겨간다. 스케줄러에는 남기지 않는다
- **`endHour`는 양쪽이 다 갖는다.** 스케줄러는 `shouldCollect`와 `dataTime` 계산에 계속 쓰고,
  판정 객체는 마감 리포트 시각 판정에 쓴다. 같은 `${collect.end-hour}`를 각자 주입받는다.
  한쪽이 다른 쪽에서 꺼내 쓰는 구조로 만들지 않는다 — 판정 객체가 스케줄러를 알게 되면 스프링
  없이 단위 테스트한다는 조건이 깨진다
- **위치는 `domain/notification/schedule`이다.** 이 객체가 답하는 질문은 "지금 발송할 때인가"라
  판단의 주인은 notification이다. `collect.*` 두 값을 읽는 것은 수집 격자에 맞추기 위한
  제약일 뿐 소속의 근거가 아니다
- 스프링 없이 단위 테스트할 수 있어야 한다. 08:00 / 08:10 / 08:40 / 10:10 / 20:10 / 20:40이 각각
  어떤 주기에 걸리는지를 겹침 정책 두 값 모두에 대해 검증한다. 08:00은 기준점 이전이라 아무
  주기에도 걸리지 않아야 한다

### 기동 시 검증

아래를 만족하지 않으면 애플리케이션이 뜨지 않게 한다. 조건에 안 맞으면 발송이 조용히 사라지는데,
그것이 지금 고치고 있는 08:40 버그와 같은 종류의 사고다. 로그 경고로는 부족하다.

- 각 주기와 `send-minute`이 `collect.interval-minutes`의 배수일 것
- **`send-minute > 0`일 것.** 마감 리포트 조건은 "`shouldCollect`가 꺼졌고 `endHour:sendMinute`"인데
  `shouldCollect`는 `시각 <= endHour:00`이라 20:00 정각에는 아직 `true`다. `send-minute=0`이면 두
  조건이 동시에 성립할 수 없어 마감 리포트가 영영 안 나간다. 그런데 `0 % 5 == 0`이라 배수 검증만
  으로는 통과한다. 별도 조건으로 막는다

### 08:40 버그는 여기서 사라진다

```java
boolean isBoundaryHour = hour == startHour || hour == endHour;
return !isBoundaryHour || minute == sendMinute;
```

요청받은 것은 20:40 스킵뿐이었는데 `startHour`까지 경계로 묶으면서 08:40도 함께 막혔다.
javadoc의 근거도 사실이 아니다. "startHour는 아직 장이 열리기 전이라 30분 뒤에도 데이터가
그대로다"라고 적혀 있는데, `shouldCollect`는 `시각 <= endHour:00`이라 08:40에도 수집기가
정상으로 돌고 새 스냅샷이 생긴다.

새 규칙에는 경계 시간이라는 개념 자체가 없다. 진짜 규칙은 "직전 발송과 내용이 같으면 보내지
않는다"이고 그건 `shouldCollect`와 마감 리포트 규칙이 이미 표현한다.

`CollectionSchedulerTest`의 `isSendCycle_수집_시작_시각의_추가_사이클은_개장_전이라_발송되지_않는다`
가 지금 버그를 고정하고 있다. 08:40은 발송이 정답이다.

다만 이 단언 하나만 뒤집는 것은 불가능하다. 그 파일의 테스트 7개가 전부 `CollectionScheduler.isSendCycle(...)`
을 직접 부르는데 그 static 메서드가 사라지기 때문이다. **`CollectionSchedulerTest`는 새 판정 객체를
대상으로 다시 쓴다.** 지금 7개가 검증하던 시각들(startHour의 sendMinute, endHour의 sendMinute,
endHour의 추가 사이클, 중간 시각, 간격에 안 맞는 시각, sendMinute보다 이른 시각, 그리고 08:40)은
새 테스트에서도 전부 다뤄야 한다. 08:40만 결과가 뒤집히고 나머지 여섯은 같아야 한다.

### 발송 실패 알림

지금은 발송 시각마다 수집 실패 여부를 보고 알림 또는 리포트 중 하나를 보낸다. 발송 대상이 여럿이
되어도 실패 알림은 그 tick에 한 번만 보낸다. 리포트 두 통이 나갈 자리에 실패 알림 두 통이 나가면
안 된다.

## 5-2. before를 이미지에도 적용한다

### 이미지의 before를 백엔드가 정하지 못한다

렌더러 캡처 URL은 `/category-change-rate?market=KOSPI`뿐이라 before가 넘어가지 않는다. 프론트는
`usePersistedState('categoryChangeRate.beforeMinutes', 30)`인데 렌더러는 요청마다 Chromium을
새로 띄우므로 sessionStorage가 항상 비어 있다. **결과적으로 이미지는 언제나 30분 전 기준으로
그려지고, 백엔드에는 그것을 바꿀 수단이 없다.**

주기별로 before를 달리 주려면 이 통로부터 뚫어야 한다.

**텍스트 쪽은 before를 아예 쓰지 않는다.** `CategoryRankingTextBuilder.buildRankingText`는
`item.now()`만 읽고 `item.before()`는 한 번도 참조하지 않는다. `BEFORE_MINUTES = 60`은
`findRankingForMarkets`가 before 스냅샷을 한 번 더 조회하게 만들 뿐 출력에 영향이 없다.
그러니 "이미지와 텍스트가 서로 다른 before를 쓴다"는 어긋남은 존재하지 않는다. 같은 이유로
`beforeMinutes`를 텍스트 경로에 흘려도 텍스트 출력은 달라지지 않는다 — 버리는 조회가
주기에 맞춰질 뿐이다. **이걸 어긋남을 고치는 작업으로 설명하지 않는다.**

### 프론트

`CategoryChangeRatePage`가 `market`을 쿼리 파라미터로 받는 것과 같은 방식으로 `beforeMinutes`도
받는다. 같은 `useEffect`에서 처리하고, 반영한 뒤 주소에서 지우는 것까지 동일하다.

- 양의 정수가 아니면 무시하고 기존 값을 쓴다
- 이 변경만 단독으로 배포해도 지금 동작은 달라지지 않는다. 파라미터가 없으면 지금과 같다

### 백엔드

`beforeMinutes`를 발송 경로 전체에 인자로 흘린다.

```
CollectionScheduler → DailyMarketReportSender → MarketMapAndSectorTelegramReportSender
                                                   ├─ 섹터 캡처 URL에 &beforeMinutes=N
                                                   └─ CategoryRankingTextBuilder
```

`BEFORE_MINUTES` 상수는 없앤다. 마켓맵 캡처 URL은 before 개념이 없으므로 건드리지 않는다.

시그니처가 바뀌면 위 그림에 없는 곳들이 함께 깨진다. 전부 같은 커밋에서 고친다.

| 파일 | 왜 |
|---|---|
| `MarketMapTelegramReportSender` | `buildRankingText`를 부른다. `@Scheduled`만 주석 처리돼 있을 뿐 살아 있는 빈이고 `CollectionScheduler`가 필드로 주입받는다 |
| `TelegramReportSender` | 위의 부모 추상 클래스. `buildText(LocalDateTime, MarketQuery)` 시그니처 |
| `MarketMapAndSectorTelegramReportSenderTest` | `buildRankingText` 스텁 2곳 |
| `CategoryRankingTextBuilderTest` | `buildRankingText` 호출 6곳 |
| `TelegramReportCycleManualTest` | `dailyMarketReportSender.send(dataTime, true)` |
| `MarketMapTelegramReportSenderManualTest` | 위 sender를 직접 부른다 |

**매뉴얼 테스트도 컴파일 대상이다.** `build.gradle`의 `manualTest` 태스크는
`sourceSets.test.output.classesDirs`를 그대로 재사용한다. 실행에서만 `@Tag("manual")`로
빠질 뿐 `compileTestJava`에는 잡히므로, 여기가 깨지면 완료 기준 1번이 깨진다.

## 5-3. 카테고리 랭킹 조회를 한 번으로 모은다

### 구조

전체 카테고리 랭킹을 확정해서 내려주는 데까지가 공통이고, 필터링은 쓰는 쪽이 각자 한다.

```
공통(domain/view)   마켓별 랭킹 확정 — 지수 등락률, 카테고리 이름, depth,
                   대분류 필터, 기본 제외 구간 제외, TOP3까지 전부
프론트              전체 뎁스가 필요하므로 필터 없는 쪽을 그대로 쓴다
백엔드 텍스트        확정된 결과를 받아 문자열로만 조립한다
```

표와 아래 「notification은 포매팅만 한다」가 어긋나지 않게 한다. **필터·정렬·TOP3의 주인은
view다.** 텍스트 쪽에는 남기지 않는다.

### 응답에 depth와 categoryName을 싣는다

지금은 응답에 `categoryId`밖에 없어서 양쪽이 뎁스를 알아내려고 각자 한 번 더 조회한다.

```
프론트   useMarketMap 트리를 한 번 더 호출해서 최상위 노드를 대분류로 본다
백엔드   marketMapCategoryRepository.findAll()로 hasNoParent()를 본다
```

`CategoryChangeRateItem`에 `depth`와 `categoryName`을 추가하면 둘 다 없어진다. `depth`는
`MarketMapCategory` 엔티티에 이미 있는 컬럼이다.

**프론트의 zod 스키마도 같이 고친다.** `src/types/api.ts`의 `CategoryChangeRateItemSchema`는
`{ categoryId, now, before }`인데 zod는 모르는 키를 조용히 버린다. 스키마에 두 필드를 넣지
않으면 새 값이 화면에 도달하지 않는다.

### 지수 등락률을 붙이는 곳을 하나로 만든다

같은 맵을 두 곳에서 각자 만든다.

```
MarketMapQueryService.getCategoryChangeRates   findOverviewsBySnapshotTime → Market별 changeRate
CategoryRankingTextBuilder.buildRankingText    findBySnapshotTime → Market별 changeRate
```

**동작이 다르지는 않다.** `findRankingForMarkets(markets, snapshotTime, before)`는 마지막에
`new SnapshotResponse<>(snapshotTime, rankings)`로 인자를 그대로 돌려주므로, 텍스트 경로에서
"랭킹이 확정한 시각" == `dataTime`이다. 두 경로가 쓰는 시각은 같고 `buildRankingText`에도 그
주석이 이미 붙어 있다. **없는 버그를 찾지 않는다.** 고치는 이유는 같은 맵을 만드는 코드가 두
벌이라는 것 하나다.

**`MarketMapQueryService.getCategoryChangeRates`에 스냅샷 시각을 인자로 받는 변형을 만든다.**
지금의 것은 최신 시각을 구한 뒤 그 메서드에 위임하게 바꾸고, 텍스트 쪽은 `dataTime`으로 같은
메서드를 부른다. 지수 등락률을 붙이는 코드는 한 곳만 남는다.

한 층 아래(`MarketMapCategoryChangeRateSnapshotService`)는 이미 그 모양이다.
`findLatestRankingForMarkets`가 최신 시각을 찾아 `findRankingForMarkets`에 위임한다. **그쪽은
건드리지 않는다.** 없는 것은 지수 등락률을 붙이는 위층의 시각 인자 변형뿐이다.

### notification은 포매팅만 한다

`CategoryRankingTextBuilder.buildRankingText`가 한 메서드에서 다음을 전부 한다.

```
랭킹 조회 → 지수 등락률 조회 → 전체 카테고리 조회(이름 맵 + 루트 ID 집합)
→ 제외 구간 조회 → 마켓별 필터·정렬·TOP3 → 텍스트 조립
```

70줄에 스트림이 3중이고, `domain/notification`인데 조회 의존을 네 개 직접 주입받는다
(`MarketMapCategoryRepository`, `MarketOverviewSnapshotRepository` 두 리포지토리와
`MarketMapCategoryChangeRateSnapshotService`, `MarketValueTierThresholdService` 두 서비스).
`docs/architecture.md`는 조회와 집계를 `domain/view`의 역할로, notification을 "데이터를 밖으로
내보낸다"로 정해두었다.

대분류만 고르는 것, 기본 제외 구간을 빼는 것, 가중평균을 합치는 것, 정렬해서 TOP3를 자르는 것까지
전부 view에서 끝낸다. `CategoryRankingTextBuilder`에는 헤더 조립과 `formatPercent`만 남고
리포지토리 주입은 사라진다.

- 새 메서드는 `MarketMapQueryService`에 둔다. 지수 등락률을 붙이는 층이 거기고, 랭킹 확정은
  그 층의 일이다. 별도 조회 서비스를 새로 만들지 않는다
- 반환 타입은 새로 만든다. 마켓, 지수 등락률, 그리고 (카테고리 이름, 등락률) TOP3를 담는다.
  기존 `CategoryChangeRateMarketRanking`은 화면용(전체 뎁스, 필터 없음)이라 재사용하지 않는다
- 이동한 로직의 동작이 바뀌면 안 된다. 기존 `CategoryRankingTextBuilderTest`가 검증하던 것(TOP3
  선정, 대분류 필터, 구간 제외, 포맷)은 옮겨간 자리에서 그대로 검증되어야 한다. 포맷 검증만
  `CategoryRankingTextBuilder`에 남고 나머지는 view 쪽 테스트로 옮겨간다

### 프론트의 트리 조회 제거

섹터 페이지의 `useMarketMap` 호출은 카테고리 이름과 최상위 ID를 얻으려고만 쓴다. 응답에 둘 다
실리면 이 호출은 필요 없다.

**`depth == 0`과 `hasNoParent()`는 동치다.** `MarketMapCategory.createParent/createChild`가
depth를 세팅하고, `MarketMapCategoryService.reparent`가 `changeParent`와 함께 하위 전체 depth를
`depthDifference`만큼 일괄 갱신한다. 둘이 어긋나는 경로가 없다. 지금 판정("트리의 최상위 노드")과
새 판정("랭킹 응답의 depth 0")의 차이는 "그 시각 스냅샷 row가 없는 최상위 카테고리"뿐인데, 그건
어차피 화면 병합 대상에 안 들어오므로 결과가 같다. **확인 절차를 따로 두지 않는다.**

`data-capture-ready`가 `!isLoading && !isTreeLoading`인데 `isTreeLoading`이 사라진다. 조건이
`!isLoading` 하나로 줄어드는 것이 맞다. 기다릴 것이 하나 줄었으니 캡처는 오히려 빨라진다.

`excludedCategoryIds`(사용자 설정) 필터는 그대로 유지한다.

## 5-4. 그 시각 데이터가 없으면 발송하지 않는다

### 왜 필요한가

텔레그램 이미지의 기준 시각은 백엔드가 정하지 못한다. 렌더러가 프론트 화면을 찍는데 프론트는 최신
스냅샷을 그린다. 반면 텍스트와 캡션은 `dataTime` 기준이다.

수집기가 예외를 던지면 `lastIndexContributionSuccess`가 false가 되어 리포트 대신 실패 알림이 나간다.
그런데 예외 없이 그 시각 스냅샷이 안 생긴 경우에는 가드가 없다. 그때 이렇게 된다.

```
텍스트   dataTime(08:25) 기준 → 데이터 없는 마켓은 결과에서 빠져 비거나 반쪽
이미지   프론트가 그린 최신(08:20) 화면이 찍힘
캡션     dataTime(08:25)이 적힘
```

08:20 이미지에 08:25 캡션이 붙는다. 5분 사이에 큰 변화가 없으니 대개는 티가 안 나지만, 티가 나는 그
한 번을 막는 것이 이 항목의 목적이다.

### 가드는 `sectorAvailable`이 참일 때만 돈다

이 가드를 무조건 돌리면 지금 있는 장애 대응 경로가 죽는다. **"카테고리 등락률 스냅샷이 그 시각에
없다"가 정확히 지금의 `sectorAvailable == false` 상황이기 때문이다.**

```java
lastChangeRateSuccess = lastIndexContributionSuccess
        && run("카테고리등락률스냅샷", () -> captureCategoryChangeRateSnapshots(snapshotTime));
```

이 단계가 실패하면 그 시각 row가 안 써지고, `sectorAvailable = false`가 발송기까지 내려간다. 지금은
그때 섹터 이미지만 빼고 맵 이미지는 보내면서 캡션에 "섹터 이미지 생성에 실패했습니다"를 덧붙인다.
가드를 무조건 돌리면 조회가 비어 발송 전체가 스킵되고 **맵 이미지조차 안 나간다.** 장애가 났는데
받는 정보가 지금보다 줄어드는 방향이라 받아들이지 않는다.
`MarketMapAndSectorTelegramReportSenderTest.send_섹터가_불가능하면_맵_이미지만_보내고_캡션에_실패_안내를_덧붙인다`
가 고정하고 있는 동작이고, 그 테스트는 살아 있어야 한다.

두 상황은 원래 다른 상황이다.

| 상황 | 판단 | 결과 |
|---|---|---|
| 수집기가 예외를 던졌다 (`sectorAvailable == false`) | 이미 아는 실패다 | 지금 그대로. 맵만 보내고 캡션에 안내. **가드를 돌리지 않는다** |
| 수집기는 성공했는데 조회가 빈다 (`sectorAvailable == true`) | 조용히 생긴 구멍이다 | 그 마켓 발송을 건너뛴다 |

5-4를 만든 이유가 "예외 없이 그 시각 스냅샷이 안 생긴 경우에는 가드가 없다"였으므로, 예외가 던져진
경우까지 가드에 걸리게 두면 범위를 잘못 잡은 것이다.

### 조치

`sectorAvailable`이 참인 경우에 한해, 발송 직전에 `dataTime`에 그 마켓의 스냅샷이 있는지 확인하고
없으면 그 마켓 발송을 건너뛴다. 두 마켓 다 없으면 아무것도 보내지 않는다.

- **조용히 건너뛴다.** 사용자 채널로 별도 알림을 보내지 않는다. 받지 못한 것 자체가 신호다
- 서버 로그에는 WARN으로 남긴다. 어느 시각 어느 마켓이 왜 빠졌는지 알 수 있어야 한다
- **존재 확인용 쿼리를 새로 만들지 않는다.** 5-3에서 만든 조회 메서드를 그 시각으로 부르고, 결과가
  비어 있으면 없는 것으로 판정한다. 어차피 발송 경로가 그 조회를 하므로 한 번만 조회해서 판정과
  본문 생성에 함께 쓴다
- **가드는 `MarketMapAndSectorTelegramReportSender.send` 안, 캡처보다 먼저다.** 지금 이 메서드는
  캡처를 먼저 돌고 텍스트를 나중에 만든다. 그 순서 그대로 두면 건너뛸 발송에도 캡처가 먼저 도는데,
  캡처가 이 작업에서 가장 비싼 동작이라 의미가 없다. **조회 → 판정 → 캡처 → 텍스트 조립 순으로
  뒤집는다.** `sectorAvailable`이 거짓이라 판정을 건너뛰는 경우에도 순서는 같다. 조회 결과는
  어차피 텍스트 조립에 쓰이므로 앞으로 당겨도 버리는 일이 없다
- 마켓별 판정이므로 "두 마켓 다 없으면 아무것도 안 보낸다"는 별도 분기가 아니라 결과다.
  `DailyMarketReportSender`에는 가드를 두지 않는다
- **세 갈래를 섞지 않는다.** 지금 있는 두 갈래는 그대로 두고 세 번째만 새로 생긴다

| 무엇이 실패했나 | 무엇이 나가나 |
|---|---|
| `lastIndexContributionSuccess == false` | 리포트 대신 데이터수집 실패 알림 (기존) |
| 카테고리 등락률 스냅샷 (`sectorAvailable == false`) | 맵 이미지 + 캡션에 섹터 실패 안내 (기존) |
| 실패는 없는데 그 시각 조회가 빔 | 아무것도 안 나감. 서버 로그에만 WARN (이번에 추가) |

### 이렇게 하면 이미지와 텍스트의 시각이 자동으로 맞는다

건너뛰지 않았다는 것은 `dataTime`에 스냅샷이 있다는 뜻이고, 그러면 그 시각이 최신이므로 프론트가
그리는 화면과 텍스트의 기준이 같아진다. 캡처 URL에 시각을 넘기는 방식(프론트에 시각 지정 조회를
새로 만드는 것)은 채택하지 않는다. 화면은 항상 최신을 보여준다는 원칙과 어긋난다.

## 5-5. 마켓 순서 의존성을 주석으로 고정한다

`DailyMarketReportSender.send`가 KOSPI를 먼저, KOSDAQ을 나중에 부른다. 그 사이에 예외를 잡는 곳이
없어서 KOSPI에서 실패하면 KOSDAQ은 시도조차 되지 않고, KOSDAQ에서 실패하면 KOSPI는 이미 나간 뒤다.

이건 사고가 아니라 의도다. KOSPI를 더 중요하게 보기 때문에 그렇게 두었다. 그런데 그 의도가 코드
어디에도 없고, 두 줄의 호출 순서에만 담겨 있다. 5-1에서 이 메서드에 `beforeMinutes`가 인자로 들어가고
호출부가 바뀌므로, 모르고 손대면 조용히 뒤집힌다.

- 주석으로 고정한다. 내용은 "KOSPI를 더 중요하게 본다. KOSPI가 성공하고 KOSDAQ이 실패하면 KOSPI는
  그대로 나가고, KOSPI가 실패하면 예외가 올라가 KOSDAQ은 시도하지 않는다. 이 두 줄의 순서가 그
  규칙이다"
- **`Market.values()` 순회로 바꾸지 않는다.** 순서가 enum 선언에 묻혀 더 안 보이게 된다
- 부분 성공을 허용하도록 바꾸지 않는다. 이번 범위가 아니다

## 5-6. TelegramClient가 EscalateException을 던지지 않게 한다

### 문제

`EscalateException`은 javadoc에 "발생 즉시 개발자에게 텔레그램 알림을 발송하는 예외"라고 선언된
타입이다. 그것을 텔레그램 전송기 안에서 던지면, 소스를 읽는 사람에게 "텔레그램 실패를 텔레그램으로
알리려 한다"로 읽힌다. 실제 의도는 "전송이 실패했다"뿐이고, 알릴지 말지는 `CollectionScheduler.run()`이
정한다.

동작은 지금도 문제없다. `run()`이 전부 잡아 삼키고, 알림 발송이 또 실패해도 `EscalationNotifier`가
`catch`로 막아서 순환도 생기지 않는다. 고치는 이유는 **코드에 적힌 의도가 실제와 다르기 때문**이다.

부수적으로, `EscalateException.wrap`은 이미 `EscalateException`이면 그대로 반환한다. 그래서 지금은
`run()`이 넘기는 `collectorName`이 context에 붙지 않는다.

### 조치

`TelegramClient` 전용 예외를 만들어 그것을 던진다. `RuntimeException`을 직접 상속한 평범한 예외다.

- `BusinessException`을 상속하지 않는다. `sealed`이고 그 목록은 HTTP 상태 코드 매핑표다. 컨트롤러까지
  도달하지 않는 예외를 거기 넣으면 안 된다. `permits`도 늘리지 않는다
- ErrorCode를 갖지 않는다. 마스킹된 메시지와 원본 타입명만 갖는다. 알림 제목은
  `COLLECTOR_EXECUTION_FAILED | context : 일일마켓리포트발송`이 되고, 원인은 메시지로 붙는다
- **키움 신호 예외들과는 다른 모양이다.** `KiwoomRateLimitException`/`KiwoomTransientFailureException`은
  javadoc에 "상태도 메시지도 갖지 않는다"고 못박은 빈 클래스다. `@Retryable(retryFor = ...)`의 타입
  신호로만 쓰이기 때문이다. 이건 값을 실어 나르므로 그 둘을 본떠 만들지 않는다
- **정보가 하나 줄어드는 것을 감수한다.** 지금은 `TELEGRAM_MESSAGE_SEND_FAILED`와
  `TELEGRAM_IMAGE_SEND_FAILED`가 알림 제목에 구분돼 뜨는데, 새 예외는 ErrorCode가 없으므로 둘 다
  `COLLECTOR_EXECUTION_FAILED`로 통일된다. 대신 지금 안 붙는 `collectorName`이 붙는다. 메시지냐
  이미지냐는 본문의 원본 타입명과 마스킹된 메시지로 구분한다
- **원본 예외를 cause로 달지 않는다.** RestClient 예외 메시지에는 요청 URI가 들어 있고 거기에 봇
  토큰이 박혀 있다. cause로 달면 예외 로그 파일의 스택트레이스와 알림 본문에 토큰이 샌다. 새 예외는
  `SecretMasker`로 마스킹한 메시지와 원본 타입명만 갖는다
- 4단계에서 넣은 `maskedFailure` 헬퍼는 이 과정에서 사라진다. `Object[]`를 반환하는 형태였는데 타입
  정보가 없어 좋은 모양이 아니었다. 새 예외를 만들어 돌려주는 메서드로 대체한다

### ScreenshotClient와 KrxCrawler는 건드리지 않는다

둘도 `EscalateException`을 직접 던진다. 그래서 이번 변경으로 클라이언트 셋 중 하나만 다른 패턴이 된다.
그래도 `TelegramClient`만 바꾼다.

`TelegramClient`가 가진 문제는 자기 참조다. 자기가 죽었는데 자기로 알리라고 한다. 렌더러나 KRX가
죽어도 텔레그램은 살아 있으므로 나머지 둘에는 그 문제가 없다. 다른 문제를 가진 하나를 다르게 다루는
것이지 일관성을 깨는 것이 아니다. 셋을 통일하는 작업은 `docs/backlog.md`의 「클라이언트마다 던지는
예외의 성격이 다르다」에 이미 적혀 있다. **구현 세션이 문서를 고칠 일은 없다.**

`EscalationNotifierTest`가 `EscalateException`을 스텁으로 던지는데, 이 변경 뒤에는 컴파일은 되지만
현실에 없는 상황을 검증하게 된다. 새 예외로 바꾼다.

## 이 단계에서 하지 않는 것

- **대/중/소 토글 UI.** 이번에는 응답에 `depth`를 싣고 프론트가 그것으로 거르는 데까지만 한다.
  토글은 별도 작업으로 뺀다
- **랭킹 규칙의 이중 구현.** 화면과 텍스트가 각자 대분류 필터, 기본 제외 구간, TOP3를 구현하고
  있다. 백엔드가 순위를 확정해 내려주고 프론트는 그리기만 하는 구조로 가야 하는데, 화면은
  전체를 보여주고 텍스트만 TOP3라서 단순히 합칠 수 없다. `docs/backlog.md` 참고
- **렌더러 Chromium 재사용.** backlog에 있다. 아래 부하 항목과 관련되지만 이번 범위가 아니다
- **`ScreenshotClient`와 `KrxCrawler`의 예외 구조.** 5-6 참고. `TelegramClient`만 바꾼다
- **알림 채널 이중화.** 텔레그램이 통째로 죽으면 알림이 전달되지 않고 `exception.log`만 남는다.
  5-6으로도 이건 안 풀린다. `docs/backlog.md` 참고
- **KOSPI 실패 시 KOSDAQ 부분 발송.** 5-5 참고. 지금 동작을 유지한다

## 알아둘 것 — 발송 부하

발송 한 번에 캡처가 4번 일어난다(마켓 2개 × 맵/섹터). 15분 주기면 시간당 16번으로 지금(30분
주기)의 두 배다. 겹침 정책이 `all`이면 겹치는 tick에서 캡처 8번이 한 tick 안에서 순차로 돈다.
렌더러 read 타임아웃이 90초라 최악의 경우 5분 tick을 넘겨 다음 수집이 밀린다.

**부하를 알고도 15분으로 낸다. 사용자가 정했다.** 근거는 캡처 실패의 원인을 고친 뒤로 실패
사례가 없다는 것이다. 위험을 감수하는 것이지 없다고 보는 것이 아니므로 아래를 알고 시작한다.

- tick이 밀리면 스프링 cron이 다음 발화 시각을 새로 잡아 그 사이 tick이 통째로 스킵된다. 그러면
  그 시각 스냅샷이 아예 없고, `findRankingForMarkets`는 정확히 일치하는 시각만 조회하므로 5-4
  가드가 다음 발송까지 건너뛴다. 캡처가 느려지면 발송이 줄어드는 방향으로 연결된다
- 15분 주기면 08:10 / 08:25 / 08:40 / 08:55 네 번이 개장(09:00) 전에 나간다. 개장 전에도 수집기는
  정상으로 돌고 새 스냅샷이 생기므로 내용이 같지는 않다

**되돌리는 방법은 프로퍼티 하나다.** 실패가 늘거나 tick이 밀리기 시작하면
`telegram.send-interval-minutes`의 앞 숫자를 30으로 올린다. 코드는 건드리지 않는다. 주기를
프로퍼티로 뺀 이유가 이것이다.

## PR 설명에 적을 것

### 프론트

- `beforeMinutes` 파라미터가 없으면 동작이 지금과 같다는 것
- 트리 조회를 제거했는지, 제거했다면 카테고리 목록이 같은지 어떻게 확인했는지

### 백엔드

- 이미지의 before를 백엔드가 정하지 못하고 있었다는 것(sessionStorage가 항상 비어 30분 고정).
  텍스트는 before를 안 쓰므로 "어긋나 있었다"고 적지 않는다
- 08:40 발송이 복구된다는 것. 배포 후 다음 영업일 08:40 텔레그램이 오는지가 확인 지점이다
- 프로퍼티로 설정한 주기와 겹침 정책의 초기값
- `CategoryRankingTextBuilder`에서 옮긴 로직의 목록과 옮긴 자리
- 그 시각 스냅샷이 없어 발송을 건너뛰는 경로를 어떻게 검증했는지(5-4)
- `TelegramClient`가 던지는 예외가 바뀌면서 알림 문구가 어떻게 달라지는지(5-6). 배포 후 실패 알림이
  실제로 그 형태로 오는지가 확인 지점이다

# 6단계 — 예외 로그를 한곳에 모은다

브랜치명 예: `claude/refactor/exception-log`

5단계가 병합된 뒤에 시작한다. 백엔드 레포만 바꾼다.

## 이 단계가 하는 일

지금은 텔레그램 알림과 파일 기록이 한 경로에 묶여 있다. `EscalationPublisher.report()`를 거친
것만 `exception.log`에 들어가고, 나머지 예외는 `application.log`에 섞여 있다.

둘을 나눈다. **텔레그램은 즉시 대응이 필요한 것만, 파일은 전체 예외.** 사용자가 정한 방향이다.

지금 `exception.log`에 들어가지 않는 것들이다.

- `BadRequestException`, `NotFoundException`, `ConflictException` (400/404/409)
- catch-all에서 5분 억제 창에 걸린 반복 예외
- 클래스 로거로 찍는 `log.error` 전부. `EscalationNotifier`의 "에스컬레이션 알림 발송에 실패했습니다"가
  대표적이다. 알림이 실패한 사실이 정작 예외 파일에 안 남는다

**이 변경으로도 안 들어오는 것이 있다.** 필터는 로그 이벤트에 throwable이 붙어 있는지만 본다.
throwable 없이 메시지만 찍는 로그는 6-1을 넣어도 그대로 빠진다.

- `MethodArgumentNotValidException`(400) — `GlobalExceptionHandler.handleMethodArgumentNotValid`가
  `log.warn(detail)`로 예외 없이 찍는다. **6-4와 같은 조치를 여기에도 한다.** 예외를 인자로 붙인다
- `MethodArgumentTypeMismatchException` 계열 — `isSpringHandledException`에 걸려 다시 던져지고
  스프링 기본 처리로 간다. 스프링의 `AbstractHandlerExceptionResolver.logException`이 throwable
  없이 찍으므로 필터를 통과하지 못한다. **이번에는 손대지 않는다.** 스프링이 잡아 처리하는 경로를
  가져오는 것은 별개의 판단이다

## 6-1. throwable이 붙은 로그를 전부 예외 파일로 보낸다

로깅 호출부를 하나씩 고치지 않는다. logback appender에 필터를 걸고 그 appender를 root에 붙인다.

```java
public class ThrowableFilter extends Filter<ILoggingEvent> {
    @Override
    public FilterReply decide(ILoggingEvent event) {
        return event.getThrowableProxy() != null ? FilterReply.ACCEPT : FilterReply.DENY;
    }
}
```

```xml
<appender name="EXCEPTION_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <filter class="..." />
    <file>logs/exception.log</file>
    ...
</appender>

<root level="INFO">
    <appender-ref ref="CONSOLE" />
    <appender-ref ref="APP_FILE" />
    <appender-ref ref="EXCEPTION_FILE" />
</root>
```

- **잡는 지점마다 전용 로거를 쓰는 방식은 채택하지 않는다.** 지금 있는 호출부를 다 찾아 고쳐도 앞으로
  추가되는 것을 강제할 방법이 없어서 누락이 조용히 생긴다. 필터는 어느 로거로 찍든 걸린다
- **필터 클래스는 `common/logging`에 둔다.** 도메인이 없고 logback이 XML에서 이름으로만 참조하는
  인프라 코드다
- 예외가 붙지 않은 로그는 들어가지 않는다. 그게 의도다
- 예외는 `application.log`에도 그대로 남는다. 시간순 전체 흐름과 예외만 추린 뷰는 용도가 다르다
- appender 이름은 `ESCALATION_FILE`에서 `EXCEPTION_FILE`로 바꾼다. 「파일 이름 변경 안 함」은
  `logs/exception.log` 경로를 뜻하고 appender 이름은 거기 해당하지 않는다. 더 이상 에스컬레이션
  전용이 아니게 되므로 이름이 남으면 오해를 부른다
- `<springProfile name="!prod">`의 CONSOLE-only root는 **그대로 둔다.** 파일 appender는 prod에만
  붙는다는 지금 구조를 바꾸지 않는다
- root level이 INFO라 그 아래(DEBUG)로 찍는 throwable은 필터가 보지 못한다. 지금 그런 호출은 없다

## 6-2. ESCALATION 전용 로거를 없앤다

6-1이 들어가면 전용 로거가 필요 없어진다.

- logback의 `<logger name="ESCALATION">` 블록을 제거한다
- `EscalationPublisher`의 `ESCALATION_LOG`를 평범한 클래스 로거로 바꾼다. root를 타고 같은 파일에
  들어간다
- **텔레그램 알림 경로(`EscalationEvent` 발행)는 그대로 둔다.** 그것이 즉시 대응 채널이다. 이번에
  나누는 것은 파일 기록 쪽이다

**중복은 오히려 줄어든다.** 지금 `<logger name="ESCALATION">`에 `additivity="false"`가 없어서
에스컬레이션 로그는 이미 `exception.log`와 `application.log` 양쪽에 남고 있다. 6-2가 그 블록을
지우면 경로가 root 하나로 줄어든다.

6-1만 넣고 6-2를 안 넣은 중간 상태에서는 같은 이벤트가 `exception.log`에 두 줄 찍힌다(로거의 직접
`appender-ref` + root를 타고 한 번 더). 한 PR 안이라 배포에는 나가지 않지만, **6-1과 6-2를 서로
다른 PR로 쪼개지 않는다.**

logback의 `name="ESCALATION"`과 코드의 `LoggerFactory.getLogger("ESCALATION")`은 문자열로만 짝지어져
있다. 한쪽만 지우면 컴파일도 되고 테스트도 통과하는데 로거가 root로 떨어진다. 이번에는 둘 다
없애므로 해당 없지만, 부분적으로 바꾸지 않는다.

`EscalationPublisher`, `EscalationNotifier`, `EscalateException` 같은 클래스 이름은 바꾸지 않는다.
"즉시 알린다"는 개념의 이름이고 파일명과는 층위가 다르다.

## 6-3. 두 로그 파일에 용량 상한을 건다

`application.log`에 지금 상한이 없다. `maxHistory 7`만 있어서 하루에 로그가 폭주하면 그 하루가 디스크를
채운다. 새로 생기는 위험이 아니라 지금 있는 위험이다. 예외 파일에 400/404가 들어오기 시작하면 같은
문제가 한 겹 더 생기므로 함께 막는다.

`SizeAndTimeBasedRollingPolicy`로 바꾸고 아래 값을 건다.

| 파일 | maxFileSize | maxHistory | totalSizeCap |
|---|---|---|---|
| `application.log` | 100MB | 7 | 1GB |
| `exception.log` | 50MB | 30 | 500MB |

상한은 평소 사용량이 아니라 **폭주했을 때 잃어도 되는 양**으로 잡는다. 로그 디렉터리 실측이 2.2MB라
정상 운영에서는 이 값에 닿지 않는다. 봇 스캔으로 400이 쏟아지거나 재시도 루프가 도는 경우에만
의미가 있다.

`${LOG_DIR}:/app/logs` 바인드 마운트라 컨테이너가 아니라 호스트 디스크를 쓴다. `/dev/sda1`이 49G에
32G 여유이므로 최악의 경우 1.5GB는 여유 안에 들어온다.

## 6-4. 억제된 예외에도 스택을 남긴다

`GlobalExceptionHandler`의 catch-all이 5분 억제 창에 걸린 예외를 이렇게 찍는다.

```java
log.warn("[예상 못한 예외 알림 억제] | key : {} | 억제 누적 : {}건", key, suppressedCount.incrementAndGet());
```

throwable이 안 붙어서 6-1의 필터에 걸리지 않는다. **알림은 억제하되 기록은 남긴다**가 맞으므로 예외를
인자로 붙인다. 한 줄이다.

## 이 단계에서 하지 않는 것

- **로그 수집 플랫폼 도입.** 파일로 남기는 것까지만 한다
- **파일 이름 변경.** `application.log`와 `exception.log`를 그대로 쓴다
- **클래스 이름 변경.** 6-2 참고
- **알림 채널 이중화.** `docs/backlog.md` 참고. 이번 변경으로 풀리지 않는다

## 검증

### 자동 테스트로 메운다

이 단계의 변경은 대부분 XML과 필터라 평소 방식으로는 아무것도 검증되지 않는다. 파일 appender가
`prod` 프로파일에만 붙는데 `application-prod.properties`는 컨테이너 호스트명 DB에
`ddl-auto=validate`와 Flyway가 켜져 있어 DB 없이 기동하지 않고, CI도 DB 없이 도는 것이 전제다.
`docs/rules/process.md`가 "사람이 리뷰를 안 하면 그 자리를 자동 검증이 메워야 한다"고 정해두었으므로
**아래 두 테스트를 이 단계의 산출물에 포함한다.** DB 없이 돌고 CI에 잡힌다.

- **`ThrowableFilter` 단위 테스트.** throwable이 붙은 이벤트는 `ACCEPT`, 안 붙은 이벤트는 `DENY`.
  스프링도 DB도 필요 없다
- **`logback-spring.xml` 결선 테스트.** `JoranConfigurator`로 `prod` 프로파일을 지정해 XML을
  직접 로드하고, `EXCEPTION_FILE` appender가 root에 붙어 있는지, 거기에 `ThrowableFilter`가
  걸려 있는지, 롤링 정책의 상한 값이 표대로인지를 단언한다. XML 오타나 클래스명 오기를 여기서
  잡는다. `<logger name="ESCALATION">`이 사라졌다는 것도 같이 확인한다

### 배포 후 사용자가 확인하는 것

실제로 파일이 써지는지는 자동 테스트로 확인할 수 없다. 배포 후 확인 항목으로 남긴다.

- 예외가 붙은 로그가 `application.log`와 `exception.log` 양쪽에 들어가는지
- 예외가 없는 INFO 로그가 `exception.log`에 들어가지 **않는지**
- `BusinessException` 계열이 실제로 던져지는 요청을 한 번 보내 `exception.log`에 남는지. 이게 이번
  변경의 핵심이라 반드시 확인한다. **예시로 "존재하지 않는 마켓 파라미터"를 쓰면 안 된다** —
  그건 `MethodArgumentTypeMismatchException`이라 `isSpringHandledException`에 걸려 다시 던져지고
  필터에 안 잡힌다. 없는 카테고리 id 조회(`MarketMapCategoryService`의 `NotFoundException`)처럼
  핸들러가 직접 잡아 `log.error(e.createLogMessage(), e)`로 찍는 경로를 쓴다
- 롤링 정책 변경 후 앱이 정상 기동하는지

## PR 설명에 적을 것

- 필터 클래스를 어디에 뒀고 왜 거기인지
- 배포 후 `exception.log`에 무엇이 새로 들어오게 되는지. 운영자가 파일을 열었을 때 내용이 달라진다
- 용량 상한 값과 근거

# 마지막 단계 — 문서 마무리

**설계·문서 세션이 수행한다.** 구현 세션은 관여하지 않는다.

- `docs/rules/style.md` **전면 재검토** — 225줄 중 상당수가 사용자가 승인한 적 없는 규칙이다.
  1A~4단계에서 실제로 바뀐 내용을 반영하고, 코드와 어긋나거나 근거가 약한 항목을 정리한다.
  판단이 애매한 항목은 목록으로 뽑아 사용자 확인을 받는다
- `docs/rules/testing.md` 확정 — 실제 작성한 테스트 반영
- `docs/architecture.md` 갱신
- `docs/operations.md` 갱신 — 1A로 바뀐 배포·롤백 절차가 반영돼 있는지 확인
- `docs/decisions.md` / `docs/backlog.md` 갱신 — 각 PR에서 나온 판단 회수
- **`docs/work-plan.md`(이 파일) 삭제**

---

# 이번 작업에서 다루지 않는 것

배경과 구상은 `docs/backlog.md`에 있다.

- **보안 모델 전반** — 로그인 기능 도입으로 통째로 대체될 영역
- **로그인 기능** — 정비 후 첫 신규 기능
- **공휴일 판정 구현** (`TODO(#38)`)
- **데이터 지연 감지** — 공휴일 판정이 선행. 프론트 작업도 필요
- **JPA Auditing 전환** — 엔티티 20개(대입 51곳)의 동작 변경인데 DB 없는 CI로는 검증 불가.
  하나라도 빠뜨리면 프로덕션 INSERT가 NOT NULL 위반으로 실패한다
- **관심종목(WatchStock) 구조 정리**
- **대량 insert 배치화**
- **`snapshot_time` 인덱스 추가** — `V1` 수정 부담
- **프론트엔드 레포 정비** — 백엔드 갈무리 후
