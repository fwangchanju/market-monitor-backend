package dev.eolmae.marketmonitor.external.kiwoom.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.eolmae.marketmonitor.external.kiwoom.KiwoomRequest;

public record Ka20002Request(
	@JsonProperty("mrkt_tp") String mrktTp,
	@JsonProperty("inds_cd") String indsCd,
	@JsonProperty("stex_tp") String stexTp
) implements KiwoomRequest {

	@Override
	public String path() {
		return "/api/dostk/sect";
	}

	@Override
	public String apiId() {
		return "ka20002";
	}
}
