package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// ka10014: 공매도추이요청
public record ShortSellingTrendRequest(
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
