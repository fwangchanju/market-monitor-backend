package dev.eolmae.marketmonitor.domain.krx.dto;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

// TODO: 실제 채용 시점에 KRX 데이터마켓이 주는 요청 구조를 보고 bld별 구체 구현체 생성 필요.
public interface KrxDataMarketRequest {

    String BLD_KEY = "bld";

    String bld();

    /** KRX API는 JSON이 아닌 application/x-www-form-urlencoded를 사용한다. Spring의 폼 인코딩
     * 변환기는 MultiValueMap 형태만 인식해서 폼으로 바꿔주기 때문에(임의 객체를 그대로 보내면 변환기를
     * 못 찾음), 여기서 바로 그 형태로 변환해둔다. */
    default MultiValueMap<String, String> toMultiValueMap() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(BLD_KEY, bld());
        return params;
    }
}
