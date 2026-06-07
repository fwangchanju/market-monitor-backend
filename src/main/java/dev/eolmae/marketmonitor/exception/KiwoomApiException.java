package dev.eolmae.marketmonitor.exception;

public class KiwoomApiException extends BusinessException {

    public KiwoomApiException(String message) {
        super(message);
    }

    public KiwoomApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
