package dev.eolmae.marketmonitor.external.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.external.kiwoom.KiwoomRequest;

public record Ka10051Request(
        @JsonProperty("mrkt_tp") String mrktTp,
        @JsonProperty("amt_qty_tp") String amtQtyTp,
        @JsonProperty("base_dt") String baseDt,
        @JsonProperty("stex_tp") String stexTp)
        implements KiwoomRequest {

    @Override
    public String path() {
        return "/api/dostk/sect";
    }

    @Override
    public String apiId() {
        return "ka10051";
    }
}
