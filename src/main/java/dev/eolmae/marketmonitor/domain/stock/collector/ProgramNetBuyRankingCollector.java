package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import dev.eolmae.marketmonitor.common.util.StockCode;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.ProgramNetBuyRankingRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.ProgramNetBuyRankingResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.ProgramTradingRankingSnapshot;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.ProgramRankingType;
import dev.eolmae.marketmonitor.domain.stock.enums.StexType;
import dev.eolmae.marketmonitor.domain.stock.repository.ProgramTradingRankingSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// ka90003: 프로그램순매수상위50요청 — 코스피/코스닥 × 순매수/순매도 × 금액만 = 4회 호출/사이클
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramNetBuyRankingCollector {

    private final KiwoomApiClient kiwoomApiClient;
    private final ProgramTradingRankingSnapshotRepository rankingRepository;

    @Transactional
    public void collect(LocalDateTime snapshotTime) {
        for (Market marketType : Market.values()) {
            for (ProgramRankingType rankingType : ProgramRankingType.values()) {
                try {
                    collectForCombination(marketType, rankingType, AmtQtyType.AMOUNT, snapshotTime);
                } catch (Exception e) {
                    log.error("프로그램매매 랭킹 수집 실패: marketType={}, ranking={}", marketType, rankingType, e);
                }
            }
        }
    }

    private void collectForCombination(
            Market marketType, ProgramRankingType rankingType, AmtQtyType amtQtyType, LocalDateTime snapshotTime) {

        String mrktTp =
                switch (marketType) {
                    case KOSPI -> MrktTp.KOSPI.value;
                    case KOSDAQ -> MrktTp.KOSDAQ.value;
                };

        boolean alreadyExists = rankingRepository.existsBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQtyType(
                snapshotTime,
                marketType,
                rankingType,
                amtQtyType); // TODO 매 스케줄에서 한번만 수행해서 데이터 적재하는 구조 아닌가? 이 조회로 유효성 검증하는 것처럼 보이는 행위를 하는 이유는?
        if (alreadyExists) {
            log.debug(
                    "프로그램매매 랭킹 이미 존재, 스킵: marketType={}, ranking={}, amtQty={}, snapshotTime={}",
                    marketType,
                    rankingType,
                    amtQtyType,
                    snapshotTime);
            return;
        }

        var request =
                new ProgramNetBuyRankingRequest(rankingType.code(), amtQtyType.code(), mrktTp, StexType.KRX_NXT.code());
        ProgramNetBuyRankingResponse response = kiwoomApiClient.post(request, ProgramNetBuyRankingResponse.class);

        List<ProgramNetBuyRankingResponse.RankingItem> items = response.items() != null ? response.items() : List.of();

        int rank = 1; // TODO 왜 있어야됨?
        for (ProgramNetBuyRankingResponse.RankingItem item : items) {
            String stockCode = StockCode.removeSuffix(item.stkCd());
            if (stockCode.isBlank()) {
                continue;
            }

            String stockName = item.stkNm() != null ? item.stkNm().trim() : "";
            BigDecimal buyAmount = KiwoomValueParser.parseBigDecimal(item.prmBuyAmt());
            BigDecimal sellAmount = KiwoomValueParser.parseBigDecimal(item.prmSellAmt());
            BigDecimal netBuyAmount = KiwoomValueParser.parseBigDecimal(item.prmNetprpsAmt());

            rankingRepository.save(ProgramTradingRankingSnapshot.create(
                    marketType,
                    amtQtyType,
                    rankingType,
                    rank++,
                    stockCode,
                    stockName,
                    buyAmount,
                    sellAmount,
                    netBuyAmount,
                    snapshotTime));
        }

        log.debug(
                "프로그램매매 랭킹 수집 완료: market={}, ranking={}, amtQty={}, count={}",
                marketType,
                rankingType,
                amtQtyType,
                rank - 1);
    }

    private enum MrktTp {
        KOSPI("P00101"),
        KOSDAQ("P10102"); // ka90003 mrkt_tp
        final String value;

        MrktTp(String value) {
            this.value = value;
        }
    }
}
