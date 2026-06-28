package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorInvestorNetBuyRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorInvestorNetBuyResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.InvestorTradingSummarySnapshot;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQtyType;
import dev.eolmae.marketmonitor.domain.stock.enums.InvestorType;
import dev.eolmae.marketmonitor.domain.stock.enums.StexType;
import dev.eolmae.marketmonitor.domain.stock.repository.InvestorTradingSummarySnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SectorInvestorNetBuyCollector {

    // ka10051: 업종별투자자순매수요청
    // 업종코드 001(코스피종합)/101(코스닥) 기준으로 시장 전체 투자자별 순매수 조회

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String AMT_QTY_TP_AMOUNT = "0"; // ka10051 amt_qty_tp: 0=금액

    private final KiwoomApiClient kiwoomApiClient;
    private final InvestorTradingSummarySnapshotRepository investorTradingSummarySnapshotRepository;

    @Transactional
    public void collect(LocalDateTime snapshotTime) {
        for (Market market : Market.values()) {
            try {
                collectForMarket(market, snapshotTime);
            } catch (Exception e) {
                log.error("투자자별매매종합 수집 실패: market={}", market, e);
            }
        }
    }

    private void collectForMarket(Market market, LocalDateTime snapshotTime) {
        String mrktTp =
                switch (market) {
                    case KOSPI -> MrktTp.KOSPI.value;
                    case KOSDAQ -> MrktTp.KOSDAQ.value;
                };
        String indsCd =
                switch (market) {
                    case KOSPI -> IndsCd.KOSPI.value;
                    case KOSDAQ -> IndsCd.KOSDAQ.value;
                };
        var request = new SectorInvestorNetBuyRequest(
                mrktTp, AMT_QTY_TP_AMOUNT, snapshotTime.format(DATE_FMT), StexType.KRX_NXT.code());
        var response = kiwoomApiClient.post(request, SectorInvestorNetBuyResponse.class);

        if (response.indsNetprps() == null || response.indsNetprps().isEmpty()) {
            log.warn("투자자별매매종합 응답 없음: market={}", market);
            return;
        }

        SectorInvestorNetBuyResponse.IndsNetprps compositeItem = response.indsNetprps().stream()
                .filter(item -> item.indsCd() != null && item.indsCd().startsWith(indsCd))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("투자자별매매종합 종합지수 행 없음: market=" + market + ", indsCd=" + indsCd));

        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());

        // ka10051은 순매수만 제공하므로 매수/매도는 ZERO로 저장 // TODO 순매수만 제공하는데 매수/매도는 왜 저장함?
        saveSnapshot(
                market,
                InvestorType.PERSONAL,
                KiwoomValueParser.parseBigDecimal(compositeItem.indNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                market,
                InvestorType.FOREIGNER,
                KiwoomValueParser.parseBigDecimal(compositeItem.frgnrNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                market,
                InvestorType.INSTITUTION,
                KiwoomValueParser.parseBigDecimal(compositeItem.orgnNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                market,
                InvestorType.FINANCIAL_INVESTMENT,
                KiwoomValueParser.parseBigDecimal(compositeItem.scNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                market,
                InvestorType.TRUST,
                KiwoomValueParser.parseBigDecimal(compositeItem.invtrtNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                market,
                InvestorType.PENSION_FUND,
                KiwoomValueParser.parseBigDecimal(compositeItem.endwNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                market,
                InvestorType.PRIVATE_FUND,
                KiwoomValueParser.parseBigDecimal(compositeItem.samoFundNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                market,
                InvestorType.INSURANCE,
                KiwoomValueParser.parseBigDecimal(compositeItem.insrncNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                market,
                InvestorType.BANK,
                KiwoomValueParser.parseBigDecimal(compositeItem.bankNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                market,
                InvestorType.OTHER_CORP,
                KiwoomValueParser.parseBigDecimal(compositeItem.etcCorpNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                market,
                InvestorType.GOVERNMENT,
                KiwoomValueParser.parseBigDecimal(compositeItem.natnNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                market,
                InvestorType.OTHER_FINANCE,
                KiwoomValueParser.parseBigDecimal(compositeItem.jnsinkmNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                market,
                InvestorType.FOREIGN_COMPANY,
                KiwoomValueParser.parseBigDecimal(compositeItem.nativeTrmtFrgnrNetprps()),
                snapshotTime,
                now);

        log.debug("투자자별매매종합 수집 완료: market={}", market);
    }

    private void saveSnapshot(
            Market market,
            InvestorType investor,
            BigDecimal netBuyAmount,
            LocalDateTime snapshotTime,
            LocalDateTime now) {

        if (investorTradingSummarySnapshotRepository
                .findByMarketTypeAndInvestorTypeAndAmtQtyTypeAndSnapshotTime(
                        market, investor, AmtQtyType.AMOUNT, snapshotTime)
                .isEmpty()) {
            investorTradingSummarySnapshotRepository.save(InvestorTradingSummarySnapshot.create(
                    market,
                    investor,
                    AmtQtyType.AMOUNT,
                    snapshotTime,
                    now,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    netBuyAmount));
        }
    }

    private enum MrktTp {
        KOSPI("0"),
        KOSDAQ("1"); // ka10051 mrkt_tp
        final String value;

        MrktTp(String value) {
            this.value = value;
        }
    }

    private enum IndsCd {
        KOSPI("001"),
        KOSDAQ("101"); // ka10051 inds_cd (응답 필터용)
        final String value;

        IndsCd(String value) {
            this.value = value;
        }
    }
}
