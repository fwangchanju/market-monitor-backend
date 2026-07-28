package dev.eolmae.marketmonitor.domain.access.util;

/** 오픈 리다이렉트 방지를 위한 내부 경로 검증 유틸리티. */
public final class RedirectPaths {

    private static final String DEFAULT_PATH = "/";

    private RedirectPaths() {}

    /** 안전한 내부 경로면 그대로, 아니면(외부 URL 등) 기본 경로로 대체한다. */
    public static String resolveInternal(String redirectTo) {
        return redirectTo.startsWith("/") && !redirectTo.startsWith("//") ? redirectTo : DEFAULT_PATH;
    }
}
