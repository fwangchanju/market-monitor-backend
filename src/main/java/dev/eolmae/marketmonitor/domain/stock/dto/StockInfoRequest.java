package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.domain.stock.client.BaseRequest;

// ka10099: 종목정보 리스트
public record StockInfoRequest(@JsonProperty("mrkt_tp") String mrktTp) implements BaseRequest {

    @Override
    public String path() {
        return "/api/dostk/stkinfo";
    }

    @Override
    public String apiId() {
        return "ka10099";
    }
}
