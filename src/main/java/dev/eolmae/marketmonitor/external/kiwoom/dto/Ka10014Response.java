package dev.eolmae.marketmonitor.external.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Ka10014Response(@JsonProperty("shrts_trnsn") List<ShortTick> ticks) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShortTick(
            @JsonProperty("dt") String dt,
            @JsonProperty("close_pric") String closePric,
            @JsonProperty("pred_pre_sig") String predPreSig,
            @JsonProperty("pred_pre") String predPre,
            @JsonProperty("flu_rt") String fluRt,
            @JsonProperty("trde_qty") String trdeQty,
            @JsonProperty("shrts_qty") String shrtsQty,
            @JsonProperty("ovr_shrts_qty") String ovrShrtsQty,
            @JsonProperty("trde_wght") String trdeWght,
            @JsonProperty("shrts_trde_prica") String shrtsTrdePrica,
            @JsonProperty("shrts_avg_pric") String shrtsAvgPric) {}
}
