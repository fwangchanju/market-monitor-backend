package dev.eolmae.marketmonitor.domain.notification.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

class TelegramReportDispatcherTest {

    private final SectorTelegramReportSender sectorTelegramReportSender =
            Mockito.mock(SectorTelegramReportSender.class);
    private final MarketMapAlbumReportSender marketMapAlbumReportSender =
            Mockito.mock(MarketMapAlbumReportSender.class);
    private final TelegramReportDispatcher dispatcher =
            new TelegramReportDispatcher(sectorTelegramReportSender, marketMapAlbumReportSender);

    private final LocalDateTime dataTime = LocalDateTime.of(2025, 6, 2, 8, 15);

    @Test
    void sendSector_섹터_발송기만_부른다() {
        dispatcher.sendSector(dataTime);

        verify(sectorTelegramReportSender).send(dataTime);
        verifyNoMoreInteractions(marketMapAlbumReportSender);
    }

    @Test
    void sendMap_맵_발송기만_부른다() {
        dispatcher.sendMap(dataTime);

        verify(marketMapAlbumReportSender).send(dataTime);
        verifyNoMoreInteractions(sectorTelegramReportSender);
    }

    @Test
    void sendAll_섹터_뒤에_맵_순서로_부른다() {
        dispatcher.sendAll(dataTime);

        InOrder inOrder = Mockito.inOrder(sectorTelegramReportSender, marketMapAlbumReportSender);
        inOrder.verify(sectorTelegramReportSender).send(dataTime);
        inOrder.verify(marketMapAlbumReportSender).send(dataTime);
    }

    @Test
    void sendMapSinglePage_맵_발송기의_한_페이지_경로를_부른다() {
        dispatcher.sendMapSinglePage(dataTime, MarketQuery.ALL_STOCK);

        verify(marketMapAlbumReportSender).sendMapSinglePage(dataTime, MarketQuery.ALL_STOCK);
        verifyNoMoreInteractions(sectorTelegramReportSender);
    }
}
