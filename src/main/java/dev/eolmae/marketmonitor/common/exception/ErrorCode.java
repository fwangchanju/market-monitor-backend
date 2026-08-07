package dev.eolmae.marketmonitor.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 에러 카탈로그(초안). code는 {@link #name()} 사용, message는 간략 유지. */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT("입력값이 올바르지 않습니다."),
    INTERNAL_ERROR("서버 내부 오류가 발생했습니다."),

    // Kiwoom API
    KIWOOM_HTTP_ERROR("키움 API HTTP 오류가 발생했습니다."),
    KIWOOM_RATE_LIMIT("키움 API 호출 한도를 초과했습니다."),
    KIWOOM_RESPONSE_PARSE_FAILED("키움 API 응답 파싱에 실패했습니다."),
    KIWOOM_ERROR_RESPONSE("키움 API가 오류 응답을 반환했습니다."),
    KIWOOM_TOKEN_ISSUE_FAILED("키움 토큰 발급에 실패했습니다."),

    // KRX
    KRX_LOGIN_FAILED("KRX 로그인에 실패했습니다."),
    KRX_SESSION_EXPIRED("KRX 세션이 만료되었습니다."),
    KRX_RESPONSE_INVALID("KRX 응답 구조가 올바르지 않습니다."),

    // Stock 수집
    STOCK_INFO_SYNC_FAILED("종목 기준정보 동기화에 실패했습니다."),
    PREV_MARKET_CAPITALIZATION_ZERO("유효 종목이 없어 지수기여도 연산에 실패했습니다."),
    COMPOSITE_INDEX_ROW_NOT_FOUND("투자자별매매종합 종합지수 행을 찾을 수 없습니다."),
    BASE_SNAPSHOT_NOT_FOUND("연산 기준 스냅샷 데이터가 없습니다."),

    // Market summary
    RENDERER_DISABLED("렌더러가 비활성 상태입니다."),
    SCREENSHOT_CAPTURE_FAILED("스크린샷 캡처에 실패했습니다."),
    TELEGRAM_MESSAGE_SEND_FAILED("텔레그램 메시지 발송에 실패했습니다."),
    TELEGRAM_IMAGE_SEND_FAILED("텔레그램 이미지 발송에 실패했습니다."),

    // Market map
    CATEGORY_TREE_SERIALIZE_FAILED("카테고리 트리 직렬화에 실패했습니다."),
    CATEGORY_TREE_PARSE_FAILED("카테고리 스냅샷 파싱에 실패했습니다."),
    CATEGORY_NOT_FOUND("이미 삭제되었거나 존재하지 않는 카테고리입니다."),
    CATEGORY_NAME_DUPLICATE("이미 사용 중인 카테고리명입니다."),
    CATEGORY_CIRCULAR_REFERENCE("해당 위치로 카테고리 위치 변경이 불가능합니다."),
    CATEGORY_HAS_ASSIGNED_STOCK("배정된 종목이 있어 삭제할 수 없습니다."),
    VERSION_NOT_FOUND("이미 삭제되었거나 존재하지 않는 버전입니다.");

    private final String message;
}
