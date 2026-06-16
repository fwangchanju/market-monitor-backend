package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// ka90013: 종목일별프로그램매매추이요청
@JsonIgnoreProperties(ignoreUnknown = true)
public record DailyProgramTradeTrendResponse(
        @JsonProperty("return_code") String returnCode,
        @JsonProperty("return_msg") String returnMsg,
        @JsonProperty("stk_daly_prm_trde_trnsn") List<DailyTick> ticks)
        implements KiwoomResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DailyTick(
            @JsonProperty("dt") String dt,
            @JsonProperty("cur_prc") String curPrc,
            @JsonProperty("pre_sig") String preSig,
            @JsonProperty("pred_pre") String predPre,
            @JsonProperty("flu_rt") String fluRt,
            @JsonProperty("trde_qty") String trdeQty,
            @JsonProperty("prm_sell_amt") String prmSellAmt,
            @JsonProperty("prm_buy_amt") String prmBuyAmt,
            @JsonProperty("prm_netprps_amt") String prmNetprpsAmt,
            @JsonProperty("prm_netprps_amt_irds") String prmNetprpsAmtIrds,
            @JsonProperty("prm_sell_qty") String prmSellQty,
            @JsonProperty("prm_buy_qty") String prmBuyQty,
            @JsonProperty("prm_netprps_qty") String prmNetprpsQty,
            @JsonProperty("prm_netprps_qty_irds") String prmNetprpsQtyIrds,
            @JsonProperty("stex_tp") String stexTp) {}
}
