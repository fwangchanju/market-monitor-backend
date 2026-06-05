package dev.eolmae.marketmonitor.external.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Ka20002Response(
	@JsonProperty("inds_stkpc") List<StockItem> items
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record StockItem(
		@JsonProperty("stk_cd") String stkCd,
		@JsonProperty("stk_nm") String stkNm,
		@JsonProperty("cur_prc") String curPrc,
		@JsonProperty("pred_pre_sig") String predPreSig,
		@JsonProperty("pred_pre") String predPre,
		@JsonProperty("flu_rt") String fluRt
	) {}
}
