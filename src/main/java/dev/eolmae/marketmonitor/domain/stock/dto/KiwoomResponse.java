package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KiwoomResponse<T>(
        @JsonProperty("rt_cd") String returnCode,
        @JsonProperty("msg1") String returnMsg,
        T data) {}
