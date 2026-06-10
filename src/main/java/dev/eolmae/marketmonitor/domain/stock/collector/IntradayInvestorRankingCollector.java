package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.common.util.NumberParser;
import dev.eolmae.marketmonitor.domain.stock.IntradayInvestorRankingSnapshot;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.IntradayInvestorRankingRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.IntradayInvestorRankingResponse;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.Board;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayInvestorType;
import dev.eolmae.marketmonitor.domain.stock.enums.IntradayRankingType;
import dev.eolmae.marketmonitor.domain.stock.repository.IntradayInvestorRankingSnapshotRepository;
import java.time.LocalDateTime;
import java.util.List;
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
        for (Board board : Board.values()) {
            for (IntradayInvestorType investorType : IntradayInvestorType.storableValues()) {
                for (IntradayRankingType rankingType : IntradayRankingType.values()) {
                    try {
                        collectForCombination(board, investorType, rankingType, snapshotTime);
                    } catch (Exception e) {
                        log.error(
                                "장중투자자랭킹 수집 실패: board={}, investor={}, ranking={}",
                                board,
                                investorType,
                                rankingType,
                                e);
                    }
                }
            }
        }
    }

    private void collectForCombination(
            Board board,
            IntradayInvestorType investorType,
            IntradayRankingType rankingType,
            LocalDateTime snapshotTime) {

        Exchange marketType = Exchange.valueOf(board.name());
        String mrktTp =
                switch (board) {
                    case KOSPI -> MrktTp.KOSPI.value;
                    case KOSDAQ -> MrktTp.KOSDAQ.value;
                };

        List<IntradayInvestorRankingSnapshot> existing =
                repository.findBySnapshotTimeAndMarketTypeAndInvestorTypeAndRankingTypeOrderByRankAsc(
                        snapshotTime, marketType, investorType, rankingType);
        if (!existing.isEmpty()) {
            log.debug(
                    "장중투자자랭킹 이미 존재, 스킵: board={}, investor={}, ranking={}, snapshotTime={}",
                    board,
                    investorType,
                    rankingType,
                    snapshotTime);
            return;
        }

        var request = new IntradayInvestorRankingRequest(
                rankingType.code(), mrktTp, investorType.apiCode(), AmtQtyType.AMOUNT.code());
        var response = kiwoomApiClient.post(request, IntradayInvestorRankingResponse.class);

        if (response.items() == null) {
            return;
        }

        int rank = 1; // TODO 이건 왜 있는거임?
        for (IntradayInvestorRankingResponse.RankingItem item : response.items()) {
            repository.save(IntradayInvestorRankingSnapshot.create(
                    marketType,
                    investorType,
                    rankingType,
                    AmtQtyType.AMOUNT,
                    rank++,
                    item.stkCd(),
                    item.stkNm(),
                    NumberParser.parseBigDecimal(item.netslmt()),
                    NumberParser.parseLong(item.selQty()),
                    NumberParser.parseLong(item.buyQty()),
                    snapshotTime));
        }

        log.debug(
                "장중투자자랭킹 수집 완료: market={}, investor={}, ranking={}, count={}",
                marketType,
                investorType,
                rankingType,
                rank - 1);
    }

    private enum MrktTp {
        KOSPI("001"),
        KOSDAQ("101"); // ka10065 mrkt_tp
        final String value;

        MrktTp(String value) {
            this.value = value;
        }
    }
}
