package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.enums.Market;
import dev.eolmae.marketry.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketry.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.MarketSnapshotTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
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
