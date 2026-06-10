package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.domain.stock.client.BaseRequest;

// ka10065: 장중투자자별매매상위요청
public record IntradayInvestorRankingRequest(
        @JsonProperty("trde_tp") String trdeTp,
        @JsonProperty("mrkt_tp") String mrktTp,
        @JsonProperty("orgn_tp") String orgnTp,
        @JsonProperty("amt_qty_tp") String amtQtyTp)
        implements BaseRequest {

    @Override
    public String path() {
        return "/api/dostk/rkinfo";
    }

    @Override
    public String apiId() {
        return "ka10065";
    }
}
