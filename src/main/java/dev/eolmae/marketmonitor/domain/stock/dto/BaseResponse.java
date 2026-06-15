package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface BaseResponse {

    @JsonProperty("return_code")
    String returnCode();

    @JsonProperty("return_msg")
    String returnMsg();
}
