package dev.eolmae.marketmonitor.domain.stock.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 시장별 최신 종목 가격 스냅샷 조회. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SectorPriceSnapshotService {

    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository;

    public Optional<LocalDateTime> findLatestSnapshotTime(Market market) {
        return sectorPriceSnapshotRepository
                .findFirstByMarketTypeOrderBySnapshotTimeDesc(market)
                .map(SectorPriceSnapshot::getSnapshotTime);
    }

    public boolean existsSnapshot(Market market, LocalDateTime snapshotTime) {
        return sectorPriceSnapshotRepository.existsByMarketTypeAndSnapshotTime(market, snapshotTime);
    }

    public boolean notExistsSnapshot(Market market, LocalDateTime snapshotTime) {
        return !existsSnapshot(market, snapshotTime);
    }

    public Map<String, SectorPriceSnapshot> findPriceByStockCode(Market market, LocalDateTime snapshotTime) {
        return sectorPriceSnapshotRepository.findByMarketTypeAndSnapshotTime(market, snapshotTime).stream()
                .collect(Collectors.toMap(SectorPriceSnapshot::getStockCode, Function.identity()));
    }

    /** market 인자 없이 KOSPI/KOSDAQ 각각 최신 스냅샷 가격을 종목코드 기준으로 합침 */
    public Map<String, SectorPriceSnapshot> findLatestPriceByStockCode() {
        return Arrays.stream(Market.values())
                .flatMap(market -> findLatestSnapshotTime(market)
                        .map(snapshotTime -> findPriceByStockCode(market, snapshotTime))
                        .orElse(Map.of())
                        .values()
                        .stream())
                .collect(Collectors.toMap(SectorPriceSnapshot::getStockCode, Function.identity()));
    }
}
