package dev.eolmae.marketry.domain.stock.repository;

import dev.eolmae.marketry.common.enums.Market;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SectorPriceSnapshotRepositoryCustom {

    /** 여러 마켓을 동시에 보여줄 때(All Stocks 등) 필요한, markets 전부가 공통으로 가진 최신 스냅샷 시각.
     * 한쪽 마켓에만 있고 다른 쪽엔 없는 시각은 제외. */
    Optional<LocalDateTime> findLatestCommonSnapshotTime(List<Market> markets);

    /** 시장별 max 집계는 Spring Data 파생 메서드로 표현할 수 없어 QueryDSL로 조회한다. 종목 행은 적재하지 않는다. */
    List<MarketSnapshotTime> findLatestMarketSnapshotTimesBetween(LocalDateTime from, LocalDateTime toExclusive);

    /** 여러 날의 종가 구간을 한 번에 조회해 시장·날짜별로 구간 안 가장 늦은 스냅샷 시각을 돌려준다. 구간 하나는 하루를 넘지 않아야 한다. */
    List<MarketSnapshotTime> findLatestMarketSnapshotTimesPerDay(List<TimeWindow> windows);

    /** 지정 날짜에서 보존 시각이 있는 시장만 삭제한다. 보존 시각의 모든 종목 행과 후보 없는 시장은 남긴다. */
    long deleteSnapshotsForDate(LocalDate date, List<MarketSnapshotTime> retainedSnapshotTimes);

    /** 시각 구간 [from, toExclusive). */
    record TimeWindow(LocalDateTime from, LocalDateTime toExclusive) {}

    /** 보존 윈도우 안 후보 하나 — 어느 마켓의 몇 시 스냅샷인지. */
    record MarketSnapshotTime(Market market, LocalDateTime snapshotTime) {}
}
