package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.domain.stock.client.BaseRequest;

// ka00018: 계좌평가잔고내역요청
public record AccountBalanceRequest(
        @JsonProperty("qry_tp") String qryTp,
        @JsonProperty("dmst_stex_tp") String dmstStexTp) implements BaseRequest {

    public static AccountBalanceRequest defaults() {
        return new AccountBalanceRequest("1", "KRX");
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
