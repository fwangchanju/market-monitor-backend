package dev.eolmae.marketmonitor.external.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.external.kiwoom.KiwoomRequest;

public record Ka10099Request(@JsonProperty("mrkt_tp") String mrktTp) implements KiwoomRequest {

    @Override
    public String path() {
        return "/api/dostk/stkinfo";
    }

    @Override
    public String apiId() {
        return "ka10099";
    }
}
