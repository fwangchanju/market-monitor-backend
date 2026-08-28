package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.IntradayInvestorRankingRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.IntradayInvestorRankingResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.IntradayInvestorRankingSnapshot;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQty;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestor;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayRanking;
import dev.eolmae.marketmonitor.domain.stock.repository.IntradayInvestorRankingSnapshotRepository;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntradayInvestorRankingCollector {

    // ka10065: 장중투자자별매매상위요청 (순위정보 카테고리)
    // amt_qty_tp=1 (금액 기준) 고정, 5개 투자자 유형만 수집

    private final KiwoomApiClient kiwoomApiClient;
    private final IntradayInvestorRankingSnapshotRepository repository;
    private final TransactionTemplate transactionTemplate;

    // 조합(market × investor × ranking)별로 독립된 트랜잭션. 하나 실패하면 예외를 그대로 던져(catch 안 함)
    // 호출부가 한 곳에서만 escalate.
    public void collect(LocalDateTime snapshotTime) {
        for (Market market : Market.values()) {
            for (IntradayInvestor investor : IntradayInvestor.values()) {
                for (IntradayRanking ranking : IntradayRanking.values()) {
                    transactionTemplate.executeWithoutResult(
                            status -> collectForCombination(market, investor, ranking, snapshotTime));
                }
            }
        }
    }

    private void collectForCombination(
            Market market, IntradayInvestor investor, IntradayRanking ranking, LocalDateTime snapshotTime) {

        String mrktTp = MrktTp.from(market);

        if (repository.existsBySnapshotTimeAndMarketTypeAndInvestorAndRankingType(
                snapshotTime, market, investor, ranking)) {
            log.debug(
                    "장중투자자랭킹 이미 존재, 스킵: market={}, investor={}, ranking={}, snapshotTime={}",
                    market,
                    investor,
                    ranking,
                    snapshotTime);
            return;
        }

        String amtQtyTp = AmtQty.AMOUNT.code(); // 금액 기준 고정
        var request = new IntradayInvestorRankingRequest(ranking.code(), mrktTp, investor.apiCode(), amtQtyTp);
        var response = kiwoomApiClient.post(request, IntradayInvestorRankingResponse.class);

        if (response.items() == null) {
            return;
        }

        int rank = 1;
        for (IntradayInvestorRankingResponse.RankingItem item : response.items()) {
            repository.save(toEntity(market, investor, ranking, snapshotTime, rank++, item));
        }

        log.debug("장중투자자랭킹 수집 완료: market={}, investor={}, ranking={}, count={}", market, investor, ranking, rank - 1);
    }

    private static IntradayInvestorRankingSnapshot toEntity(
            Market market,
            IntradayInvestor investor,
            IntradayRanking ranking,
            LocalDateTime snapshotTime,
            int rank,
            IntradayInvestorRankingResponse.RankingItem item) {
        return IntradayInvestorRankingSnapshot.create(
                market,
                investor,
                ranking,
                AmtQty.AMOUNT,
                snapshotTime,
                rank,
                item.stkCd(),
                item.stkNm(),
                KiwoomValueParser.parseBigDecimal(item.netslmt()),
                KiwoomValueParser.parseLong(item.selQty()),
                KiwoomValueParser.parseLong(item.buyQty()));
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
