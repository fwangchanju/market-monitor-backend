package dev.eolmae.marketmonitor.domain.notification.enums;

// 캡처할 프론트 페이지와 그 안에서 스크린샷 찍을 영역(data-captureid, 프론트와 값 합의)
public enum RenderTarget {
    MARKET_SUMMARY("/market-summary", "market-summary-capture"),
    MARKET_MAP("/market-map", "market-map-capture"),
    CATEGORY_CHANGE_RATE("/category-change-rate", "category-change-rate-capture");

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
}
