package dev.eolmae.marketmonitor.external.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Ka10065Response(
        @JsonProperty("opmr_invsr_trde_upper") List<RankingItem> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankingItem(
            @JsonProperty("stk_cd") String stkCd,
            @JsonProperty("stk_nm") String stkNm,
            @JsonProperty("sel_qty") String selQty,
            @JsonProperty("buy_qty") String buyQty,
            @JsonProperty("netslmt") String netslmt) {}
}
