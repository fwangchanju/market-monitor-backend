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
> | 5 텔레그램 발송 주기 재구성 | 완료 (백엔드 PR #98, 프론트 PR #52) |
> | 6 페이지별 발송 주기 분리 | 다음 |
> | 7 예외 로그 정리 | |
> | 마지막 문서 마무리 | |
>
> **완료된 단계의 본문은 지웠다.** 다시 할 일이 없는데 읽을 양만 늘리기 때문이다. 필요하면 git
> 히스토리나 위 표의 PR에서 본다. 아래 남은 규칙과 6·7단계만 읽으면 된다.
>
> 6단계는 5단계가 배포된 뒤 실제로 받아보고 나온 요구다. 5단계에서 확정했던 것 일부를 되돌린다.
> 어디를 왜 되돌리는지는 6단계 본문에 적어두었다.

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
- **이 지시서의 항목 번호를 커밋 단위로 착각하지 않는다.** 6-1~6-5는 설명의 단위이지 커밋의 단위가
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

# 6단계 — 페이지별 발송 주기 분리

브랜치명 예: `claude/refactor/send-composition`

5단계가 배포된 뒤에 시작한다. **백엔드 레포만 바꾼다.** 프론트는 건드리지 않는다.

## 이 단계가 하는 일

5단계는 "주기가 곧 before"였다. 15분 주기면 15분 전 대비, 2시간 주기면 2시간 전 대비로 보냈다.
실제로 받아보니 원하는 것이 달랐다.

**맵과 섹터는 갱신 속도가 다르다.** 맵은 판이 크게 바뀔 때만 의미가 있어서 15분마다 볼 이유가 없고,
섹터는 자주 보고 싶다. 그리고 before는 화면을 읽는 기준이라 발송 주기와 묶일 이유가 없다.

그래서 셋을 분리한다.

```
발송 주기      15분
맵 포함 주기    2시간 (그 tick에만 맵 이미지를 함께 보낸다)
before        15분 고정 (주기와 무관)
```

발송 모양도 둘로 갈린다.

```
2시간 격자 (08:10, 10:10, …, 20:10)
  메시지 1   코스피 맵 + 코스피 섹터 + 캡션(코스피 TOP3)
  메시지 2   코스닥 맵 + 코스닥 섹터 + 캡션(코스닥 TOP3)

그 밖의 15분 지점 (08:25, 08:40, …)
  메시지 1   코스피 섹터 + 코스닥 섹터 + 캡션(코스피 TOP3 + 코스닥 TOP3)
```

2시간 격자는 **지금 동작 그대로**다. 새로 생기는 것은 "섹터만, 두 마켓 한 메시지" 한 가지다.

## 부하가 절반 가까이 준다

5단계 배포 때 감수했던 부하가 상당 부분 해소된다.

```
              하루 발송   캡처 장수   텔레그램 메시지
5단계          49회       196장       98건
6단계          49회       112장       56건
```

발송 tick 수는 같다(08:10~20:10, 15분 간격 49회). 그중 2시간 격자가 7회, 나머지 42회가 섹터만이다.
섹터만 발송은 캡처가 4장에서 2장으로, 메시지가 2건에서 1건으로 준다.

## 6-1. 프로퍼티를 다시 잡는다

5단계의 프로퍼티는 "주기 목록 + 겹침 정책"이었다. 주기가 하나가 되고 before가 독립하면서 그 구조가
통째로 필요 없어진다.

```properties
telegram.send-minute=10
telegram.send-interval-minutes=15
telegram.map-interval-minutes=120
telegram.before-minutes=15
```

- `send-interval-minutes`는 **다시 단수 `int`**가 된다. 5단계에서 `List<Integer>`로 바꾼 것을
  되돌린다
- `telegram.overlap`과 `TelegramOverlap` enum을 **삭제한다.** 주기가 하나뿐이라 겹칠 일이 없다.
  "겹치면 긴 쪽만"이라는 개념 자체가 사라진다
- `before-minutes`는 새 프로퍼티다. 주기에서 파생시키지 않는다

`TelegramProperties`의 record 시그니처가 또 바뀐다. 5단계와 같은 자리가 깨지므로 같은 커밋에서
고친다.

| 파일 | 위치 |
|---|---|
| `EscalationNotifierTest` | `new TelegramProperties(...)` 2곳 |
| `MarketMapAndSectorTelegramReportSenderTest` | `new TelegramProperties(...)` |
| `TelegramSendScheduleTest` | 상수와 헬퍼 전반 |

### 기동 시 검증

5단계에서 넣은 검증을 새 프로퍼티에 맞춰 다시 쓴다. `IllegalStateException`을 던지는 방식은
그대로 유지한다 — 기동 실패 자체가 신호라 `EscalateException`을 쓰지 않는다.

- `send-minute > 0` — 유지. `shouldCollect`가 `시각 <= endHour:00`까지 true라서 0이면 마감 리포트
  조건이 영영 성립하지 않는다
- `send-interval-minutes > 0`, `before-minutes > 0`, `map-interval-minutes > 0`
- `send-minute`, `send-interval-minutes`, `before-minutes`가 `collect.interval-minutes`의 배수
- **`map-interval-minutes`가 `send-interval-minutes`의 배수** — 새로 필요한 검증이다. 배수가
  아니면 맵 포함 tick이 발송 격자와 어긋나 맵이 영영 안 나가거나 엉뚱한 시각에 나간다

## 6-2. 판정을 "보낼지 + 맵을 포함할지"로 바꾼다

`TelegramSendSchedule.due()`가 지금은 주기 목록(`List<Integer>`)을 돌려준다. 주기가 하나가 되면
목록일 이유가 없고, 대신 "이번 tick에 맵을 함께 보내는가"를 답해야 한다.

```java
public enum TelegramSendKind { NONE, SECTOR_ONLY, WITH_MAP }

public TelegramSendKind due(LocalDateTime now, boolean shouldCollect)
```

판정 규칙이다.

```
shouldCollect 이고
  경과분 < 0                              → NONE
  경과분 % send-interval != 0             → NONE
  경과분 % map-interval == 0              → WITH_MAP
  그 밖                                   → SECTOR_ONLY

shouldCollect 가 꺼졌고
  지금이 endHour:sendMinute               → WITH_MAP (마감 리포트)
  그 밖                                   → NONE
```

- 기준점은 그대로 **그날 `startHour:sendMinute`**다
- **`경과분 >= 0`을 빠뜨리지 않는다.** cron이 `0 0/5 8-20`이라 08:00과 08:05에도 tick이 뜬다.
  자바의 `%`는 음수에 음수를 돌려주므로 대부분 우연히 안 걸리지만, `sendMinute`이 주기의 배수인
  조합에서는 걸린다
- **마감 리포트는 `WITH_MAP`으로 고정한다.** 지금 설정(08:10 기준, 2시간 격자)에서는 20:10이
  격자에 저절로 걸리지만 그 계산에 기대지 않는다. 그날 마지막 메시지에 맵이 빠지면 안 된다
- `TelegramOverlap`과 `applyOverlap`은 사라진다

`TelegramSendScheduleTest`를 새 반환 타입에 맞춰 다시 쓴다. 5단계에서 검증하던 시각은 전부 유지하고
기대값만 바꾼다. 08:00(기준점 이전), 08:10(맵 포함), 08:20(격자 밖), 08:40(섹터만), 10:10(맵 포함),
20:10(마감), 20:40(발송 없음).

## 6-3. `beforeMinutes` 인자 연쇄를 걷어낸다

5단계에서 `beforeMinutes`를 `CollectionScheduler → DailyMarketReportSender →
MarketMapAndSectorTelegramReportSender → …`로 흘렸다. 주기마다 값이 달라지기 때문이었다.

**이제 고정값이라 흘릴 이유가 없다.** 인자를 걷어내고 sender가 `TelegramProperties.beforeMinutes()`를
직접 읽는다. sender들은 이미 `TelegramProperties`를 주입받고 있다(`chatId()` 때문에).

되돌아가는 자리다.

- `DailyMarketReportSender.send(dataTime, sectorAvailable, beforeMinutes)`에서 인자 제거
- `MarketMapAndSectorTelegramReportSender.send(...)`에서 인자 제거
- `TelegramReportSender.buildText(dataTime, query, beforeMinutes)`에서 인자 제거
- `MarketMapTelegramReportSender.buildText(...)`에서 인자 제거
- `CollectionScheduler.collectMarketDataHourly()`의 `send(snapshotTime, MarketQuery.KOSPI, 60)`에서
  `60`과 그 주석이 사라진다
- 매뉴얼 테스트 두 개(`TelegramReportCycleManualTest`, `MarketMapTelegramReportSenderManualTest`)도
  함께 고친다. `manualTest`는 `sourceSets.test.output.classesDirs`를 재사용하므로 `compileTestJava`에
  잡힌다

**5단계를 되돌리는 것이 아니다.** 5단계는 "화면 before를 백엔드가 정할 수 있게" 통로를 뚫은 것이고,
그 통로(캡처 URL의 `&beforeMinutes=N`, 프론트의 쿼리 파라미터 처리)는 그대로 쓴다. 값이 tick마다
달라지지 않으니 인자로 들고 다니지 않을 뿐이다.

## 6-4. 섹터만 보내는 발송기를 만든다

지금 `MarketMapAndSectorTelegramReportSender`는 **마켓 하나**의 맵+섹터를 한 메시지로 보낸다.
새로 필요한 것은 **두 마켓**의 섹터를 한 메시지로 보내는 것이다.

**기존 클래스에 모드를 추가하지 않는다. 별도 발송기를 만든다.** 메시지의 단위가 다르기 때문이다
(마켓 하나 vs 전체). 한 클래스가 두 단위를 갖게 하면 "이 메서드가 만드는 메시지는 몇 개인가"를
호출부마다 다시 확인해야 한다.

- 이름은 `SectorTelegramReportSender`. 위치는 `domain/notification/service`
- `MarketQuery.ALL_STOCK`으로 `getTopCategoryRankings`를 **한 번** 호출한다
- 캡션은 그 결과를 `CategoryRankingTextBuilder.buildRankingText`에 그대로 넘긴다. 마켓별 블록을
  `\n\n`로 이어붙이는 동작이 이미 있어서 **새로 짤 것이 없다**

```
#코스피 +0.82%
반도체 +1.35%
...

#코스닥 -0.31%
바이오 +0.94%
...
```

### 이미지는 마켓별로 두 장을 따로 찍는다

`?market=ALL_STOCK`으로 한 장만 찍으면 안 된다. 섹터 페이지는 `ALL_STOCK`일 때 코스피와 코스닥을
**하나로 합쳐서** 그리고, 그러면 마켓 지수 바(노란색)도 사라진다(`items.find(item => item.market ===
market)`이 `ALL_STOCK`과 일치하는 항목을 못 찾는다). 마켓별 비교가 목적이므로 `?market=KOSPI`와
`?market=KOSDAQ`을 각각 찍어 한 앨범에 넣는다.

캡처 URL에 `&beforeMinutes=N`을 붙이는 것은 지금과 같다.

### 캡처할 마켓은 랭킹 결과에서 뽑는다

`getTopCategoryRankings`가 그 시각 데이터 없는 마켓을 이미 결과에서 뺀다. **결과에 있는 마켓만
캡처한다.** 목록을 상수로 박아두면 데이터 없는 마켓의 빈 화면이 앨범에 섞인다.

### 부분 성공은 사라진다

`DailyMarketReportSender`에 주석으로 고정해 둔 판단이 이 경로에는 적용되지 않는다.

> KOSPI를 더 중요하게 본다. KOSPI가 성공하고 KOSDAQ이 실패하면 KOSPI는 그대로 나간다.

한 메시지로 합치면 **하나가 실패하면 둘 다 안 나간다.** 사용자가 받아들이기로 했다. 15분마다 오는
발송이라 한 번 빠져도 15분 뒤에 온다.

**2시간 격자 발송은 지금처럼 마켓별로 나뉘므로 그 순서 규칙과 주석은 그대로 유지한다.**
`DailyMarketReportSender`의 그 주석을 지우지 마라.

### 섹터 스냅샷이 없으면 아무것도 보내지 않는다

`sectorAvailable`이 false면(카테고리 등락률 수집이 예외를 던진 경우) 섹터 이미지를 못 그린다.
섹터만 보내는 발송에는 대신 내보낼 맵이 없으므로 **보낼 것이 아무것도 없다.**

- **발송하지 않는다.** 서버 로그에 WARN만 남긴다
- 사용자 채널로 실패 안내를 보내지 않는다. 15분마다 오던 것이 안 오는 것 자체가 신호다
- 수집기 실패는 `CollectionScheduler.run()`이 이미 개발자 에스컬레이션으로 따로 알린다
- 2시간 격자 발송은 지금 그대로다. 맵 이미지가 나가고 캡션에 "섹터 이미지 생성에 실패했습니다"가
  붙는다. 그러니 **최대 2시간 안에는 사용자 채널에서도 상태를 알 수 있다**

`MarketMapAndSectorTelegramReportSender`에 있는 가드(수집은 성공했는데 그 시각 조회가 비는 경우)는
이 발송기에도 같은 규칙으로 적용한다.
조회 결과가 통째로 비면 발송하지 않는다.

## 6-5. 스케줄러가 두 발송기를 가른다

`CollectionScheduler.collectMarketData`의 발송 게이팅이 이렇게 바뀐다.

```
TelegramSendKind kind = telegramSendSchedule.due(snapshotTime, shouldCollect);
kind == NONE          → 아무것도 하지 않는다
수집 실패              → 지금처럼 데이터수집실패알림 한 번 (kind와 무관)
kind == WITH_MAP      → dailyMarketReportSender.send(...)   (마켓별 2메시지)
kind == SECTOR_ONLY   → sectorTelegramReportSender.send(...) (1메시지)
```

- 5단계의 `for (int beforeMinutes : dueCycles)` 반복은 사라진다. 한 tick에 한 종류만 나간다
- **실패 알림은 그 tick에 한 번만.** 지금 구조 그대로다
- `run(...)`으로 감싸는 것도 그대로다. 발송기 이름만 갈린다

## 이 단계에서 하지 않는 것

- **프론트 변경.** 캡처 URL 규약과 쿼리 파라미터 처리는 5단계에서 이미 들어갔다. `beforeMinutes`
  프리셋에 120을 추가하는 것도 하지 않는다 — 이제 120으로 찍을 일이 없다
- **맵 페이지의 before.** 마켓맵 캡처 URL에는 before 개념이 없다. 그대로 둔다
- **`MarketMapAndSectorTelegramReportSender`의 구조 변경.** 2시간 격자 경로는 지금 동작을 유지한다.
  인자에서 `beforeMinutes`가 빠지는 것 말고는 손대지 않는다
- **`DailyMarketReportSender`의 마켓 순서 규칙.** 2시간 격자 경로에 그대로 살아 있다
- **알림 채널 이중화.** `docs/backlog.md` 참고

## 알아둘 것 — 15분 발송이 하루 42건이다

합쳐도 하루 56건이다. 알림이 많다고 느껴지면 `telegram.send-interval-minutes`를 30으로 올리면
된다. 그러면 하루 25회 발송, 메시지 30건 수준이 된다. `map-interval-minutes=120`은 30의 배수라
검증에 걸리지 않는다.

반대로 맵을 더 자주 보고 싶으면 `map-interval-minutes`를 60으로 내린다. 코드 수정 없이 둘 다
프로퍼티로 조절된다.

## PR 설명에 적을 것

- 프로퍼티 네 개의 값과, `overlap`이 왜 사라졌는지
- 하루 발송 횟수·캡처 장수·메시지 건수가 5단계 대비 어떻게 달라지는지
- `beforeMinutes` 인자 연쇄를 걷어낸 범위. 5단계를 되돌리는 것이 아니라는 설명
- 섹터만 보내는 경로에서 부분 성공이 사라진다는 것과, 2시간 격자 경로에는 마켓 순서 규칙이 그대로 살아 있다는 것
- `sectorAvailable`이 false일 때 섹터만 발송이 조용히 빠진다는 것. 배포 후 확인 지점이다
- 배포 후 다음 영업일 08:10에 맵이 오고 08:25에 섹터만 오는지가 확인 지점이라는 것

# 7단계 — 예외 로그를 한곳에 모은다

브랜치명 예: `claude/refactor/exception-log`

6단계가 병합된 뒤에 시작한다. 백엔드 레포만 바꾼다.

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
throwable 없이 메시지만 찍는 로그는 7-1을 넣어도 그대로 빠진다.

- `MethodArgumentNotValidException`(400) — `GlobalExceptionHandler.handleMethodArgumentNotValid`가
  `log.warn(detail)`로 예외 없이 찍는다. **7-4와 같은 조치를 여기에도 한다.** 예외를 인자로 붙인다
- `MethodArgumentTypeMismatchException` 계열 — `isSpringHandledException`에 걸려 다시 던져지고
  스프링 기본 처리로 간다. 스프링의 `AbstractHandlerExceptionResolver.logException`이 throwable
  없이 찍으므로 필터를 통과하지 못한다. **이번에는 손대지 않는다.** 스프링이 잡아 처리하는 경로를
  가져오는 것은 별개의 판단이다

## 7-1. throwable이 붙은 로그를 전부 예외 파일로 보낸다

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

## 7-2. ESCALATION 전용 로거를 없앤다

7-1이 들어가면 전용 로거가 필요 없어진다.

- logback의 `<logger name="ESCALATION">` 블록을 제거한다
- `EscalationPublisher`의 `ESCALATION_LOG`를 평범한 클래스 로거로 바꾼다. root를 타고 같은 파일에
  들어간다
- **텔레그램 알림 경로(`EscalationEvent` 발행)는 그대로 둔다.** 그것이 즉시 대응 채널이다. 이번에
  나누는 것은 파일 기록 쪽이다

**중복은 오히려 줄어든다.** 지금 `<logger name="ESCALATION">`에 `additivity="false"`가 없어서
에스컬레이션 로그는 이미 `exception.log`와 `application.log` 양쪽에 남고 있다. 7-2가 그 블록을
지우면 경로가 root 하나로 줄어든다.

7-1만 넣고 7-2를 안 넣은 중간 상태에서는 같은 이벤트가 `exception.log`에 두 줄 찍힌다(로거의 직접
`appender-ref` + root를 타고 한 번 더). 한 PR 안이라 배포에는 나가지 않지만, **7-1과 7-2를 서로
다른 PR로 쪼개지 않는다.**

logback의 `name="ESCALATION"`과 코드의 `LoggerFactory.getLogger("ESCALATION")`은 문자열로만 짝지어져
있다. 한쪽만 지우면 컴파일도 되고 테스트도 통과하는데 로거가 root로 떨어진다. 이번에는 둘 다
없애므로 해당 없지만, 부분적으로 바꾸지 않는다.

`EscalationPublisher`, `EscalationNotifier`, `EscalateException` 같은 클래스 이름은 바꾸지 않는다.
"즉시 알린다"는 개념의 이름이고 파일명과는 층위가 다르다.

## 7-3. 두 로그 파일에 용량 상한을 건다

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

## 7-4. 억제된 예외에도 스택을 남긴다

`GlobalExceptionHandler`의 catch-all이 5분 억제 창에 걸린 예외를 이렇게 찍는다.

```java
log.warn("[예상 못한 예외 알림 억제] | key : {} | 억제 누적 : {}건", key, suppressedCount.incrementAndGet());
```

throwable이 안 붙어서 7-1의 필터에 걸리지 않는다. **알림은 억제하되 기록은 남긴다**가 맞으므로 예외를
인자로 붙인다. 한 줄이다.

## 이 단계에서 하지 않는 것

- **로그 수집 플랫폼 도입.** 파일로 남기는 것까지만 한다
- **파일 이름 변경.** `application.log`와 `exception.log`를 그대로 쓴다
- **클래스 이름 변경.** 7-2 참고
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
