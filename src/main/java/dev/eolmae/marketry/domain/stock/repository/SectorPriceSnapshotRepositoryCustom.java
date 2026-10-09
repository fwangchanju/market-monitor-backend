package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.common.enums.Market;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SectorPriceSnapshotRepositoryCustom {

    /** 여러 마켓을 동시에 보여줄 때(All Stocks 등) 필요한, markets 전부가 공통으로 가진 최신 스냅샷 시각.
     * 한쪽 마켓에만 있고 다른 쪽엔 없는 시각은 제외. */
    Optional<LocalDateTime> findLatestCommonSnapshotTime(List<Market> markets);

    /** 날짜별 시간표 적용을 위해 cutoff 이전의 (마켓, 시각)만 distinct로 조회한다. 종목 행은 적재하지 않는다. */
    List<MarketSnapshotTime> findMarketSnapshotTimesBefore(LocalDateTime cutoff);

    /** 보존 시각이 있는 날짜·마켓의 cutoff 이전 행만 삭제한다. 보존 시각 자체와 후보 없는 그룹은 남긴다. */
    long deleteSnapshotsBefore(LocalDateTime cutoff, List<MarketSnapshotTime> retainedSnapshotTimes);

    /** 보존 윈도우 안 후보 하나 — 어느 마켓의 몇 시 스냅샷인지. */
    record MarketSnapshotTime(Market market, LocalDateTime snapshotTime) {}
}
