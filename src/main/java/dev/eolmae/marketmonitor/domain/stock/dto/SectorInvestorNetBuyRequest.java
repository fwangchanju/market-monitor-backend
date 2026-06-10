package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.domain.stock.client.BaseRequest;

// ka10051: 업종별투자자순매수요청
public record SectorInvestorNetBuyRequest(
        @JsonProperty("mrkt_tp") String mrktTp,
        @JsonProperty("amt_qty_tp") String amtQtyTp,
        @JsonProperty("base_dt") String baseDt,
        @JsonProperty("stex_tp") String stexTp)
        implements BaseRequest {

    @Override
    public String path() {
        return "/api/dostk/sect";
    }

    @Override
    public String apiId() {
        return "ka10051";
    }
}
