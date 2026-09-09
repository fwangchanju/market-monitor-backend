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

    /** cutoff 이전이면서 marketCloseTime(장마감 시각)이 아닌 스냅샷을 삭제하고 삭제된 행 수를 반환한다. */
    long deleteSnapshotsBefore(LocalDateTime cutoff, LocalTime marketCloseTime);

    /** deleteSnapshotsBefore와 같은 조건의 삭제 대상 현황 — 드라이런 로그 및 실제 삭제 전 확인용. */
    SnapshotRetentionSummary summarizeSnapshotsToDelete(
            LocalDateTime cutoff, LocalTime marketCloseTime, int sampleSize);

    record SnapshotRetentionSummary(
            long targetCount,
            long totalCountBeforeCutoff,
            LocalDateTime minSnapshotTime,
            LocalDateTime maxSnapshotTime,
            List<LocalTime> sampleSnapshotTimes) {}
}
