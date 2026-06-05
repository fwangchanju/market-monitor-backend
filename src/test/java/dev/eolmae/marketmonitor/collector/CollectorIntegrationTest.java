package dev.eolmae.marketmonitor.collector;

import dev.eolmae.marketmonitor.domain.dashboard.repository.IndexContributionRankingSnapshotRepository;
import dev.eolmae.marketmonitor.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.notification.service.AlertService;
import dev.eolmae.marketmonitor.domain.dashboard.repository.IntradayInvestorRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.dashboard.repository.InvestorTradingSummarySnapshotRepository;
import dev.eolmae.marketmonitor.domain.dashboard.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.domain.dashboard.repository.ProgramTradingRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.history.repository.ProgramTradingHistoryRepository;
import dev.eolmae.marketmonitor.domain.history.repository.ShortSellingDailyHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.StockMasterRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.WatchStockRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 수집기 전체 통합 테스트 — 외부 API 호출 → DB 저장까지 검증
 *
 * 실행 환경:
 *   서버에서 --network backend 옵션으로 실행 (prod PostgreSQL 접근 필요)
 *   KIWOOM_APP_KEY, KIWOOM_SECRET, DB_APP_PASSWD 환경변수 필요
 *   KRX_ID, KRX_PW — order7, order8 (KRX 수집기) 실행 시 필요
 *
 * 실행 명령 예시:
 *   ./gradlew test --tests "*.CollectorIntegrationTest" -i
 *
 * 권장 실행 순서 (각 테스트는 독립 실행 가능, 선행 조건 있는 경우 아래 순서 준수 권장):
 *
 *   Order 1. stockMaster_동기화
 *              → WatchStock 기반 수집기(order6, order7)의 선행 조건
 *
 *   Order 2. marketOverview_수집
 *              → IndexContribution 기여도 연산(order8)의 선행 조건 (전일 지수값 역산)
 *
 *   Order 3. investorTradingSummary_수집    (독립)
 *   Order 4. intradayInvestorRanking_수집   (독립, 장중 실행 권장)
 *   Order 5. programTradingRanking_수집     (독립, 장중 실행 권장)
 *
 *   Order 6. programTrading_장중_수집
 *              → WatchStock 종목 필요. 없으면 테스트 스킵됨.
 *              → 텔레그램 봇 /add <종목코드> 로 종목 추가 후 실행
 *
 *   Order 7. shortSelling_수집
 *              → WatchStock 종목 필요. 없으면 테스트 스킵됨.
 *              → KRX 공매도 데이터는 18:30 이후 확정. 장중 실행 시 당일 데이터 없을 수 있음.
 *
 *   Order 8. indexContributionRanking_수집
 *              → MarketOverviewSnapshot 데이터 필요. 없으면 테스트 스킵됨.
 *              → order2 먼저 실행 권장.
 *
 * 참고:
 *   - 장중(09:00~15:30 KST) 실행 시 실데이터 수집 가능
 *   - 장 외 시간에도 예외 없이 완료되나 snapshot 계열은 0건이 정상
 *   - 수집기는 모두 멱등성 보장 (동일 snapshotTime 중복 실행 시 스킵 또는 upsert)
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("prod")
@TestPropertySource(properties = {
	"spring.test.database.replace=none",
	"spring.datasource.url=jdbc:postgresql://localhost:5433/market_monitor_db"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CollectorIntegrationTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Value("${krx.login-id:}")
	private String krxLoginId;

	@Autowired StockMasterCollector stockMasterCollector;
	@Autowired MarketOverviewCollector marketOverviewCollector;
	@Autowired InvestorTradingSummaryCollector investorTradingSummaryCollector;
	@Autowired IntradayInvestorRankingCollector intradayInvestorRankingCollector;
	@Autowired ProgramTradingRankingCollector programTradingRankingCollector;
	@Autowired ProgramTradingCollector programTradingCollector;
	@Autowired ShortSellingCollector shortSellingCollector;
	@Autowired IndexContributionRankingCollector indexContributionRankingCollector;

	@Autowired StockMasterRepository stockMasterRepository;
	@Autowired MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;
	@Autowired InvestorTradingSummarySnapshotRepository investorTradingSummarySnapshotRepository;
	@Autowired IntradayInvestorRankingSnapshotRepository intradayInvestorRankingSnapshotRepository;
	@Autowired ProgramTradingRankingSnapshotRepository programTradingRankingSnapshotRepository;
	@Autowired ProgramTradingHistoryRepository programTradingHistoryRepository;
	@Autowired ShortSellingDailyHistoryRepository shortSellingDailyHistoryRepository;
	@Autowired IndexContributionRankingSnapshotRepository indexContributionRankingSnapshotRepository;
	@Autowired WatchStockRepository watchStockRepository;
	@Autowired AlertService alertService;

	/** 스케줄러와 동일한 방식으로 snapshotTime 산출 (현재 시각을 1시간 단위로 내림) */
	private LocalDateTime snapshotTime() {
		return LocalDateTime.now(KST).withMinute(0).withSecond(0).withNano(0);
	}

	/** 비거래일(토·일)이면 직전 금요일 날짜를 반환 */
	private LocalDate lastTradingDay() {
		LocalDate today = LocalDate.now(KST);
		return switch (today.getDayOfWeek()) {
			case SATURDAY -> today.minusDays(1);
			case SUNDAY -> today.minusDays(2);
			default -> today;
		};
	}

	@Test
	@Order(1)
	void order1_stockMaster_동기화() {
		// 선행 조건: 없음
		// 검증: 동기화 후 StockMaster 테이블에 코스피+코스닥 종목이 존재하는지 확인
		stockMasterCollector.sync();

		long count = stockMasterRepository.count();
		log.info("[1] StockMaster 동기화 완료 — 총 {}종목", count);
		assertThat(count).as("종목 마스터가 비어 있음 — ka10099 API 응답 확인 필요").isPositive();
	}

	@Test
	@Order(2)
	void order2_marketOverview_수집() {
		// 선행 조건: 없음
		// 검증: KOSPI/KOSDAQ 각 1건씩 총 2건 스냅샷 저장 확인
		//       지수값(indexValue)이 0보다 큰지 확인 (장 외 시간에도 전일 종가로 응답)
		LocalDateTime snapshotTime = snapshotTime();
		marketOverviewCollector.collect(snapshotTime);

		var result = marketOverviewSnapshotRepository.findBySnapshotTimeOrderByMarketTypeAsc(snapshotTime);
		log.info("[2] MarketOverview 수집 완료 — snapshotTime={}, 저장 건수={}", snapshotTime, result.size());
		result.forEach(o -> log.info("    {} | indexValue={} | changeValue={}",
			o.getMarketType(), o.getIndexValue(), o.getChangeValue()));

		assertThat(result).as("MarketOverviewSnapshot가 2건(KOSPI+KOSDAQ)이어야 함").hasSize(2);
		assertThat(result).allSatisfy(o ->
			assertThat(o.getIndexValue()).as("지수값이 0이어서는 안 됨").isNotZero()
		);
	}

	@Test
	@Order(3)
	void order3_investorTradingSummary_수집() {
		// 선행 조건: 없음
		// 검증: 시장(2) × 투자자(13) = 최대 26건 스냅샷 저장 확인
		//       장 외 시간에도 이전 데이터로 채워지므로 항상 0보다 커야 함
		LocalDateTime snapshotTime = snapshotTime();
		investorTradingSummaryCollector.collect(snapshotTime);

		long count = investorTradingSummarySnapshotRepository.count();
		log.info("[3] InvestorTradingSummary 수집 완료 — snapshotTime={}, 저장 건수={}", snapshotTime, count);
		assertThat(count).as("투자자별 매매종합 스냅샷 데이터가 없음").isPositive();
	}

	@Test
	@Order(4)
	void order4_intradayInvestorRanking_수집() {
		// 선행 조건: 없음
		// 검증: 장중에는 순위 스냅샷이 쌓이는지 확인
		//       장 외 시간에는 0건이 정상 (API가 빈 배열 반환)
		LocalDateTime snapshotTime = snapshotTime();
		intradayInvestorRankingCollector.collect(snapshotTime);

		long count = intradayInvestorRankingSnapshotRepository.count();
		log.info("[4] IntradayInvestorRanking 수집 완료 — snapshotTime={}, 총 누적 스냅샷={}건", snapshotTime, count);
		// 장 외 시간에는 0건이 정상이므로 assert 없음 — 로그로 확인
	}

	@Test
	@Order(5)
	void order5_programTradingRanking_수집() {
		// 선행 조건: 없음
		// 검증: 장중에는 순매수 상위 스냅샷이 쌓이는지 확인
		//       장 외 시간에는 0건이 정상
		LocalDateTime snapshotTime = snapshotTime();
		programTradingRankingCollector.collect(snapshotTime);

		long count = programTradingRankingSnapshotRepository.count();
		log.info("[5] ProgramTradingRanking 수집 완료 — snapshotTime={}, 총 누적 스냅샷={}건", snapshotTime, count);
	}

	@Test
	@Order(6)
	void order6_programTrading_장중_수집() {
		// 선행 조건: WatchStock에 종목이 1개 이상 등록되어 있어야 함
		//   → 텔레그램 봇 /add <종목코드> 로 등록하거나, order1 실행 후 DB에 직접 insert
		// 검증: 관심종목별 장중 프로그램매매 추이 저장 확인
		long watchStockCount = watchStockRepository.count();
		assumeTrue(watchStockCount > 0,
			"관심종목 없음 — 텔레그램 봇 /add <종목코드> 로 종목 추가 후 재실행");

		LocalDateTime snapshotTime = snapshotTime();
		programTradingCollector.collect(snapshotTime);

		long count = programTradingHistoryRepository.count();
		log.info("[6] ProgramTrading 장중 수집 완료 — snapshotTime={}, 관심종목={}개, 저장 건수={}",
			snapshotTime, watchStockCount, count);
	}

	@Test
	@Order(7)
	void order7_shortSelling_수집() {
		// 선행 조건: WatchStock에 종목이 1개 이상 등록되어 있어야 함
		//   → 텔레그램 봇 /add <종목코드> 로 등록하거나, order1 실행 후 DB에 직접 insert
		// ka10014 기반, KRX 로그인 불필요
		long watchStockCount = watchStockRepository.count();
		assumeTrue(watchStockCount > 0,
			"관심종목 없음 — 텔레그램 봇 /add <종목코드> 로 종목 추가 후 재실행");

		LocalDateTime snapshotTime = snapshotTime();
		shortSellingCollector.collect(snapshotTime);

		long dailyCount = shortSellingDailyHistoryRepository.count();
		log.info("[7] ShortSelling 수집 완료 — snapshotTime={}, 관심종목={}개, 일별={}건",
			snapshotTime, watchStockCount, dailyCount);
	}

	@Test
	@Order(8)
	void order8_indexContributionRanking_수집() {
		// 선행 조건: MarketOverviewSnapshot 데이터 필요 (전일 지수값 역산에 사용)
		//   → order2(marketOverview_수집) 먼저 실행 후 진행 권장
		// 검증: KOSPI/KOSDAQ 각 상위 50종목 스냅샷 저장 확인
		//       장 외 시간에는 당일 데이터 없을 수 있으나 예외 없이 완료되어야 함
		assumeTrue(!krxLoginId.isBlank(), "KRX 로그인 정보 미설정 — KRX_ID/KRX_PW 환경변수 확인 후 재실행");
		long snapshotCount = marketOverviewSnapshotRepository.count();
		assumeTrue(snapshotCount > 0,
			"MarketOverviewSnapshot 데이터 없음 — order2(marketOverview_수집) 먼저 실행");

		LocalDateTime snapshotTime = lastTradingDay().atTime(snapshotTime().toLocalTime());
		indexContributionRankingCollector.collect(snapshotTime);

		long count = indexContributionRankingSnapshotRepository.count();
		log.info("[8] IndexContributionRanking 수집 완료 — snapshotTime={}, 총 누적 스냅샷={}건", snapshotTime, count);
	}

	@Test
	@Order(10)
	void order10_텔레그램_에스컬레이션_알림() {
		// 검증: EscalateException 발생 시 DEVELOPER_CHAT_ID로 텔레그램 메시지가 발송되는지 확인
		alertService.sendEscalation(new EscalateException("[테스트] 에스컬레이션 알림 발송 확인"));
		log.info("[10] 텔레그램 에스컬레이션 알림 발송 완료 — 실제 수신 여부는 텔레그램에서 확인");
	}
}
