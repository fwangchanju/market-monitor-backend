package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.domain.stock.client.BaseRequest;

// ka20002: 업종별주가요청
public record SectorPriceListRequest(
        @JsonProperty("mrkt_tp") String mrktTp,
        @JsonProperty("inds_cd") String indsCd,
        @JsonProperty("stex_tp") String stexTp)
        implements BaseRequest {

    @Override
    public String path() {
        return "/api/dostk/sect";
    }

    @Override
    public String apiId() {
        return "ka20002";
    }
}
