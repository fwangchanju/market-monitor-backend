package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// ka90003: 프로그램순매수상위50요청
public record ProgramNetBuyRankingRequest(
        @JsonProperty("trde_upper_tp") String trdeUpperTp, // 2=순매수상위, 1=순매도상위
        @JsonProperty("amt_qty_tp") String amtQtyTp, // 1=금액, 2=수량
        @JsonProperty("mrkt_tp") String mrktTp, // P00101=코스피, P10102=코스닥
        @JsonProperty("stex_tp") String stexTp // 3=통합(KRX+NXT)
        ) implements KiwoomRequest {

    @Override
    public String path() {
        return "/api/dostk/stkinfo";
    }

    @Override
    public String apiId() {
        return "ka90003";
    }
}
