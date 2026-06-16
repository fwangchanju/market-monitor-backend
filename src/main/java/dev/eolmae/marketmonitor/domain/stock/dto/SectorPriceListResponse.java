package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// ka20002: 업종별주가요청
@JsonIgnoreProperties(ignoreUnknown = true)
public record SectorPriceListResponse(
        @JsonProperty("return_code") String returnCode,
        @JsonProperty("return_msg") String returnMsg,
        @JsonProperty("inds_stkpc") List<StockItem> items)
        implements KiwoomResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockItem(
            @JsonProperty("stk_cd") String stkCd,
            @JsonProperty("stk_nm") String stkNm,
            @JsonProperty("cur_prc") String curPrc,
            @JsonProperty("pred_pre_sig") String predPreSig,
            @JsonProperty("pred_pre") String predPre,
            @JsonProperty("flu_rt") String fluRt) {}
}
