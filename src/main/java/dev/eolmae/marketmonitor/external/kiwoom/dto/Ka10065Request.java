package dev.eolmae.marketmonitor.external.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.external.kiwoom.KiwoomRequest;

public record Ka10065Request(
        @JsonProperty("trde_tp") String trdeTp,
        @JsonProperty("mrkt_tp") String mrktTp,
        @JsonProperty("orgn_tp") String orgnTp,
        @JsonProperty("amt_qty_tp") String amtQtyTp)
        implements KiwoomRequest {

    @Override
    public String path() {
        return "/api/dostk/rkinfo";
    }

    @Override
    public String apiId() {
        return "ka10065";
    }
}
