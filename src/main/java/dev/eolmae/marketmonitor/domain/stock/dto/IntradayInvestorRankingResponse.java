package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// ka10065: 장중투자자별매매상위요청
@JsonIgnoreProperties(ignoreUnknown = true)
public record IntradayInvestorRankingResponse(
        @JsonProperty("return_code") String returnCode,
        @JsonProperty("return_msg") String returnMsg,
        @JsonProperty("opmr_invsr_trde_upper") List<RankingItem> items)
        implements BaseResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankingItem(
            @JsonProperty("stk_cd") String stkCd,
            @JsonProperty("stk_nm") String stkNm,
            @JsonProperty("sel_qty") String selQty,
            @JsonProperty("buy_qty") String buyQty,
            @JsonProperty("netslmt") String netslmt) {}
}
