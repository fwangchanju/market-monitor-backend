package dev.eolmae.marketmonitor.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.repository.IndexContributionRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketOverviewSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.service.SectorPriceCacheService;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionTemplate;

// transactionTemplate을 mock해 collectForMarket(실제 수집)이 돌지 않게 막고, 마켓 트랜잭션 직후의
// 캐시 적재(warmSectorPriceCache, 결정 5) 부분만 따로 검증한다.
class IndexContributionRankingCollectorTest {

    private final KiwoomApiClient kiwoomApiClient = Mockito.mock(KiwoomApiClient.class);
    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository =
            Mockito.mock(MarketOverviewSnapshotRepository.class);
    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository =
            Mockito.mock(SectorPriceSnapshotRepository.class);
    private final IndexContributionRankingSnapshotRepository indexContributionRankingSnapshotRepository =
            Mockito.mock(IndexContributionRankingSnapshotRepository.class);
    private final SectorPriceCacheService sectorPriceCacheService = Mockito.mock(SectorPriceCacheService.class);
    // executeWithoutResult는 void라 stub 없이도 mock이 아무 것도 안 한다 — collectForMarket이 실행되지
    // 않으므로 kiwoomApiClient 등 나머지 mock은 스텁할 필요가 없다.
    private final TransactionTemplate transactionTemplate = Mockito.mock(TransactionTemplate.class);
    private final IndexContributionRankingCollector collector = new IndexContributionRankingCollector(
            kiwoomApiClient,
            stockInfoCacheService,
            marketOverviewSnapshotRepository,
            sectorPriceSnapshotRepository,
            indexContributionRankingSnapshotRepository,
            sectorPriceCacheService,
            transactionTemplate);

    @Test
    void collect_마켓별_트랜잭션_직후_그_마켓의_가격_캐시를_적재한다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        when(sectorPriceCacheService.getCache(any(Market.class), any(LocalDateTime.class)))
                .thenReturn(Map.of());

        collector.collect(snapshotTime);

        verify(sectorPriceCacheService).getCache(Market.KOSPI, snapshotTime);
        verify(sectorPriceCacheService).getCache(Market.KOSDAQ, snapshotTime);
    }

    // 5-6 — 캐시 적재는 성능 최적화지 정확성 조건이 아니다. 적재가 실패해도 collect() 자체는 예외
    // 없이 끝나야 한다(호출부 CollectionScheduler가 적재 실패로 escalate하면 안 된다).
    @Test
    void collect_캐시_적재가_실패해도_수집_자체는_예외_없이_끝난다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        doThrow(new RuntimeException("cache warm failed"))
                .when(sectorPriceCacheService)
                .getCache(any(Market.class), any(LocalDateTime.class));

        assertThatCode(() -> collector.collect(snapshotTime)).doesNotThrowAnyException();
    }

    // 한 마켓의 적재만 실패해도 나머지 마켓은 적재를 계속 시도해야 한다 — 마켓 하나 실패로 그 tick
    // 전체의 적재를 포기하면 안 된다.
    @Test
    void collect_한_마켓의_캐시_적재가_실패해도_다른_마켓_적재는_계속된다() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 7, 31, 10, 0);
        doThrow(new RuntimeException("cache warm failed"))
                .when(sectorPriceCacheService)
                .getCache(Market.KOSPI, snapshotTime);
        when(sectorPriceCacheService.getCache(Market.KOSDAQ, snapshotTime)).thenReturn(Map.of());

        collector.collect(snapshotTime);

        verify(sectorPriceCacheService).getCache(Market.KOSPI, snapshotTime);
        verify(sectorPriceCacheService).getCache(Market.KOSDAQ, snapshotTime);
    }
}
