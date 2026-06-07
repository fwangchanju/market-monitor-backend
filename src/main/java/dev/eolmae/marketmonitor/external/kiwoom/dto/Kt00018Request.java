package dev.eolmae.marketmonitor.external.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.external.kiwoom.KiwoomRequest;

public record Kt00018Request(
        @JsonProperty("qry_tp") String qryTp,
        @JsonProperty("dmst_stex_tp") String dmstStexTp) implements KiwoomRequest {

    public static Kt00018Request defaults() {
        return new Kt00018Request("1", "KRX");
    }

    @Override
    public String path() {
        return "/api/dostk/acnt";
    }

    @Override
    public String apiId() {
        return "kt00018";
    }
}
