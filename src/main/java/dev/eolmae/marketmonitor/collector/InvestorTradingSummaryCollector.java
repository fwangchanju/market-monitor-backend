package dev.eolmae.marketmonitor.collector;

import dev.eolmae.marketmonitor.common.enums.AmtQtyType;
import dev.eolmae.marketmonitor.common.enums.Board;
import dev.eolmae.marketmonitor.common.enums.Exchange;
import dev.eolmae.marketmonitor.common.enums.InvestorType;
import dev.eolmae.marketmonitor.common.enums.StexType;
import dev.eolmae.marketmonitor.common.util.NumberParser;
import dev.eolmae.marketmonitor.domain.dashboard.InvestorTradingSummarySnapshot;
import dev.eolmae.marketmonitor.domain.dashboard.repository.InvestorTradingSummarySnapshotRepository;
import dev.eolmae.marketmonitor.external.kiwoom.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.external.kiwoom.dto.Ka10051Request;
import dev.eolmae.marketmonitor.external.kiwoom.dto.Ka10051Response;
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
public class InvestorTradingSummaryCollector {

    // ka10051: 업종별투자자순매수요청 (업종 카테고리)
    // 업종코드 001(코스피종합)/101(코스닥) 기준으로 시장 전체 투자자별 순매수 조회

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String AMT_QTY_TP_AMOUNT = "0"; // ka10051 amt_qty_tp: 0=금액

    private final KiwoomApiClient kiwoomApiClient;
    private final InvestorTradingSummarySnapshotRepository investorTradingSummarySnapshotRepository;

    @Transactional
    public void collect(LocalDateTime snapshotTime) {
        for (Board board : Board.values()) {
            try {
                collectForMarket(board, snapshotTime);
            } catch (Exception e) {
                log.error("투자자별매매종합 수집 실패: board={}", board, e);
            }
        }
    }

    private void collectForMarket(Board board, LocalDateTime snapshotTime) {
        Exchange marketType = Exchange.valueOf(board.name());
        String mrktTp =
                switch (board) {
                    case KOSPI -> MrktTp.KOSPI.value;
                    case KOSDAQ -> MrktTp.KOSDAQ.value;
                };
        String indsCd =
                switch (board) {
                    case KOSPI -> IndsCd.KOSPI.value;
                    case KOSDAQ -> IndsCd.KOSDAQ.value;
                };
        var request =
                new Ka10051Request(mrktTp, AMT_QTY_TP_AMOUNT, snapshotTime.format(DATE_FMT), StexType.KRX_NXT.code());
        var response = kiwoomApiClient.post(request, Ka10051Response.class);

        if (response.indsNetprps() == null || response.indsNetprps().isEmpty()) {
            log.warn("투자자별매매종합 응답 없음: market={}", marketType);
            return;
        }

        Ka10051Response.IndsNetprps compositeItem = response.indsNetprps().stream()
                .filter(item -> item.indsCd() != null && item.indsCd().startsWith(indsCd))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("투자자별매매종합 종합지수 행 없음: market=" + marketType + ", indsCd=" + indsCd));

        LocalDateTime now = LocalDateTime.now();

        // ka10051은 순매수만 제공하므로 매수/매도는 ZERO로 저장 // TODO 순매수만 제공하는데 매수/매도는 왜 저장함?
        saveSnapshot(
                marketType,
                InvestorType.PERSONAL,
                NumberParser.parseBigDecimal(compositeItem.indNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                marketType,
                InvestorType.FOREIGNER,
                NumberParser.parseBigDecimal(compositeItem.frgnrNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                marketType,
                InvestorType.INSTITUTION,
                NumberParser.parseBigDecimal(compositeItem.orgnNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                marketType,
                InvestorType.FINANCIAL_INVESTMENT,
                NumberParser.parseBigDecimal(compositeItem.scNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                marketType,
                InvestorType.TRUST,
                NumberParser.parseBigDecimal(compositeItem.invtrtNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                marketType,
                InvestorType.PENSION_FUND,
                NumberParser.parseBigDecimal(compositeItem.endwNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                marketType,
                InvestorType.PRIVATE_FUND,
                NumberParser.parseBigDecimal(compositeItem.samoFundNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                marketType,
                InvestorType.INSURANCE,
                NumberParser.parseBigDecimal(compositeItem.insrncNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                marketType,
                InvestorType.BANK,
                NumberParser.parseBigDecimal(compositeItem.bankNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                marketType,
                InvestorType.OTHER_CORP,
                NumberParser.parseBigDecimal(compositeItem.etcCorpNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                marketType,
                InvestorType.GOVERNMENT,
                NumberParser.parseBigDecimal(compositeItem.natnNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                marketType,
                InvestorType.OTHER_FINANCE,
                NumberParser.parseBigDecimal(compositeItem.jnsinkmNetprps()),
                snapshotTime,
                now);
        saveSnapshot(
                marketType,
                InvestorType.FOREIGN_COMPANY,
                NumberParser.parseBigDecimal(compositeItem.nativeTrmtFrgnrNetprps()),
                snapshotTime,
                now);

        log.debug("투자자별매매종합 수집 완료: market={}", marketType);
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

    private void saveSnapshot(
            Exchange marketType,
            InvestorType investorType,
            BigDecimal netBuyAmount,
            LocalDateTime snapshotTime,
            LocalDateTime now) {

        if (investorTradingSummarySnapshotRepository
                .findByMarketTypeAndInvestorTypeAndAmtQtyTypeAndSnapshotTime(
                        marketType, investorType, AmtQtyType.AMOUNT, snapshotTime)
                .isEmpty()) {
            investorTradingSummarySnapshotRepository.save(InvestorTradingSummarySnapshot.create(
                    marketType,
                    investorType,
                    AmtQtyType.AMOUNT,
                    snapshotTime,
                    now,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    netBuyAmount));
        }
    }
}
