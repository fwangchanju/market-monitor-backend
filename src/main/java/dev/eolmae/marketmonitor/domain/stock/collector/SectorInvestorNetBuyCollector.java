package dev.eolmae.marketmonitor.domain.stock.collector;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.enums.Zone;
import dev.eolmae.marketmonitor.domain.stock.util.KiwoomValueParser;
import dev.eolmae.marketmonitor.domain.stock.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorInvestorNetBuyRequest;
import dev.eolmae.marketmonitor.domain.stock.dto.SectorInvestorNetBuyResponse;
import dev.eolmae.marketmonitor.domain.stock.entity.InvestorTradingSummarySnapshot;
import dev.eolmae.marketmonitor.domain.stock.enums.AmtQty;
import dev.eolmae.marketmonitor.domain.stock.enums.Investor;
import dev.eolmae.marketmonitor.domain.stock.enums.StexType;
import dev.eolmae.marketmonitor.domain.stock.repository.InvestorTradingSummarySnapshotRepository;
import dev.eolmae.marketmonitor.common.enums.DateTimePattern;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        String mrktTp = MrktTp.from(market);
        String indsCd = IndsCd.from(market);
        String stexTp = StexType.KRX_NXT.code(); // KRX+NXT 합산
        var request = new SectorInvestorNetBuyRequest(
                mrktTp, AMT_QTY_TP_AMOUNT, snapshotTime.format(DateTimePattern.DATE.formatter()), stexTp);
        var response = kiwoomApiClient.post(request, SectorInvestorNetBuyResponse.class);

        if (response.indsNetprps() == null || response.indsNetprps().isEmpty()) {
            log.warn("투자자별매매종합 응답 없음: market={}", market);
            return;
        }

        SectorInvestorNetBuyResponse.IndsNetprps compositeItem = response.indsNetprps().stream()
                .filter(item -> item.indsCd() != null && item.indsCd().startsWith(indsCd))
                .findFirst()
                .orElseThrow(() -> new EscalateException(ErrorCode.COMPOSITE_INDEX_ROW_NOT_FOUND, market.name()));

        LocalDateTime now = LocalDateTime.now(Zone.KST.zoneId());

        // TODO ka10051은 순매수만 제공하나 엔티티의 buyAmount/sellAmount가 nullable=false — nullable 허용 또는 순매수 전용 엔티티 분리 검토
        // ka10051은 순매수만 제공하므로 매수/매도는 ZERO로 저장
        for (Investor investor : Investor.values()) {
            BigDecimal netBuyAmount = resolveNetBuy(investor, compositeItem);
            saveSnapshot(market, investor, netBuyAmount, snapshotTime, now);
        }

        log.debug("투자자별매매종합 수집 완료: market={}", market);
    }

    private static BigDecimal resolveNetBuy(
            Investor investor, SectorInvestorNetBuyResponse.IndsNetprps item) {
        return KiwoomValueParser.parseBigDecimal(switch (investor) {
            case PERSONAL -> item.indNetprps();
            case FOREIGNER -> item.frgnrNetprps();
            case INSTITUTION -> item.orgnNetprps();
            case FINANCIAL_INVESTMENT -> item.scNetprps();
            case TRUST -> item.invtrtNetprps();
            case PENSION_FUND -> item.endwNetprps();
            case PRIVATE_FUND -> item.samoFundNetprps();
            case INSURANCE -> item.insrncNetprps();
            case BANK -> item.bankNetprps();
            case OTHER_CORP -> item.etcCorpNetprps();
            case GOVERNMENT -> item.natnNetprps();
            case OTHER_FINANCE -> item.jnsinkmNetprps();
            case FOREIGN_COMPANY -> item.nativeTrmtFrgnrNetprps();
        });
    }

    private void saveSnapshot(
            Market market,
            Investor investor,
            BigDecimal netBuyAmount,
            LocalDateTime snapshotTime,
            LocalDateTime now) {

        if (investorTradingSummarySnapshotRepository
                .existsByMarketTypeAndInvestorAndAmtQtyAndSnapshotTime(
                        market, investor, AmtQty.AMOUNT, snapshotTime)) {
            return;
        }
        investorTradingSummarySnapshotRepository.save(toEntity(market, investor, netBuyAmount, snapshotTime, now));
    }

    private static InvestorTradingSummarySnapshot toEntity(
            Market market, Investor investor, BigDecimal netBuyAmount,
            LocalDateTime snapshotTime, LocalDateTime now) {
        return InvestorTradingSummarySnapshot.create(
                market,
                investor,
                AmtQty.AMOUNT,
                snapshotTime,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                netBuyAmount,
                now);
    }

    private enum MrktTp {
        KOSPI("0"),
        KOSDAQ("1"); // ka10051 mrkt_tp
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

    private enum IndsCd {
        KOSPI("001"),
        KOSDAQ("101"); // ka10051 inds_cd (응답 필터용)
        final String value;

        IndsCd(String value) {
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
