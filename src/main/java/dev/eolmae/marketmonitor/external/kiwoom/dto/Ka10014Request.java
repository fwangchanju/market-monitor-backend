package dev.eolmae.marketmonitor.external.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.external.kiwoom.KiwoomRequest;

// ka10014: 공매도추이요청 (공매도 카테고리)
public record Ka10014Request(
        @JsonProperty("stk_cd") String stkCd,
        @JsonProperty("tm_tp") String tmTp, // 2=일별
        @JsonProperty("strt_dt") String strtDt, // yyyyMMdd
        @JsonProperty("end_dt") String endDt // yyyyMMdd
        ) implements KiwoomRequest {

    @Override
    public String path() {
        return "/api/dostk/shsa";
    }

    @Override
    public String apiId() {
        return "ka10014";
    }
}
