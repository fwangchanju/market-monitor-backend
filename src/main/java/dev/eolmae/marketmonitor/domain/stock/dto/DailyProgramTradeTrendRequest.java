package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// ka90013: 종목일별프로그램매매추이요청
public record DailyProgramTradeTrendRequest(
        @JsonProperty("stk_cd") String stkCd,
        @JsonProperty("amt_qty_tp") String amtQtyTp // 1=금액
        ) implements BaseRequest {

    @Override
    public String path() {
        return "/api/dostk/mrkcond";
    }

    @Override
    public String apiId() {
        return "ka90013";
    }
}
