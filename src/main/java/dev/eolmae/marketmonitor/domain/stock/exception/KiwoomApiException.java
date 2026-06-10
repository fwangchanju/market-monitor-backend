package dev.eolmae.marketmonitor.domain.stock.exception;

import dev.eolmae.marketmonitor.common.exception.BusinessException;

public class KiwoomApiException extends BusinessException {

    public KiwoomApiException(String message) {
        super(message);
    }

    public KiwoomApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
