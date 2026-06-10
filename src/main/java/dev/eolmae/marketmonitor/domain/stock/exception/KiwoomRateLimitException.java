package dev.eolmae.marketmonitor.domain.stock.exception;

import dev.eolmae.marketmonitor.common.exception.BusinessException;

public class KiwoomRateLimitException extends BusinessException {

    public KiwoomRateLimitException(String message) {
        super(message);
    }
}
