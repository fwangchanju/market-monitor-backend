package dev.eolmae.marketmonitor.domain.marketmap.repository;

import dev.eolmae.marketmonitor.common.enums.Market;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MarketMapCategoryChangeRateSnapshotRepositoryCustom {

    /** 여러 마켓을 동시에 보여줄 때(All Stocks 등) 필요한, markets 전부가 공통으로 가진 최신 스냅샷 시각.
     * 한쪽 마켓에만 있고 다른 쪽엔 없는 시각은 제외 — 그래야 마켓별 랭킹을 같은 시각 기준으로 나란히 보여줄 수 있다. */
    Optional<LocalDateTime> findLatestCommonSnapshotTime(List<Market> markets);
}
