package dev.eolmae.marketmonitor.domain.stock.exception;

import dev.eolmae.marketmonitor.common.exception.BusinessException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;

public class KiwoomRateLimitException extends BusinessException {

    public KiwoomRateLimitException(ErrorCode errorCode, String... args) {
        super(errorCode, args);
    }
}
