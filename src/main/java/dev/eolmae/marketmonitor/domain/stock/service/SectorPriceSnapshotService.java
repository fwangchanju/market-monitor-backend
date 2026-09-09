package dev.eolmae.marketmonitor.domain.stock.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.SnapshotRetentionSummary;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

    // collect.*와 무관한 별개 상수 — 장마감 시각이 아니라 "보존할 스냅샷 시각"을 뜻한다.
    private static final LocalTime MARKET_CLOSE_TIME = LocalTime.of(15, 30);
    private static final int RETENTION_LOG_SAMPLE_SIZE = 5;

    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository;

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

    /** cutoff 이전이면서 MARKET_CLOSE_TIME(15:30)이 아닌 스냅샷 정리 — dryRun이면 조회만 하고 로그로 남긴다. */
    @Transactional
    public void cleanupSnapshotsBefore(LocalDateTime cutoff, boolean dryRun) {
        SnapshotRetentionSummary summary = sectorPriceSnapshotRepository.summarizeSnapshotsToDelete(
                cutoff, MARKET_CLOSE_TIME, RETENTION_LOG_SAMPLE_SIZE);
        log.info(
                "[섹터가격스냅샷정리] 대상건수:{} | cutoff이전전체건수:{} | 최소시각:{} | 최대시각:{} | 표본시각:{}",
                summary.targetCount(),
                summary.totalCountBeforeCutoff(),
                summary.minSnapshotTime(),
                summary.maxSnapshotTime(),
                summary.sampleSnapshotTimes());

        if (dryRun) {
            return;
        }

        long deletedCount = sectorPriceSnapshotRepository.deleteSnapshotsBefore(cutoff, MARKET_CLOSE_TIME);
        log.info("[섹터가격스냅샷정리] 삭제완료 | 삭제건수:{}", deletedCount);
    }
}
