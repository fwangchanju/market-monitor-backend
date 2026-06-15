package dev.eolmae.marketmonitor.domain.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// ka10051: 업종별투자자순매수요청
@JsonIgnoreProperties(ignoreUnknown = true)
public record SectorInvestorNetBuyResponse(
        @JsonProperty("return_code") String returnCode,
        @JsonProperty("return_msg") String returnMsg,
        @JsonProperty("inds_netprps") List<IndsNetprps> indsNetprps)
        implements BaseResponse {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IndsNetprps(
            @JsonProperty("inds_cd") String indsCd,
            @JsonProperty("ind_netprps") String indNetprps, // 개인
            @JsonProperty("frgnr_netprps") String frgnrNetprps, // 외국인
            @JsonProperty("orgn_netprps") String orgnNetprps, // 기관계
            @JsonProperty("sc_netprps") String scNetprps, // 금융투자
            @JsonProperty("invtrt_netprps") String invtrtNetprps, // 투신
            @JsonProperty("endw_netprps") String endwNetprps, // 연기금등
            @JsonProperty("samo_fund_netprps") String samoFundNetprps, // 사모펀드
            @JsonProperty("insrnc_netprps") String insrncNetprps, // 보험
            @JsonProperty("bank_netprps") String bankNetprps, // 은행
            @JsonProperty("etc_corp_netprps") String etcCorpNetprps, // 기타법인
            @JsonProperty("natn_netprps") String natnNetprps, // 국가/지자체
            @JsonProperty("jnsinkm_netprps") String jnsinkmNetprps, // 기타금융
            @JsonProperty("native_trmt_frgnr_netprps") String nativeTrmtFrgnrNetprps // 외국계
            ) {}
}
