package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import dev.eolmae.marketmonitor.common.util.StockCode;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.ProgramNetBuyRankingRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.ProgramNetBuyRankingResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.ProgramTradingRankingSnapshot;
import dev.eolmae.marketmonitor.common.util.Strings;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQty;
import dev.eolmae.marketmonitor.domain.stock.enums.ProgramRanking;
import dev.eolmae.marketmonitor.domain.stock.enums.StexType;
import dev.eolmae.marketmonitor.domain.stock.repository.ProgramTradingRankingSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        for (Market market : Market.values()) {
            for (ProgramRanking ranking : ProgramRanking.values()) {
                try {
                    collectForCombination(market, ranking, snapshotTime);
                } catch (Exception e) {
                    log.error("프로그램매매 랭킹 수집 실패: market={}, ranking={}", market, ranking, e);
                }
            }
        }
    }

    private void collectForCombination(
            Market market, ProgramRanking ranking, LocalDateTime snapshotTime) {

        String mrktTp = MrktTp.from(market);
        AmtQty amtQty = AmtQty.AMOUNT; // 금액 기준 고정
        String stexTp = StexType.KRX_NXT.code(); // KRX+NXT 합산

        if (rankingRepository.existsBySnapshotTimeAndMarketTypeAndRankingTypeAndAmtQty(snapshotTime, market, ranking, amtQty)) {
            log.debug(
                    "프로그램매매 랭킹 이미 존재, 스킵: market={}, ranking={}, amtQty={}, snapshotTime={}",
                    market,
                    ranking,
                    amtQty,
                    snapshotTime);
            return;
        }

        var request = new ProgramNetBuyRankingRequest(ranking.code(), amtQty.code(), mrktTp, stexTp);
        ProgramNetBuyRankingResponse response = kiwoomApiClient.post(request, ProgramNetBuyRankingResponse.class);

        if (response.items() == null || response.items().isEmpty()) {
            log.debug("프로그램매매 랭킹 데이터 없음: market={}, ranking={}", market, ranking);
            return;
        }

        int rank = 1;
        for (ProgramNetBuyRankingResponse.RankingItem item : response.items()) {
            String stockCode = StockCode.removeSuffix(item.stkCd());
            if (stockCode.isBlank()) {
                log.warn("프로그램매매 랭킹 종목코드 없음, 스킵: market={}, ranking={}", market, ranking);
                continue;
            }
            rankingRepository.save(toEntity(market, amtQty, ranking, snapshotTime, rank++, item));
        }

        log.debug(
                "프로그램매매 랭킹 수집 완료: market={}, ranking={}, amtQty={}, count={}",
                market,
                ranking,
                amtQty,
                rank - 1);
    }

    private static ProgramTradingRankingSnapshot toEntity(
            Market market, AmtQty amtQty, ProgramRanking ranking,
            LocalDateTime snapshotTime, int rank, ProgramNetBuyRankingResponse.RankingItem item) {
        return ProgramTradingRankingSnapshot.create(
                market,
                amtQty,
                ranking,
                snapshotTime,
                rank,
                StockCode.removeSuffix(item.stkCd()),
                Strings.trimToEmpty(item.stkNm()),
                KiwoomValueParser.parseBigDecimal(item.prmBuyAmt()),
                KiwoomValueParser.parseBigDecimal(item.prmSellAmt()),
                KiwoomValueParser.parseBigDecimal(item.prmNetprpsAmt()));
    }

    private enum MrktTp {
        KOSPI("P00101"),
        KOSDAQ("P10102"); // ka90003 mrkt_tp
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
