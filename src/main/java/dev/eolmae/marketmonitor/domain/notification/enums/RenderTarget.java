package dev.eolmae.marketmonitor.domain.notification.enums;

import java.util.Locale;

// 캡처할 프론트 페이지와 그 안에서 스크린샷 찍을 영역(data-captureid, 프론트와 값 합의)
public enum RenderTarget {
    MARKET_SUMMARY("/summary", "market-summary-capture"),
    MARKET_MAP("/map", "market-map-capture"),
    CATEGORY_CHANGE_RATE("/sector", "category-change-rate-capture");

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

    // 캡처 경로의 마켓 세그먼트(kospi/kosdaq/allstock)로 변환한다. Market/MarketQuery 양쪽 enum name을
    // 그대로 받는다 — MarketQuery.ALL_STOCK처럼 상수명에 "_"가 있어도 프론트 라우트(/map/allstock,
    // PR #59)엔 없으므로 제거한다.
    public static String marketSegment(String marketEnumName) {
        return marketEnumName.toLowerCase(Locale.ROOT).replace("_", "");
    }
}
