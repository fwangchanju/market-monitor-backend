package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

// ka90003: 프로그램순매수상위50요청
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProgramNetBuyRankingResponse(
        @JsonProperty("return_code") String returnCode,
        @JsonProperty("return_msg") String returnMsg,
        @JsonProperty("prm_netprps_upper_50") List<RankingItem> items)
        implements KiwoomResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankingItem(
            @JsonProperty("rank") String rank,
            @JsonProperty("stk_cd") String stkCd,
            @JsonProperty("stk_nm") String stkNm,
            @JsonProperty("cur_prc") String curPrc,
            @JsonProperty("flu_sig") String fluSig,
            @JsonProperty("pred_pre") String predPre,
            @JsonProperty("flu_rt") String fluRt,
            @JsonProperty("acc_trde_qty") String accTrdeQty,
            @JsonProperty("prm_sell_amt") String prmSellAmt,
            @JsonProperty("prm_buy_amt") String prmBuyAmt,
            @JsonProperty("prm_netprps_amt") String prmNetprpsAmt) {}

    @Override
    public ProgramNetBuyRankingResponse mergeNext(KiwoomResponse next) {
        List<RankingItem> mergedItems = new ArrayList<>(items);
        mergedItems.addAll(((ProgramNetBuyRankingResponse) next).items);
        return new ProgramNetBuyRankingResponse(returnCode, returnMsg, mergedItems);
    }
}
