package dev.eolmae.marketmonitor.domain.stock.service;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.MarketSnapshotTime;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepositoryCustom.SnapshotRetentionSummary;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
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

    // collect.*와 무관한 별개 상수 — "보존할 스냅샷 시각"의 윈도우다. KRX 정규장은 15:30에 닫히고 NXT
    // 애프터마켓은 15:40에 열려서 그 10분은 두 시장 다 닫혀 있어 가격이 안 바뀐다. 그 안에서 가장 늦은
    // 시각(보통 15:35, 종가 동시호가까지 다 반영되는 시각)을 남긴다 — 15:30을 그대로 박으면 동시호가
    // 결과가 아직 다 반영되지 않은 값을 종가로 오인해 남기게 된다.
    private static final LocalTime RETENTION_WINDOW_START = LocalTime.of(15, 30);
    private static final LocalTime RETENTION_WINDOW_END = LocalTime.of(15, 40);
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

    /** cutoff 이전이면서 그 날짜·마켓의 보존 윈도우([15:30, 15:40)) latest가 아닌 스냅샷 정리 — dryRun이면
     * 조회만 하고 로그로 남긴다. */
    @Transactional
    public void cleanupSnapshotsBefore(LocalDateTime cutoff, boolean dryRun) {
        List<MarketSnapshotTime> candidatesInWindow = sectorPriceSnapshotRepository.findMarketSnapshotTimesInWindow(
                cutoff, RETENTION_WINDOW_START, RETENTION_WINDOW_END);
        List<MarketSnapshotTime> retainedSnapshotTimes =
                selectRetainedSnapshotTimes(candidatesInWindow, RETENTION_WINDOW_START, RETENTION_WINDOW_END);

        SnapshotRetentionSummary summary = sectorPriceSnapshotRepository.summarizeSnapshotsToDelete(
                cutoff, retainedSnapshotTimes, RETENTION_LOG_SAMPLE_SIZE);
        log.info(
                "[섹터가격스냅샷정리] 대상건수:{} | cutoff이전전체건수:{} | 최소시각:{} | 최대시각:{} | 표본시각:{}",
                summary.targetCount(),
                summary.totalCountBeforeCutoff(),
                summary.minSnapshotTime(),
                summary.maxSnapshotTime(),
                summary.sampleSnapshotTimes());
        log.info(
                "[섹터가격스냅샷정리] 보존시각 | 표본:{} | 보존날짜수:{}",
                summary.retainedSampleSnapshotTimes(),
                summary.retainedDateCount());
        log.info(
                "[섹터가격스냅샷정리] 보존할행없음 | 대상:{} | 건수:{}",
                summary.emptyWindowTargets(),
                summary.emptyWindowTargets().size());

        if (dryRun) {
            return;
        }

        long deletedCount = sectorPriceSnapshotRepository.deleteSnapshotsBefore(cutoff, retainedSnapshotTimes);
        log.info("[섹터가격스냅샷정리] 삭제완료 | 삭제건수:{}", deletedCount);
    }

    /** 보존 윈도우 후보를 (마켓, 날짜)로 묶어 각 그룹의 가장 늦은 시각만 남긴다. 윈도우 필터를 SQL(1단계)뿐
     * 아니라 여기서도 다시 거는 이유 — 이 레포는 DB 테스트가 없어 QueryDSL 술어가 뒤집혀 있어도 컴파일과
     * 단위 테스트가 통과한다. 그래서 실제 선정 로직(윈도우 판정 + 마켓·날짜별 latest)을 전부 이 순수
     * 함수로 옮겨 SQL 술어의 정확성과 무관하게 테스트로 보장한다. */
    static List<MarketSnapshotTime> selectRetainedSnapshotTimes(
            List<MarketSnapshotTime> candidates, LocalTime windowStart, LocalTime windowEnd) {
        record GroupKey(Market market, LocalDate date) {}
        return candidates.stream()
                .filter(candidate -> isInWindow(candidate.snapshotTime().toLocalTime(), windowStart, windowEnd))
                .collect(Collectors.groupingBy(candidate -> new GroupKey(
                        candidate.market(), candidate.snapshotTime().toLocalDate())))
                .values()
                .stream()
                .map(group -> group.stream()
                        .max(Comparator.comparing(MarketSnapshotTime::snapshotTime))
                        .orElseThrow())
                .toList();
    }

    private static boolean isInWindow(LocalTime time, LocalTime windowStart, LocalTime windowEnd) {
        return !time.isBefore(windowStart) && time.isBefore(windowEnd);
    }
}
