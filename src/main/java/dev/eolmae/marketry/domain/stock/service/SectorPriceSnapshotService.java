package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.MarketSnapshotTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final MarketCalendarService marketCalendarService;
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

    /** 종가가 있는 날짜·마켓만 정리하고, 해당 구간 latest의 모든 종목 행은 보존한다. */
    @Transactional
    public void cleanupSnapshotsBefore(LocalDateTime cutoff) {
        List<MarketSnapshotTime> candidates = sectorPriceSnapshotRepository.findMarketSnapshotTimesBefore(cutoff);
        if (candidates.isEmpty()) {
            return;
        }
        List<LocalDate> dates = candidates.stream()
                .map(candidate -> candidate.snapshotTime().toLocalDate())
                .distinct()
                .toList();
        Map<LocalDate, MarketCalendar> calendars;
        try {
            calendars = marketCalendarService.findByCountryAndDateIn(Country.KR, dates);
        } catch (Exception exception) {
            log.warn("[시간표일괄조회실패] 기본 종가 구간 적용 | context : {}", cutoff);
            calendars = Map.of();
        }
        Map<LocalDate, CalendarDayTimes> timesByDate = new HashMap<>();
        // 일괄 조회한 행으로 해석하여 날짜별 DB 조회를 반복하지 않는다.
        for (LocalDate date : dates) {
            timesByDate.put(date, marketCalendarTimeService.resolve(date, calendars.get(date)));
        }
        List<MarketSnapshotTime> retained = selectRetainedSnapshotTimes(candidates, timesByDate);
        if (retained.isEmpty()) {
            log.warn("[섹터가격스냅샷정리] 종가 후보가 없어 삭제 보류 | context : {}", cutoff);
            return;
        }
        long deletedCount = sectorPriceSnapshotRepository.deleteSnapshotsBefore(cutoff, retained);
        log.info("[섹터가격스냅샷정리] 삭제완료 | 삭제건수:{}", deletedCount);
    }

    static List<MarketSnapshotTime> selectRetainedSnapshotTimes(
            List<MarketSnapshotTime> candidates, Map<LocalDate, CalendarDayTimes> timesByDate) {
        record GroupKey(Market market, LocalDate date) {}
        return candidates.stream()
                .filter(candidate -> {
                    CalendarDayTimes times =
                            timesByDate.get(candidate.snapshotTime().toLocalDate());
                    return times.holiday() == false
                            && candidate.snapshotTime().isBefore(times.closingWindowStart()) == false
                            && candidate.snapshotTime().isBefore(times.closingWindowEnd());
                })
                .collect(Collectors.groupingBy(candidate -> new GroupKey(
                        candidate.market(), candidate.snapshotTime().toLocalDate())))
                .values()
                .stream()
                .map(group -> group.stream()
                        .max(Comparator.comparing(MarketSnapshotTime::snapshotTime))
                        .orElseThrow())
                .toList();
    }
}
