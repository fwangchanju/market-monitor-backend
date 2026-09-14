# 백엔드 정비 작업 지시서

> **임시 문서다. 마지막 단계까지 끝나면 이 파일을 삭제한다.**
>
> **코드 작업은 전부 끝났다.** 남은 것은 문서 마무리 하나뿐이고 설계·문서 세션이 수행한다.
> 구현 세션이 이 파일에서 읽을 것은 아래 「절대 건드리지 말 것」뿐이다.

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
> | 7 예외 로그 정리 (+ 스냅샷 보존 기간 단축) | 완료 (PR #107) |
> | 마지막 문서 마무리 | |
>
> **완료된 단계의 본문은 지웠다.** 다시 할 일이 없는데 읽을 양만 늘리기 때문이다. 각 단계가 무엇을
> 했고 왜 그렇게 했는지는 위 표의 PR 설명과 git 히스토리에 있다.
>
> 작업 규칙·완료 기준·커밋 규칙은 처음부터 `docs/rules/process.md`와 같았으므로 여기서도 지웠다.
> 역할 표는 `CLAUDE.md`에 있다.

---

## 절대 건드리지 말 것

**아래는 "죽은 코드"가 아니다. 의도적으로 남긴 것이다. 삭제하거나 "정리"하지 마라.**

| 대상 | 위치 | 살아날 시점 |
|---|---|---|
| 주석 처리된 메서드 본문 4개 | `MarketQueryService` | 관심종목 구조 정리 |
| 주석 처리된 스케줄 메서드 | `CollectionScheduler.collectMarketDataHourly` 등 | 관심종목 구조 정리 |
| 주석 처리된 startup 단계 | `StartupRunner.run()` | 관심종목 구조 정리 |
| `ImageStitcher` 클래스 | `domain/notification/service/` | 이미지 합성이 다시 필요해질 때 |
| `okhttp` / `okhttp-urlconnection` 의존성 | `build.gradle` | `domain/krx`가 다시 돌 때 |
| `domain/krx` 전체 | — | KRX 직접 수집이 다시 필요해질 때 |
| `CollectionChecker`, `CollectionScheduler.isHoliday()` | — | 데이터 지연 감지 |

배경은 `docs/backlog.md`의 「관심종목(WatchStock) 구조 정리」와 「데이터 지연 감지」에 있다.

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

# 마지막 단계 — 문서 마무리

**설계·문서 세션이 수행한다.** 구현 세션은 관여하지 않는다.

- `docs/rules/style.md` **전면 재검토** — 225줄 중 상당수가 사용자가 승인한 적 없는 규칙이다.
  1A~4단계에서 실제로 바뀐 내용을 반영하고, 코드와 어긋나거나 근거가 약한 항목을 정리한다.
  판단이 애매한 항목은 목록으로 뽑아 사용자 확인을 받는다
- `docs/rules/testing.md` 확정 — 실제 작성한 테스트 반영
- `docs/architecture.md` 갱신
- `docs/decisions.md` / `docs/backlog.md` 갱신 — 각 PR에서 나온 판단 회수
- **위 「절대 건드리지 말 것」을 다른 문서로 옮긴다.** 이 파일이 사라지면 같이 사라지는데, 여기
  적힌 것 중 `ImageStitcher` · okhttp 의존성 · `domain/krx`는 다른 어느 문서에도 없다. 옮기지 않으면
  다음 정리 작업에서 "안 쓰는 코드"로 지워진다
- **`docs/work-plan.md`(이 파일) 삭제**

`docs/operations.md`는 7단계에서 갱신했다(로그 파일 구조와 장애 시 확인 순서). 1A로 바뀐 배포·롤백
절차는 3단계에서 반영했다.
