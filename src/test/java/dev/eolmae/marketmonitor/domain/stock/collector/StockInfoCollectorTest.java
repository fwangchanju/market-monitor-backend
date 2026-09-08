package dev.eolmae.marketmonitor.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.event.StockInfoSyncedEvent;
import dev.eolmae.marketmonitor.common.event.StockInfoSyncedEvent.NewStock;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.StockInfoRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.StockInfoResponse;
import dev.eolmae.marketmonitor.domain.stock.repository.StockInfoRepository;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

class StockInfoCollectorTest {

    private final KiwoomApiClient kiwoomApiClient = Mockito.mock(KiwoomApiClient.class);
    private final StockInfoRepository stockInfoRepository = Mockito.mock(StockInfoRepository.class);
    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
    private final StockInfoCollector collector =
            new StockInfoCollector(kiwoomApiClient, stockInfoRepository, stockInfoCacheService, eventPublisher);

    @Test
    void sync_카테고리명이_빈값이면_미분류로_치환해서_이벤트를_발행한다() {
        StockInfoResponse response = new StockInfoResponse(
                "0",
                "정상",
                List.of(
                        new StockInfoResponse.StockItem("005930", "삼성전자", "0", "반도체", "100", "10000"),
                        new StockInfoResponse.StockItem("051910", "LG화학", "0", "", "50", "20000")));
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(response);
        when(stockInfoRepository.findAll()).thenReturn(List.of());

        collector.sync();

        ArgumentCaptor<StockInfoSyncedEvent> captor = ArgumentCaptor.forClass(StockInfoSyncedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().newStocks())
                .extracting(NewStock::stockCode, NewStock::categoryName)
                .containsExactlyInAnyOrder(tuple("005930", "반도체"), tuple("051910", "미분류"));
    }

    @AfterEach
    void clearSynchronization() {
        // 테스트가 initSynchronization()만 하고 clear를 안 하면 ThreadLocal 상태가 다음 테스트로 샌다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sync_활성_트랜잭션이_없으면_캐시를_즉시_비운다() {
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(new StockInfoResponse("0", "정상", null));
        when(stockInfoRepository.findAll()).thenReturn(List.of());

        collector.sync();

        verify(stockInfoCacheService).evict();
    }

    @Test
    void sync_트랜잭션_커밋_전에는_캐시를_비우지_않고_커밋_후에_비운다() {
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(new StockInfoResponse("0", "정상", null));
        when(stockInfoRepository.findAll()).thenReturn(List.of());
        TransactionSynchronizationManager.initSynchronization();

        collector.sync();

        verify(stockInfoCacheService, never()).evict();

        TransactionSynchronizationUtils.triggerAfterCommit();

        verify(stockInfoCacheService).evict();
    }

    @Test
    void sync_ELW_ETF_등_주권_외_종목은_이벤트에서_제외한다() {
        StockInfoResponse response = new StockInfoResponse(
                "0",
                "정상",
                List.of(
                        new StockInfoResponse.StockItem("005930", "삼성전자", "0", "반도체", "100", "10000"),
                        new StockInfoResponse.StockItem("57JJJJ", "삼성전자ELW", "3", "ELW", "100", "1000"),
                        new StockInfoResponse.StockItem("069500", "KODEX200", "8", "ETF", "100", "30000")));
        when(kiwoomApiClient.post(any(StockInfoRequest.class), eq(StockInfoResponse.class)))
                .thenReturn(response);
        when(stockInfoRepository.findAll()).thenReturn(List.of());

        collector.sync();

        ArgumentCaptor<StockInfoSyncedEvent> captor = ArgumentCaptor.forClass(StockInfoSyncedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().newStocks())
                .extracting(NewStock::stockCode)
                .containsExactly("005930");
    }
}
