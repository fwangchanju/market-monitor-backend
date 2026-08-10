package dev.eolmae.marketmonitor.domain.krx.enums;

public enum KrxResponseCode {
    LOGIN_SUCCESS("CD001"),
    DUPLICATE_LOGIN("CD011"),
    SESSION_EXPIRED("LOGOUT");

    private final String code;

    KrxResponseCode(String code) {
        this.code = code;
    }

    public boolean includedIn(String body) {
        return body != null && body.contains(code);
    }

    public boolean matches(String body) {
        return body != null && body.trim().equalsIgnoreCase(code);
    }
}
