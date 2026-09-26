package dev.eolmae.marketmonitor.domain.notification.enums;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import java.util.Locale;

// 캡처할 프론트 페이지와 그 안에서 스크린샷 찍을 영역(data-captureid, 프론트와 값 합의)
public enum RenderTarget {
    SUMMARY("/summary", "summary-capture"),
    MAP("/map", "map-capture"),
    SECTOR("/sector", "sector-capture");

    private static final String CAPTURE_ATTRIBUTE = "data-captureid";

    private final String path;
    private final String captureId;

    RenderTarget(String path, String captureId) {
        this.path = path;
        this.captureId = captureId;
    }

    public String path() {
        return path;
    }

    public String selector() {
        return "[" + CAPTURE_ATTRIBUTE + "='" + captureId + "']";
    }

    // 캡처 경로의 마켓 세그먼트(kospi/kosdaq/allstock). 마켓 하나(Market)와 조회 단위(MarketQuery,
    // ALL_STOCK 포함) 둘 다 캡처 대상이 되므로 오버로드로 받는다 — 아무 문자열이나 넘기지 못하게.
    public static String marketSegment(Market market) {
        return toSegment(market.name());
    }

    public static String marketSegment(MarketQuery query) {
        return toSegment(query.name());
    }

    // MarketQuery.ALL_STOCK처럼 상수명에 "_"가 있어도 프론트 라우트(/map/allstock, PR #59)엔 없으므로 제거한다.
    private static String toSegment(String enumName) {
        return enumName.toLowerCase(Locale.ROOT).replace("_", "");
    }
}
