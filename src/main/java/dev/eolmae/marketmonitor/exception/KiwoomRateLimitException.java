package dev.eolmae.marketmonitor.exception;

public class KiwoomRateLimitException extends BusinessException {

    public KiwoomRateLimitException(String message) {
        super(message);
    }
}
