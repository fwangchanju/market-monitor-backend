package dev.eolmae.marketmonitor.domain.stock.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface SectorPriceSnapshotRepositoryCustom {

    /** 여러 마켓을 동시에 보여줄 때(All Stocks 등) 필요한, markets 전부가 공통으로 가진 최신 스냅샷 시각.
     * 한쪽 마켓에만 있고 다른 쪽엔 없는 시각은 제외. */
    Optional<LocalDateTime> findLatestCommonSnapshotTime(List<Market> markets);

    /** cutoff 이전이면서 [windowStart, windowEnd) 구간(보존 윈도우)에 속하는 (마켓, snapshotTime) distinct
     * 목록 — 마켓·날짜별 latest를 고르는 재료다(정리는 순수 자바 함수가 한다). */
    List<MarketSnapshotTime> findMarketSnapshotTimesInWindow(
            LocalDateTime cutoff, LocalTime windowStart, LocalTime windowEnd);

    /** cutoff 이전이면서 retainedSnapshotTimes에 없는 (마켓, snapshotTime)의 행을 삭제하고 삭제된 행 수를
     * 반환한다. */
    long deleteSnapshotsBefore(LocalDateTime cutoff, List<MarketSnapshotTime> retainedSnapshotTimes);

    /** deleteSnapshotsBefore와 같은 조건의 삭제 대상 현황 — 드라이런 로그 및 실제 삭제 전 확인용.
     * 보존 시각 표본과 보존 날짜수까지 함께 집계한다. */
    SnapshotRetentionSummary summarizeSnapshotsToDelete(
            LocalDateTime cutoff, List<MarketSnapshotTime> retainedSnapshotTimes, int sampleSize);

    /** 보존 윈도우 안 후보 하나 — 어느 마켓의 몇 시 스냅샷인지. */
    record MarketSnapshotTime(Market market, LocalDateTime snapshotTime) {}

    record SnapshotRetentionSummary(
            long targetCount,
            long totalCountBeforeCutoff,
            LocalDateTime minSnapshotTime,
            LocalDateTime maxSnapshotTime,
            List<LocalTime> sampleSnapshotTimes,
            List<MarketSnapshotTime> retainedSampleSnapshotTimes,
            int retainedDateCount) {}
}
