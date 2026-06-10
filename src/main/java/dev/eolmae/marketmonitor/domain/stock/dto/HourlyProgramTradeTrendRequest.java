package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.domain.stock.client.BaseRequest;

// ka90008: 종목시간별프로그램매매추이요청
public record HourlyProgramTradeTrendRequest(
        @JsonProperty("stk_cd") String stkCd,
        @JsonProperty("amt_qty_tp") String amtQtyTp, // 1=금액
        @JsonProperty("date") String date // yyyyMMdd
        ) implements BaseRequest {

    @Override
    public String path() {
        return "/api/dostk/mrkcond";
    }

    @Override
    public String apiId() {
        return "ka90008";
    }
}
