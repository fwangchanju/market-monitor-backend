package dev.eolmae.marketmonitor.external.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.external.kiwoom.KiwoomRequest;

// ka90013: 종목일별프로그램매매추이요청 (시세 카테고리)
public record Ka90013Request(
        @JsonProperty("stk_cd") String stkCd,
        @JsonProperty("amt_qty_tp") String amtQtyTp // 1=금액
        ) implements KiwoomRequest {

    @Override
    public String path() {
        return "/api/dostk/mrkcond";
    }

    @Override
    public String apiId() {
        return "ka90013";
    }
}
