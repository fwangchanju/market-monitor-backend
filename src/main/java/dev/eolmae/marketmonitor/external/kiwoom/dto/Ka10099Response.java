package dev.eolmae.marketmonitor.external.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Ka10099Response(@JsonProperty("list") List<StockItem> list) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockItem(
            @JsonProperty("code") String code,
            @JsonProperty("name") String name,
            @JsonProperty("marketCode") String marketCode,
            @JsonProperty("listCount") String listCount,
            @JsonProperty("lastPrice") String lastPrice) {}
}
