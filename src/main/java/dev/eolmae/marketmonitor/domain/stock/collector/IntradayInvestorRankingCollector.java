package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.IntradayInvestorRankingRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.IntradayInvestorRankingResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.IntradayInvestorRankingSnapshot;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestorType;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayRankingType;
import dev.eolmae.marketmonitor.domain.stock.repository.IntradayInvestorRankingSnapshotRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntradayInvestorRankingCollector {

    // ka10065: 장중투자자별매매상위요청 (순위정보 카테고리)
    // amt_qty_tp=1 (금액 기준) 고정, 5개 투자자 유형만 수집

    private final KiwoomApiClient kiwoomApiClient;
    private final IntradayInvestorRankingSnapshotRepository repository;

    @Transactional
    public void collect(LocalDateTime snapshotTime) {
        for (Market market : Market.values()) {
            for (IntradayInvestorType investor : IntradayInvestorType.values()) {
                for (IntradayRankingType ranking : IntradayRankingType.values()) {
                    try {
                        collectForCombination(market, investor, ranking, snapshotTime);
                    } catch (Exception e) {
                        log.error(
                                "장중투자자랭킹 수집 실패: market={}, investor={}, ranking={}",
                                market,
                                investor,
                                ranking,
                                e);
                    }
                }
            }
        }
    }

    private void collectForCombination(
            Market market, IntradayInvestorType investor, IntradayRankingType ranking, LocalDateTime snapshotTime) {

        String mrktTp = MrktTp.from(market);

        if (repository.existsBySnapshotTimeAndMarketTypeAndInvestorTypeAndRankingType(snapshotTime, market, investor, ranking)) {
            log.debug(
                    "장중투자자랭킹 이미 존재, 스킵: market={}, investor={}, ranking={}, snapshotTime={}",
                    market,
                    investor,
                    ranking,
                    snapshotTime);
            return;
        }

        String amtQtyTp = AmtQtyType.AMOUNT.code(); // 금액 기준 고정
        var request = new IntradayInvestorRankingRequest(
                ranking.code(), mrktTp, investor.apiCode(), amtQtyTp);
        var response = kiwoomApiClient.post(request, IntradayInvestorRankingResponse.class);

        if (response.items() == null) {
            return;
        }

        int rank = 1;
        for (IntradayInvestorRankingResponse.RankingItem item : response.items()) {
            repository.save(IntradayInvestorRankingSnapshot.create(
                    market,
                    investor,
                    ranking,
                    AmtQtyType.AMOUNT,
                    snapshotTime,
                    rank++,
                    item.stkCd(),
                    item.stkNm(),
                    KiwoomValueParser.parseBigDecimal(item.netslmt()),
                    KiwoomValueParser.parseLong(item.selQty()),
                    KiwoomValueParser.parseLong(item.buyQty())));
        }

        log.debug(
                "장중투자자랭킹 수집 완료: market={}, investor={}, ranking={}, count={}",
                market,
                investor,
                ranking,
                rank - 1);
    }

    private enum MrktTp {
        KOSPI("001"),
        KOSDAQ("101"); // ka10065 mrkt_tp
        final String value;

        MrktTp(String value) {
            this.value = value;
        }

        static String from(Market market) {
            return switch (market) {
                case KOSPI -> KOSPI.value;
                case KOSDAQ -> KOSDAQ.value;
            };
        }
    }
}
