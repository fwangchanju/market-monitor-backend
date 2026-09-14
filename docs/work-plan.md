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
> | 6 페이지별 발송 주기 분리 | 완료 (PR #100) |
> | 7 예외 로그 정리 | |
> | 마지막 문서 마무리 | |
>
> **완료된 단계의 본문은 지웠다.** 다시 할 일이 없는데 읽을 양만 늘리기 때문이다. 필요하면 git
> 히스토리나 위 표의 PR에서 본다. 아래 남은 규칙과 7단계만 읽으면 된다.

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
- **이 지시서의 항목 번호를 커밋 단위로 착각하지 않는다.** 7-1~7-4는 설명의 단위이지 커밋의 단위가
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

# 7단계 — 예외 로그를 한곳에 모은다

브랜치명 예: `claude/refactor/exception-log`

백엔드 레포만 바꾼다.

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

### `fileNamePattern`에 `%i`를 넣는다 — 안 넣으면 기동이 막힌다

`SizeAndTimeBasedRollingPolicy`는 `fileNamePattern`에 `%i`가 **반드시 있어야 한다.** 없으면 logback이
설정 오류를 내고 그 appender가 붙지 않는다. 지금 패턴은 `%i`가 없다.

```
지금    logs/application.%d{yyyy-MM-dd}.log
이후    logs/application.%d{yyyy-MM-dd}.%i.log
```

`exception.log`도 같다.

### 굴러간 옛 파일은 아무도 안 지운다 — 배포 후 수동 정리 대상이다

패턴이 바뀌면 **배포 이전에 굴러간 파일들이 새 패턴에 안 맞는다.**

```
배포 전에 생긴 것   application.2026-09-13.log
배포 후에 생기는 것  application.2026-09-14.0.log
```

logback의 `maxHistory`와 `totalSizeCap` 청소는 **자기 `fileNamePattern`에 맞는 파일만** 본다. 옛
이름 파일은 청소 대상에서 빠져 영구히 남는다. 로그 디렉터리 실측이 2.2MB라 당장 문제는 아니지만,
상한을 거는 작업에 상한 밖으로 새는 파일을 남겨두면 앞뒤가 안 맞는다.

**코드로 해결할 수 있는 것이 아니다. 배포 후 확인 항목에 넣는다.** 구현 세션이 할 일은 없고,
PR 설명에 "배포 후 `${LOG_DIR}`의 옛 패턴 파일을 한 번 지워야 한다"를 적는 것까지다.

### 현재 파일(`logs/*.log`)은 그대로 이어 쓴다

`<file>` 경로는 이 단계에서 바뀌지 않는다. `logs/application.log`와 `logs/exception.log` 그대로다.
7-1에서 바뀌는 `ESCALATION_FILE` → `EXCEPTION_FILE`은 XML 안에서만 쓰는 **appender 이름**이라 파일
경로와 무관하다.

파일 이름을 실제로 바꿨을 때는 소유권 문제가 났던 적이 있다. `infra/docker-compose.yml`이
`user: "${DEPLOY_UID}:${DEPLOY_GID}"`로 컨테이너 사용자를 고정하는데(`9c9be99`), 그 이전에 root로
돌던 시절에 만들어진 파일이 남아 있으면 새 파일을 만들 때 걸린다. 이번에는 새 파일을 만들지
않으므로 해당 없다.

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
  잡는다. `<logger name="ESCALATION">`이 사라졌다는 것도 같이 확인한다.

  **로드 중 오류가 났는지도 단언한다.** logback은 설정에 문제가 있어도 예외를 던지지 않고
  `StatusManager`에 쌓아둔 채 넘어간다. 7-3의 `%i` 누락이 바로 그런 종류의 오류라, appender 존재만
  확인하면 조용히 통과할 수 있다. `context.getStatusManager()`에 `ERROR` 레벨 항목이 없음을 함께
  단언한다

### 배포 후 사용자가 확인하는 것

실제로 파일이 써지는지는 자동 테스트로 확인할 수 없다. 배포 후 확인 항목으로 남긴다.

- 예외가 붙은 로그가 `application.log`와 `exception.log` 양쪽에 들어가는지
- 예외가 없는 INFO 로그가 `exception.log`에 들어가지 **않는지**
- `BusinessException` 계열이 실제로 던져지는 요청을 한 번 보내 `exception.log`에 남는지. 이게 이번
  변경의 핵심이라 반드시 확인한다. **예시로 "존재하지 않는 마켓 파라미터"를 쓰면 안 된다** —
  그건 `MethodArgumentTypeMismatchException`이라 `isSpringHandledException`에 걸려 다시 던져지고
  필터에 안 잡힌다. 없는 카테고리 id 조회(`MarketMapCategoryService`의 `NotFoundException`)처럼
  핸들러가 직접 잡아 `log.error(e.createLogMessage(), e)`로 찍는 경로를 쓴다
- **`${LOG_DIR}`에서 옛 패턴으로 굴러간 파일을 지운다.** 7-3 참고. `application.2026-09-13.log`처럼
  `%i`가 없는 이름들이다. 새 정책의 청소 대상에서 빠져 영구히 남으므로 한 번 손으로 지운다.
  현재 쓰이는 `application.log`와 `exception.log`는 **지우지 않는다** — 이어서 쓰는 파일이다
- 롤링 정책 변경 후 앱이 정상 기동하는지

## PR 설명에 적을 것

- 필터 클래스를 어디에 뒀고 왜 거기인지
- 배포 후 `exception.log`에 무엇이 새로 들어오게 되는지. 운영자가 파일을 열었을 때 내용이 달라진다
- 용량 상한 값과 근거
- **배포 후 `${LOG_DIR}`의 옛 패턴 파일을 한 번 지워야 한다는 것.** 7-3 참고. 코드로 해결되지 않아
  사람이 해야 하는 일이라 PR 설명에 남긴다

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
