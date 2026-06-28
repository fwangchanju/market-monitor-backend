package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.DateTimePattern;
import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.common.util.DateParser;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.ShortSellingTrendRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.ShortSellingTrendResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.ShortSellingDailyHistory;
import dev.eolmae.marketmonitor.domain.stock.repository.ShortSellingDailyHistoryRepository;
import dev.eolmae.marketmonitor.domain.stock.service.WatchStockCacheService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ka10014: 공매도추이요청
// ka10014는 장 종료 후 확정되는 일별 데이터만 제공 — 장중 실시간 없음
// 스케줄러: 19:00 1회, strt_dt=today → short_selling_daily 적재
// 백필: strt_dt=today-60 → short_selling_daily 전체 적재
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortSellingTrendCollector {

    private static final String TM_TP_DAILY = "2";
    private static final int BACKFILL_DAYS = 60;

    private final KiwoomApiClient kiwoomApiClient;
    private final ShortSellingDailyHistoryRepository shortSellingRepository;
    private final WatchStockCacheService watchStockCacheService;

    /** 스케줄러 호출 — 당일 확정 데이터 적재 (19:00 이후) */
    @Transactional
    public void collect(LocalDateTime snapshotTime) {
        LocalDate snapshotDate = snapshotTime.toLocalDate();
        List<String> stockCodes = watchStockCacheService.getCache();
        for (String stockCode : stockCodes) {
            try {
                collectForStock(stockCode, snapshotDate);
            } catch (Exception e) {
                log.error("공매도 수집 실패: stockCode={}", stockCode, e);
            }
        }
    }

    /** 관심종목 신규 등록 시 백필 — today-60일부터 수집, 비동기 호출 */
    @Transactional
    public void backfill(String stockCode) {
        LocalDate today = LocalDate.now(Zone.KST.zoneId());
        collectForStock(stockCode, today.minusDays(BACKFILL_DAYS), today);
        log.info("공매도 백필 완료: stockCode={}", stockCode);
    }

    private void collectForStock(String stockCode, LocalDate to) {
        collectForStock(stockCode, to, to);
    }

    private void collectForStock(String stockCode, LocalDate from, LocalDate to) {
        var dateFormatter = DateTimePattern.DATE.formatter();
        var request = new ShortSellingTrendRequest(
                stockCode, TM_TP_DAILY, from.format(dateFormatter), to.format(dateFormatter));
        ShortSellingTrendResponse response = kiwoomApiClient.post(request, ShortSellingTrendResponse.class);

        if (ObjectUtils.isEmpty(response.ticks())) {
            log.debug("공매도 데이터 없음: stockCode={}", stockCode);
            return;
        }

        for (ShortSellingTrendResponse.ShortTick tick : response.ticks()) {
            if (StringUtils.isBlank(tick.dt())) {
                continue;
            }
            LocalDate tradeDate = DateParser.parseDate(tick.dt());
            if (tradeDate == null) {
                continue;
            }

            if (shortSellingRepository.existsByStockCodeAndTradeDate(stockCode, tradeDate)) {
                continue;
            }

            shortSellingRepository.save(toEntity(stockCode, tradeDate, tick));
        }

        log.debug("공매도 수집 완료: stockCode={}", stockCode);
    }

    private static ShortSellingDailyHistory toEntity(
            String stockCode, LocalDate tradeDate, ShortSellingTrendResponse.ShortTick tick) {
        BigDecimal closePrice = KiwoomValueParser.parseBigDecimal(tick.closePric()).abs();
        BigDecimal priceChange = KiwoomValueParser.parseBigDecimal(tick.predPre());
        BigDecimal changeRate = KiwoomValueParser.parseBigDecimal(tick.fluRt());
        long tradingVolume = KiwoomValueParser.parseLong(tick.trdeQty());
        long shortVolume = KiwoomValueParser.parseLong(tick.shrtsQty());
        long cumulativeShortVolume = KiwoomValueParser.parseLong(tick.ovrShrtsQty());
        BigDecimal shortRatio = KiwoomValueParser.parseBigDecimal(tick.trdeWght());
        BigDecimal shortAmount = KiwoomValueParser.parseBigDecimal(tick.shrtsTrdePrica());
        BigDecimal shortAvgPrice = KiwoomValueParser.parseBigDecimal(tick.shrtsAvgPric());

        return ShortSellingDailyHistory.create(
                stockCode,
                tradeDate,
                closePrice,
                priceChange,
                changeRate,
                tradingVolume,
                shortVolume,
                cumulativeShortVolume,
                shortRatio,
                shortAmount,
                shortAvgPrice);
    }
}
