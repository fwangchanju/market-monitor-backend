package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// ka00018: 계좌평가잔고내역요청
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountBalanceResponse(
        @JsonProperty("acnt_evlt_remn_indv_tot") List<HoldingItem> holdings) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HoldingItem(
            @JsonProperty("stk_cd") String stkCd,
            @JsonProperty("stk_nm") String stkNm,
            @JsonProperty("pur_amt") String purAmt,
            @JsonProperty("evlt_amt") String evltAmt,
            @JsonProperty("poss_rt") String possRt) {
        public String stockCode() {
            return stkCd != null && stkCd.startsWith("A") ? stkCd.substring(1) : stkCd;
        }
    }
}
