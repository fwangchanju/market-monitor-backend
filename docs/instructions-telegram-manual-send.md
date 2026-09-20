# 지시서 — 텔레그램 수동 발송을 스케줄러와 같은 진입점으로 묶는다

이 파일 하나만 읽고 작업할 수 있게 썼다. 근거가 필요하면 인용한 코드를 직접 열어 확인하면 된다.
main에서 새 브랜치로 시작한다.

---

## 1. 무엇이 문제인가

배포 뒤 텔레그램 발송을 눈으로 확인하는 수동 테스트가 둘 있다.

| 테스트 | 부르는 것 |
|---|---|
| `TelegramReportCycleManualTest` | `sectorTelegramReportSender.send(dataTime, true)`, `marketMapAlbumReportSender.send(dataTime, true)` |
| `MarketMapTelegramReportSenderManualTest` | 구버전 `MarketMapTelegramReportSender.send(LocalDateTime.now(), KOSPI)` |

둘 다 `CollectionScheduler.collectMarketData()`가 발송기를 부르는 줄을 **베껴 쓴다.** 그래서 발송기
시그니처나 호출 순서가 바뀔 때마다 테스트가 따로 깨지고, 지난번엔 돌던 게 이번엔 안 돈다.
당장 다음 작업(카테고리 집계 테이블 제거)에서 `sectorAvailable` 인자가 사라진다.

구버전 테스트는 `LocalDateTime.now()`를 넘겨서 장 마감 뒤나 주말에 돌리면 그 시각 스냅샷이 없어
캡션 랭킹이 빈 채로 나간다.

그리고 올스탁 지도 한 장을 보내는 경로가 없다. 두 발송기 모두 "지도는 마켓별로 각각 캡처한다"라
`ALL_STOCK`을 넘겨도 코스피·코스닥 두 장으로 펼친다.

---

## 2. 확정된 결정

### 결정 1 — 발송 진입점을 `TelegramReportDispatcher` 하나로 뽑는다

스케줄러 안에 있는 발송 호출을 새 컴포넌트로 옮긴다. 스케줄러는 시각 판정만 하고 이 메서드를
부르고, 수동 테스트도 같은 메서드를 부른다. 발송기 시그니처가 바뀌든 순서가 바뀌든 dispatcher
안에서만 바뀌고 테스트는 한 줄이라 손댈 게 없다.

```java
// domain/notification/service/TelegramReportDispatcher
public void sendSector(LocalDateTime dataTime, boolean sectorAvailable)
public void sendMap(LocalDateTime dataTime, boolean sectorAvailable)
public void sendAll(LocalDateTime dataTime, boolean sectorAvailable)   // 섹터 → 맵 순. 스케줄러가 둘 다 due일 때와 같다
public void sendMapSinglePage(LocalDateTime dataTime, MarketQuery query, boolean sectorAvailable)
```

`sectorAvailable`은 지금 스케줄러가 `lastChangeRateSuccess`로 넘기는 값이다. 집계 테이블 제거
PR에서 이 인자가 사라지면 dispatcher 시그니처에서만 빠진다. 수동 테스트는 항상 `true`를 넘긴다.

지금 스케줄러의 발송 부분은 이렇게 된다.

```java
if (telegramSendSchedule.due(snapshotTime, shouldCollect)) {
    if (!lastIndexContributionSuccess) {
        run("데이터수집실패알림", () -> telegramCollectionFailureNotifier.notify(dataTime));
    } else {
        run("섹터텔레그램발송", () -> telegramReportDispatcher.sendSector(dataTime, lastChangeRateSuccess));
    }
}
if (telegramSendSchedule.dueForMap(snapshotTime, shouldCollect)) {
    run("맵텔레그램발송", () -> telegramReportDispatcher.sendMap(dataTime, lastChangeRateSuccess));
}
```

`run()` 단위와 에스컬레이션은 그대로다. dispatcher는 발송기를 부르는 얇은 층이고 판정 로직을 갖지
않는다.

### 결정 2 — 올스탁 한 장은 테스트 전용 메서드로 둔다

`sendMapSinglePage(dataTime, query, sectorAvailable)`는 `/map/{query 세그먼트}` 한 페이지를 캡처해서
한 장으로 보낸다. `ALL_STOCK`이면 `/map/allstock`이다. 캡션은 `sendMap`과 같은
`getMergedTopCategoryRanking(query, …)`로 만든다. 스케줄러는 이 메서드를 부르지 않는다. 스케줄러의
맵 발송은 지금처럼 코스피·코스닥 두 장 앨범 그대로다.

`MarketMapAlbumReportSender`에 한 페이지를 캡처하는 경로를 추가한다. `sendImages`가 이미 한 장이면
`sendPhoto`로 내려가므로 발송 쪽은 그대로 쓴다. 캡처 경로의 마켓 세그먼트(`kospi`/`kosdaq`/
`allstock`)는 지금 `market.name().toLowerCase()`로 두 발송기에 흩어져 있는데, `ALL_STOCK`은
`all_stock`이 되어 프론트 라우트(`/map/allstock`, PR #59)와 안 맞는다. 세그먼트 변환을 한 곳에
두고 둘 다 그걸 쓴다. 어디에 둘지(`Market`/`MarketQuery`의 메서드, `RenderTarget`의 헬퍼)는
구현자가 정한다.

### 결정 3 — 수동 테스트는 dispatcher만 부른다

`TelegramReportCycleManualTest`를 이렇게 바꾼다. 넷 다 `dataTime`은 지금처럼
`findLatestCommonSnapshotTime(KOSPI, KOSDAQ)`이다. 장 마감 뒤든 주말이든 마지막 수집 시각으로
나간다.

```java
@Test void sendsSector()        { dispatcher.sendSector(findDataTime(), true); }
@Test void sendsMap()           { dispatcher.sendMap(findDataTime(), true); }
@Test void sendsAll()           { dispatcher.sendAll(findDataTime(), true); }
@Test void sendsMapAllStockOnePage() { dispatcher.sendMapSinglePage(findDataTime(), MarketQuery.ALL_STOCK, true); }
```

`MarketMapTelegramReportSenderManualTest`는 지운다. 역할이 위 넷에 덮인다. 구버전 발송기
`MarketMapTelegramReportSender` 자체는 비활성 `collectMarketDataHourly`가 참조하고 있어 이번에
안 건드린다.

### 결정 4 — 수동 테스트가 도는 동안 스케줄러가 같이 돌면 안 된다

`@SpringBootTest`는 앱을 통째로 띄우므로 `@EnableScheduling`도 살아난다. 캡처가 몇 초 걸리는 사이
cron 시각(매 5분 정각)이 걸리면 수집과 발송이 한 번 더 나간다. `@EnableScheduling`을
`MarketMonitorApplication`에서 별도 설정 클래스로 옮기고 프로퍼티로 끌 수 있게 한다.

```java
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {}
```

수동 테스트는 `@SpringBootTest(properties = "scheduling.enabled=false")`. `@EnableAsync`는 그대로
둔다.

---

## 3. 건드리지 않는 것

- 발송기 내부 로직. `SectorTelegramReportSender.send`, `MarketMapAlbumReportSender.send`의 판정과
  캡션은 그대로다. dispatcher는 그걸 부를 뿐이다
- `TelegramSendSchedule`의 시각 판정
- `TelegramCollectionFailureNotifier`
- 스크립트 파일. 서버에서 쓰는 실행 스크립트는 저장소에 넣지 않는다. 실행 명령은 테스트 Javadoc에
  적는다(`docs/rules/testing.md`)

---

## 4. 작업

1. `SchedulingConfig` 분리 + `scheduling.enabled` 프로퍼티 (결정 4)
2. 마켓 세그먼트 변환을 한 곳으로 (결정 2)
3. `MarketMapAlbumReportSender`에 한 페이지 캡처 경로 추가 (결정 2)
4. `TelegramReportDispatcher` 추가, `CollectionScheduler`가 그걸 부르도록 (결정 1)
5. `TelegramReportCycleManualTest` 재작성, `MarketMapTelegramReportSenderManualTest` 삭제 (결정 3)
6. 발송기 단위 테스트(`SectorTelegramReportSenderTest`, `MarketMapAlbumReportSenderTest`)는 그대로
   통과해야 한다. dispatcher는 발송기를 mock으로 두고 "어느 메서드를 어떤 인자로 부르는가"만 검증하는
   테스트를 하나 둔다

---

## 5. 검증

단위 테스트는 `./gradlew test`. 수동 발송은 서버에서 사용자가 돌린다. 아래 스크립트를 테스트
Javadoc의 실행 명령에 그대로 적는다. `--tests` 필터만 다르다.

```bash
cd "$HOME/repo/market-monitor-backend"
git fetch origin main && git checkout main && git pull
mkdir -p "$HOME/.gradle-cache"

# 지도만 / 섹터만 / 둘 다 / 올스탁 한 장 — 넷 중 하나를 고른다
TEST='*.TelegramReportCycleManualTest.sendsMap'
# TEST='*.TelegramReportCycleManualTest.sendsSector'
# TEST='*.TelegramReportCycleManualTest.sendsAll'
# TEST='*.TelegramReportCycleManualTest.sendsMapAllStockOnePage'

docker run --rm \
  --network host \
  --env-file "$HOME/env/market-monitor.env" \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:postgresql://localhost:5433/market_monitor_db \
  -v "$HOME/repo/market-monitor-backend:/workspace" \
  -v "$HOME/.gradle-cache:/root/.gradle" \
  -w /workspace \
  eclipse-temurin:21-jdk-jammy \
  bash -c "chmod +x gradlew && ./gradlew manualTest --tests '$TEST' -i --no-daemon"
```

확인할 것

1. `sendsMap`: 코스피·코스닥 두 장 앨범 + 병합 랭킹 캡션. 스케줄러 15:30 발송과 같은 모양
2. `sendsSector`: 마켓별 섹터 이미지 한 장 + 캡션 한 개씩. 08:10 첫 발송처럼 before가 없으면
   등락률 폴백 캡션
3. `sendsAll`: 섹터 뒤에 맵. 메시지 순서가 스케줄러가 둘 다 due일 때와 같다
4. `sendsMapAllStockOnePage`: `/map/allstock` 한 장 + 병합 랭킹 캡션. 프론트 PR #59가 배포돼
   있어야 그 라우트가 있다. 그 전이면 `/market-map` 폴백으로 코스피 지도가 찍힌다
5. 테스트가 도는 동안 앱 로그에 "장중 시장 데이터 수집 시작"이 찍히지 않는다(결정 4)
6. 실제 채팅방(`TELEGRAM_CHAT_ID`)으로 나간다. 테스트 발송이면 env 파일의 값을
   `DEVELOPER_CHAT_ID`로 바꿔 돌린다
