package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.MarketSnapshotTime;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.TimeWindow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 시장별 최신 종목 가격 스냅샷 조회. */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SectorPriceSnapshotService {

    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository;
    private final MarketCalendarTimeService marketCalendarTimeService;

    /** markets 전부가 공통으로 가진 최신 스냅샷 시각 — markets가 하나뿐이면 그 마켓의 최신 시각과 같다. */
    public Optional<LocalDateTime> findLatestCommonSnapshotTime(List<Market> markets) {
        return sectorPriceSnapshotRepository.findLatestCommonSnapshotTime(markets);
    }

    public boolean existsSnapshot(Market market, LocalDateTime snapshotTime) {
        return sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(market, snapshotTime);
    }

    public boolean notExistsSnapshot(Market market, LocalDateTime snapshotTime) {
        return !existsSnapshot(market, snapshotTime);
    }

    /** markets를 한 번의 IN 쿼리로 조회해 종목코드 기준으로 합친다 — 종목코드가 마켓 간에 겹치지 않으므로
     * 그대로 하나의 맵으로 합쳐도 안전하다. */
    public Map<String, SectorPriceSnapshot> findPriceByStockCode(List<Market> markets, LocalDateTime snapshotTime) {
        return sectorPriceSnapshotRepository.findByMarketTypeInAndSnapshotTime(markets, snapshotTime).stream()
                .collect(Collectors.toMap(SectorPriceSnapshot::getStockCode, Function.identity()));
    }

    /** market 인자 없이 KOSPI/KOSDAQ 각각 최신 스냅샷 가격을 종목코드 기준으로 합침 — 마켓별로 독립적인
     * "그 마켓의 최신"이라 공통 시각이 아니라 마켓 하나짜리 리스트로 각각 조회한다. */
    public Map<String, SectorPriceSnapshot> findLatestPriceByStockCode() {
        return Arrays.stream(Market.values())
                .flatMap(market -> findLatestCommonSnapshotTime(List.of(market))
                        .map(snapshotTime -> findPriceByStockCode(List.of(market), snapshotTime))
                        .orElse(Map.of())
                        .values()
                        .stream())
                .collect(Collectors.toMap(SectorPriceSnapshot::getStockCode, Function.identity()));
    }

    /**
     * 한 달 안에서 markets 전부가 같은 시각의 종가 스냅샷을 가진 날짜와 그 시각. 종가 스냅샷은 정리 배치와 같은
     * 기준(종가 구간의 마지막 스냅샷)이라, 10일이 지난 날도 같은 시각이 남아 있다. 휴장일·종가 후보가 없는 날,
     * 시장끼리 시각이 엇갈린 날은 뺀다(그 시각으로는 모든 시장의 지도를 그릴 수 없다).
     */
    public Map<LocalDate, LocalDateTime> findClosingSnapshotTimes(List<Market> markets, YearMonth month) {
        List<LocalDate> dates =
                month.atDay(1).datesUntil(month.plusMonths(1).atDay(1)).toList();
        List<TimeWindow> closingWindows = marketCalendarTimeService.resolveAll(dates).values().stream()
                .filter(times -> times.holiday() == false)
                .map(times -> new TimeWindow(times.closingWindowStart(), times.closingWindowEnd()))
                .toList();
        if (closingWindows.isEmpty()) {
            return Map.of();
        }
        Map<LocalDate, List<MarketSnapshotTime>> closingByDate =
                sectorPriceSnapshotRepository.findLatestMarketSnapshotTimesPerDay(closingWindows).stream()
                        .collect(Collectors.groupingBy(
                                closing -> closing.snapshotTime().toLocalDate()));
        Map<LocalDate, LocalDateTime> closingTimes = new TreeMap<>();
        for (Map.Entry<LocalDate, List<MarketSnapshotTime>> entry : closingByDate.entrySet()) {
            Map<Market, LocalDateTime> timeByMarket = entry.getValue().stream()
                    .collect(Collectors.toMap(MarketSnapshotTime::market, MarketSnapshotTime::snapshotTime));
            Set<LocalDateTime> marketTimes =
                    markets.stream().map(timeByMarket::get).collect(Collectors.toSet());
            if (marketTimes.size() == 1 && marketTimes.contains(null) == false) {
                closingTimes.put(entry.getKey(), marketTimes.iterator().next());
            }
        }
        return closingTimes;
    }

    /** 지정 날짜 하루만 정리한다. 휴장과 종가 후보 없는 시장은 보존하며, 실패 날짜 재실행에도 같은 로직을 쓴다. */
    @Transactional
    public void cleanupSnapshotsForDate(LocalDate date) {
        CalendarDayTimes times = marketCalendarTimeService.resolve(date);
        if (times.holiday()) {
            log.info("[섹터가격스냅샷정리] 휴장일 생략 | context : {}", date);
            return;
        }
        List<MarketSnapshotTime> retained = sectorPriceSnapshotRepository.findLatestMarketSnapshotTimesBetween(
                times.closingWindowStart(), times.closingWindowEnd());
        if (retained.isEmpty()) {
            log.warn("[섹터가격스냅샷정리] 종가 후보가 없어 삭제 보류 | context : {}", date);
            return;
        }
        long deletedCount = sectorPriceSnapshotRepository.deleteSnapshotsForDate(date, retained);
        log.info("[섹터가격스냅샷정리] 삭제완료 | context : {}|{}", date, deletedCount);
    }
}
