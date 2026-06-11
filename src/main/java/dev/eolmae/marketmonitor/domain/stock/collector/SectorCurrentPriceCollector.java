package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.common.util.NumberParser;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorCurrentPriceRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorCurrentPriceResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.MarketOverviewSnapshot;
import dev.eolmae.marketmonitor.domain.stock.repository.MarketOverviewSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SectorCurrentPriceCollector {

    // ka20001: 업종현재가요청

    private final KiwoomApiClient kiwoomApiClient;
    private final MarketOverviewSnapshotRepository marketOverviewSnapshotRepository;

    @Transactional
    public void collect(LocalDateTime snapshotTime) {
        for (Market marketType : Market.values()) {
            try {
                collectForMarket(marketType, snapshotTime);
            } catch (Exception e) {
                log.error("시장종합 수집 실패: marketType={}", marketType, e);
            }
        }
    }

    private void collectForMarket(Market marketType, LocalDateTime snapshotTime) {
        String mrktTp =
                switch (marketType) {
                    case KOSPI -> MrktTp.KOSPI.value;
                    case KOSDAQ -> MrktTp.KOSDAQ.value;
                };
        String indsCd =
                switch (marketType) {
                    case KOSPI -> IndsCd.KOSPI.value;
                    case KOSDAQ -> IndsCd.KOSDAQ.value;
                };
        var request = new SectorCurrentPriceRequest(mrktTp, indsCd);
        var response = kiwoomApiClient.post(request, SectorCurrentPriceResponse.class);

        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());

        BigDecimal indexValue = NumberParser.parseBigDecimal(response.curPrc()).abs();
        BigDecimal changeValue = NumberParser.parseBigDecimal(response.predPre());
        BigDecimal changeRate = NumberParser.parseBigDecimal(response.fluRt());
        BigDecimal tradingValue = NumberParser.parseBigDecimal(response.trdePrica());
        String marketStatus =
                response.mrktStatClsCode() != null ? response.mrktStatClsCode().trim() : "";
        int upperLimitCount = NumberParser.parseInt(response.upl());
        int lowerLimitCount = NumberParser.parseInt(response.lst());
        int advancers = NumberParser.parseInt(response.rising());
        int decliners = NumberParser.parseInt(response.fall());
        int unchangedCount = NumberParser.parseInt(response.stdns());

        if (marketOverviewSnapshotRepository
                .findByMarketTypeAndSnapshotTime(marketType, snapshotTime)
                .isEmpty()) {
            marketOverviewSnapshotRepository.save(MarketOverviewSnapshot.create(
                    marketType,
                    snapshotTime,
                    now,
                    marketStatus,
                    indexValue,
                    changeValue,
                    changeRate,
                    tradingValue,
                    upperLimitCount,
                    lowerLimitCount,
                    advancers,
                    decliners,
                    unchangedCount));
        }

        log.debug("시장종합 수집 완료: market={}, index={}", marketType, indexValue);
    }

    private enum MrktTp {
        KOSPI("0"),
        KOSDAQ("1"); // ka20001 mrkt_tp
        final String value;

        MrktTp(String value) {
            this.value = value;
        }
    }

    private enum IndsCd {
        KOSPI("001"),
        KOSDAQ("101"); // ka20001 inds_cd
        final String value;

        IndsCd(String value) {
            this.value = value;
        }
    }
}
