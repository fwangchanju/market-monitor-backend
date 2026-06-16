package dev.eolmae.marketmonitor.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 에러 카탈로그(초안). code는 {@link #name()} 사용, message는 간략 유지. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT("입력값이 올바르지 않습니다."),
    INTERNAL_ERROR("서버 내부 오류."),

    // Kiwoom API
    KIWOOM_HTTP_ERROR("키움 API HTTP 오류."),
    KIWOOM_RATE_LIMIT("키움 API 호출 한도 초과."),
    KIWOOM_RESPONSE_PARSE_FAILED("키움 API 응답 파싱 실패."),
    KIWOOM_ERROR_RESPONSE("키움 API 오류 응답."),
    KIWOOM_TOKEN_ISSUE_FAILED("키움 토큰 발급 실패."),

    // KRX
    KRX_LOGIN_FAILED("KRX 로그인 실패."),
    KRX_SESSION_EXPIRED("KRX 세션 만료."),
    KRX_RESPONSE_INVALID("KRX 응답 구조 이상."),

    // Stock 수집
    STOCK_INFO_SYNC_FAILED("종목 마스터 동기화 실패."),
    PREV_MARKET_CAP_ZERO("전일 전체 시가총액 합산이 0."),
    BASE_SNAPSHOT_NOT_FOUND("연산 기준 스냅샷 데이터 없음."),

    // Dashboard
    RENDERER_DISABLED("렌더러가 비활성 상태입니다."),
    SCREENSHOT_CAPTURE_FAILED("스크린샷 캡처 실패."),
    TELEGRAM_MESSAGE_SEND_FAILED("텔레그램 메시지 발송 실패."),
    TELEGRAM_IMAGE_SEND_FAILED("텔레그램 이미지 발송 실패.");

    private final String message;
}
