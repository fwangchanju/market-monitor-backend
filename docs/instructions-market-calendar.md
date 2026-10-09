# 거래일 시간표 적용 지시서

## 합의와 작업 경계

토스 국내 시장 시간표를 날짜별로 저장하고 수집 실행 조건, 종가 기준, 삭제 배치와 지도 리포트 시각에 적용한다. 키움 시세 수집 내용과 프론트는 변경하지 않는다. 공공데이터 휴일 조회, 키움 장 상태 대체 판단, 해외 API 연동, 관심종목 백필과 비활성 수집기의 재활성화는 이번 범위에서 제외한다. 평일 07시 종목 정보 동기화도 유지한다.

사용자는 본 에이전트가 검토를 맡고 구현 담당 네 명을 gpt-6.1-sol medium으로 구성하도록 지정했다. 동시 실행 제한상 세 명을 먼저 실행하고 자리가 나면 네 번째 담당을 실행한다. 수정은 해당 담당에게 돌려보낸다.

휴장 오판으로 수집을 놓치는 것보다 불필요한 수집을 허용하는 정책을 선택했다. 토스 실패 시 기본 시간을 사용한다. 시간표 테이블에는 API 실행 이력 컬럼을 추가하지 않는다. 세션 시간을 개별 12개 컬럼으로 분리하는 안은 채택하지 않았으며 자바 객체를 JSONB로 매핑한다.

보호 파일 infra/.env와 모든 .env 파일을 읽거나 출력하거나 변경하지 않는다. 검색은 파일 종류를 제한하고 .env 및 .env.*를 제외한다. 전용 검증 프로그램 또는 애플리케이션이 인증만을 위해 실행 중 읽는 예외는 허용되지만 이 작업의 일반 테스트는 실제 API나 텔레그램을 호출하지 않는다. TOSS_CLIENT_ID, TOSS_CLIENT_SECRET은 환경 주입으로만 참조한다.

## 저장 구조와 공통 계약

테이블은 market_calendar이고 country와 date를 복합 유니크 키로 둔다. country는 KR, US, JP, CN enum을 문자열로 저장한다. status는 TRADING_DAY, HOLIDAY, FAILED enum을 문자열로 저장한다. 날짜 컬럼 이름은 date이다. integrated는 nullable JSONB이다. 별도 요일이나 실행 이력 컬럼은 만들지 않는다. 기술 식별자가 기존 엔티티 관례상 필요하면 id를 추가할 수 있다.

domain.stock.entity.MarketCalendar가 country, date, status, integrated를 가진다. IntegratedPeriod는 preMarket, regularMarket, afterMarket을 가지며 각 필드 타입은 TradingPeriod이다. TradingPeriod는 startTime, singlePriceAuctionStartTime, singlePriceAuctionEndTime, endTime을 OffsetDateTime으로 가진다. 각 세션과 시각의 null을 유지한다. 시간대가 포함된 실제 날짜·시각을 JSONB에 보존하고 국내 판단과 스냅샷 경계 변환에는 Asia/Seoul을 명시한다. 값 객체는 entity 하위에 둔다. API 응답 래퍼는 dto 하위에 둔다.

국가 enum은 common.enums.Country, 상태 enum은 domain.stock.enums.MarketCalendarStatus, 변경 이벤트는 common.event.MarketCalendarChangedEvent에 둔다. 공통 이벤트는 domain 타입에 의존하지 않는다.

구현 담당 간 공통 계약은 다음과 같다.

- MarketCalendarService.findByCountryAndDate(Country country, LocalDate date)는 Optional<MarketCalendar>를 반환한다.
- MarketCalendarService.findByCountryAndDateIn(Country country, Collection<LocalDate> dates)는 Map<LocalDate, MarketCalendar>를 반환한다. 빈 날짜 목록은 빈 맵을 반환한다. 파생 repository 메서드로 한 번에 조회한다.
- MarketCalendarTimeService.resolve(LocalDate date)는 KR의 CalendarDayTimes를 반환한다. resolve(LocalDate date, MarketCalendar calendar)도 제공하여 삭제 배치에서 일괄 조회한 행을 재사용한다. null calendar는 기본 시간으로 처리한다.
- CalendarDayTimes는 holiday, collectionStart, collectionEnd, regularMarketEnd, closingWindowStart, closingWindowEnd를 가진다. 시간 값은 국내 LocalDateTime이며 closingWindowEnd는 exclusive이다. HOLIDAY이면 holiday=true이고 시간 값은 null일 수 있다. 이 타입은 service 내부 공용 record로 둔다.
- 정상 저장 후 MarketCalendarChangedEvent(country, date)를 발행한다. 종가 캐시는 AFTER_COMMIT 리스너에서 무효화한다. stock에서 notification 또는 view로 직접 역의존을 추가하지 않는다.

기본 시간은 collect.start-hour 및 collect.end-hour를 사용한다. 종가 기본 구간은 기존 market.close-window-start(15:30), market.after-hours-start(15:40)를 사용한다. 정규장 종료 기본값은 close-window-start이다. country가 확장된 것은 저장 모델의 준비이며 이번에 해외 수집 스케줄이나 추정 시간표를 구현하지 않는다.

## 조회와 저장

토스 공식 문서와 스키마를 확인한다. https://openapi.tossinvest.com/openapi-docs/overview.md 및 https://openapi.tossinvest.com/openapi-docs/latest/openapi.json 이 기준이다. POST /oauth2/token Client Credentials 이후 GET /api/v1/market-calendar/KR?date=YYYY-MM-DD를 사용한다. 실제 응답은 result 안에 today, previousBusinessDay, nextBusinessDay가 있는 구조로 알려져 있으므로 공식 문서와 저장된 비밀 없는 테스트 결과로 확인한다.

토큰은 만료 전에 재사용하고 동시 발급을 막는다. 401이면 캐시 토큰을 무효화한다. 토큰·secret·Authorization·요청 인증 본문·원문 HTTP 예외 응답을 로그나 예외에 포함하지 않는다. 네트워크 타임아웃은 유한하게 둔다. 다른 인증을 가진 서버 프로세스의 동시 토큰 발급은 이번 범위 밖이며 한 실행 프로세스 기준으로 캐시한다.

별도 시간표 스케줄러는 Asia/Seoul 기준 매일 06:00에 조회한다. 최초 실패한 날짜만 06:05와 06:15에 재시도한다. 재시도 트리거는 저장 상태만 보고 성공 여부를 결정하지 않는다. 이전 조회에서 이미 오늘의 정상 행을 확보했더라도 오늘의 갱신 호출 자체가 실패했으면 재시도할 수 있다. 각 조회 시도는 한 번의 토큰 확보와 달력 요청 흐름이며 한 시도 안의 무제한 재시도는 금지한다.

시작 시 오늘 정상 레코드가 있으면 그대로 사용한다. 없거나 FAILED이면 한 번 조회를 시도한다. 이후 해당 일자의 정해진 재시도 시각이 남아 있으면 재시도한다. 기동 실패 또는 외부 API 실패로 애플리케이션 전체 기동이나 기존 수집을 중단하지 않는다. 오후 기동 실패는 기본 시간으로 계속 수행하며 상시 5분 API 폴링을 추가하지 않는다.

성공 응답의 세 객체를 각 date 기준으로 갱신한다. integrated=null이면 HOLIDAY이고 integrated가 있으면 TRADING_DAY이다. 각 세션은 null을 허용하되 존재하는 세션의 startTime/endTime은 필수이고 시작은 종료보다 빨라야 한다. KR의 모든 존재 시각은 Asia/Seoul로 변환했을 때 그 행의 날짜여야 한다. 단일가 start/end는 모두 null을 허용하며 존재하면 해당 세션 범위 안에 있어야 한다. integrated가 있는데 세 세션이 모두 null이면 잘못된 응답이다. regularMarket 또는 afterMarket이 null이거나 afterMarket.singlePriceAuctionEndTime이 null인 것은 정상 TRADING_DAY로 저장하고 종가 경계만 기본 구간으로 처리한다.

result 래퍼 부재, 전체 객체 부재, date 부재/역직렬화 실패, today.date와 요청 날짜 불일치, 세 객체 날짜 중복 또는 직전/직후 날짜의 순서 오류는 실패이다. today가 없으나 요청 날짜 이전의 previousBusinessDay와 이후의 nextBusinessDay가 유효하게 존재하면 요청 날짜를 HOLIDAY로 저장한다. 전체 빈 응답을 휴장으로 취급하지 않는다. 실패 시에는 직전·직후 거래일을 추정하지 않고 요청 날짜만 다룬다. 공식 공개 스키마를 2026-10-09에 인증 없이 직접 조회해 토큰 form-urlencoded, literal /api/v1/market-calendar/KR, result 래퍼, 존재 세션의 필수 start/end와 nullable 단일가 필드를 확인했다. D:/git/market-monitor/test/toss/토큰발급1_국내장운영정보6.txt의 공개 응답을 특수일 테스트 fixture 참고로 사용한다.

행 없음 + 실패는 FAILED와 integrated=null로 insert한다. 기존 FAILED + 성공/실패는 이번 결과로 갱신한다. 기존 TRADING_DAY/HOLIDAY + 성공은 과거 날짜를 포함해 갱신한다. 기존 TRADING_DAY/HOLIDAY + 실패는 행을 유지하고 실패를 로그에 남긴다. 성공 응답 저장 중 예외가 나면 정상 데이터 일부가 남지 않도록 트랜잭션을 분리하고 실패 저장을 별도 트랜잭션으로 시도한다. DB 자체가 실패하면 FAILED 저장도 불가능할 수 있으므로 로그로 남기고 기본 시간 정책을 유지한다.

## 실행 시간과 발송

기존 수집 cron인 월~금 08~20시 매 5분을 유지한다. 실제 수집 판단은 별도 서비스로 분리한다. TRADING_DAY는 존재하는 세션 중 가장 이른 startTime부터 가장 늦은 endTime까지 수집한다. 끝 시각은 inclusive로 두어 기존 20:00 수집을 유지한다. HOLIDAY는 수집과 데이터 리포트를 모두 생략한다. FAILED, 행 없음, 조회 오류는 기본 평일 08:00~20:00 시간을 적용하고 원인을 로그로 남긴다.

프리마켓 null인 2026-01-02 및 2025-11-13은 10:00부터 수집하고 20:00까지 수집한다. 정상 날 08:00~20:00이며 15:30~15:40 또는 16:30~16:40 구간의 5분 수집도 유지한다. 단일가 경계를 세션 전체 start/end에 대입하지 않는다.

수집을 생략하는 tick에서도 마지막 섹터 리포트 20:10 같은 발송은 별도로 판단한다. 오래된 다른 날짜 데이터를 오늘 데이터로 보내지 않는다. lastIndexContributionSuccess와 실제 마지막 수집 시각은 날짜를 구분하여 관리한다. MarketMapAlbumReportSender는 캡션에만 dataTime을 사용하고 화면 캡처는 최신 데이터이므로 발송 전 기존 공통 최신 스냅샷 조회를 사용해 오늘의 공통 데이터가 존재하는지 확인한다. 오늘 데이터 없이 첫 수집이 실패하면 전날 지도 이미지는 보내지 않는다. 오늘 데이터가 있으면 기존 지도 수집 실패시 발송 정책은 유지한다.

기존 섹터 리포트 발송 주기 및 처음 두 지도 리포트 08:15, 09:15의 수집 가능 시간 조건을 유지한다. map-send-times의 마지막 원소만 regularMarket.endTime으로 대체한다. 시간표가 없으면 마지막 지도 리포트는 기존 15:30이다. 지정 시각에 수집한 뒤 같은 호출에서 발송한다. 정규 종료가 5분 격자에 맞지 않으면 종료 이후 첫 수집 cron tick에 마지막 지도를 발송하며 이를 로그에 남긴다. 종료가 고정 cron 범위를 벗어나면 범위를 확장하지 않고 로그를 남긴다. 별도 동적 cron은 만들지 않는다.

## 종가·캐시·삭제

날짜별 종가 구간은 regularMarket.endTime 이상, afterMarket.singlePriceAuctionEndTime 미만이다. afterMarket.startTime은 정규 종료와 같아서 끝 경계로 사용할 수 없다. 종가 구간이 없거나 사용할 수 없으면 기존 [15:30, 15:40)를 사용한다. HOLIDAY는 종가 없음으로 처리하고 그날 데이터가 있으면 보존한다.

ClosingPriceCacheService, ClosingPriceReader 및 삭제 배치는 같은 시간 해석 서비스를 사용한다. MarketMapQueryService의 백엔드 시간외 등락률 시작 판단도 snapshotTime의 날짜에 해당하는 closingWindowEnd를 사용한다. 프론트 표시는 이번에 수정하지 않는다.

삭제 cutoff와 10일 보존 규칙은 유지한다. 삭제 대상의 각 날짜와 KOSPI/KOSDAQ별로 종가 구간 latest를 남긴다. 특정 날짜·마켓에 종가 구간 후보가 없으면 그 날짜·마켓의 모든 데이터를 보존하고 다른 정상 그룹은 정리한다. 전체 보존 목록이 비어도 전체 삭제가 발생하면 안 된다. QueryDSL로 후보 (market, snapshotTime)와 그룹을 조회하고 행 데이터 전체를 메모리에 가져오지 않는다. 시간표는 대상 날짜를 모아서 한 번에 조회한다. 삭제 predicate는 처리 가능한 날짜·마켓만 대상으로 한다.

종가 캐시는 빈 결과를 영구 고정하지 않는다. 시간표 변경 이벤트는 트랜잭션 커밋 후 현재와 과거 날짜 캐시에 반영한다. 종가 구간이 끝나기 전에 15:30/16:30 데이터로 만들어진 캐시는 뒤의 15:35/16:35 latest를 놓치지 않도록 처리한다. 시간표 복구 이후 기본 구간으로 캐시한 값을 재계산해야 한다.

## 검증과 전달

DB·외부 API 없이 단위 테스트한다. 정상 날, 휴장, 토요일, 연초 개장, 수능일, 세션 null, start/end 경계, FAILED/없음/조회 오류 기본 처리, 20:10 섹터 리포트, 두 고정 지도 발송 유지와 마지막 시각 이동을 검증한다. 토큰 재사용·만료·인증 실패와 응답 JSON/OffsetDateTime 왕복 및 성공 행 실패 보호, 요청 날짜만 FAILED 기록, 재시도 후 성공·최종 실패를 검증한다. 종가 구간 후보의 끝 exclusive, 날짜별 다른 시간, 종가 없는 그룹 삭제 보류, 캐시 복구와 종가 구간 내 latest 갱신을 검증한다.

compileJava compileTestJava, test(manual 제외), spotlessApply 후 spotlessCheck를 모두 통과시킨다. 공용 워크스페이스에서는 담당들이 동시에 Gradle/spotless를 실행하지 않고 검증 담당 한 명에게 실행을 모은다. 구현 담당은 각자 의미 있는 단위 테스트를 작성한다.

Flyway V9 새 테이블 스크립트는 기능 코드와 별도 PR로 준비한다. 지시서는 독립 검토 후 먼저 PR로 올려 병합한다. 마이그레이션의 운영 적용은 사용자 명시 승인이 필요하므로 자동 실행하지 않는다. Flyway 자동 적용을 피하도록 V9 포함 배포 및 운영 프로필 기동도 승인 전 실행하지 않는다. 승인 후에는 기존 앱에 V9만 포함한 버전을 배포해 스키마를 선적용한 뒤 기능을 병합·배포한다. 기능 코드는 로컬에서 구현·검증해 검토 가능한 별도 PR로 준비하되 스키마가 운영에 적용되기 전에는 기능 병합·배포를 진행하지 않는다. 실제 마이그레이션 및 배포를 수행하지 않았다는 사실과 저장소의 단계 순서 대비 로컬 준비 차이를 PR 설명에 남긴다.

PR/완료 보고에는 수정 동작, 검증 결과, 필요한 TOSS_CLIENT_ID/TOSS_CLIENT_SECRET 환경 주입 및 허용 IP 등록, 스키마 선적용 절차를 적는다. 인증 설정은 기존 env 파일에 쓰지 않고 application.properties에서 환경 변수를 참조한다. 공개 문서에 비밀 값을 적지 않는다. 작업 지시서는 완료 후 제거한다. docs/decisions.md와 docs/backlog.md는 직접 수정하지 않는다.
