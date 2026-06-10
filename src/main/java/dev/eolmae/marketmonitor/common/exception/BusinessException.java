package dev.eolmae.marketmonitor.common.exception;

import lombok.Getter;

public class BusinessException extends RuntimeException {

    @Getter
    private final ErrorCode errorCode;

    private final String[] args;

    private static final String LOG_DELIMITER = "|";

    public BusinessException(ErrorCode errorCode, String... args) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.args = args;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause, String... args) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.args = args;
    }

    public String createMessage() {

        if (args == null || args.length == 0) {

            return String.format("[%s] %s", errorCode.name(), getMessage());
        } else {

            String context = String.join(LOG_DELIMITER, args);
            return String.format("[%s] %s | context : %s", errorCode.name(), getMessage(), context);
        }
    }
}
