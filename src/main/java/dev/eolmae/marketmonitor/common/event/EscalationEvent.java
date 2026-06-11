package dev.eolmae.marketmonitor.common.event;

/** 에스컬레이션(개발자 즉시 인지) 알림 이벤트. common이 텔레그램 발송을 직접 의존하지 않도록 분리. */
public record EscalationEvent(String message) {}
