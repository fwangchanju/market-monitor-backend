package dev.eolmae.marketmonitor.common.enums;

import java.math.BigDecimal;

/** 시가총액 구간 분류 — 초대형주(200조 이상) / 대형주(5조~200조) / 중형주(5천억~5조) / 소형주(5천억 미만) */
public enum MarketValueTier {
    MEGA(200_000_000_000_000L),
    LARGE(5_000_000_000_000L),
    MID(500_000_000_000L),
    SMALL(0L);

    private final long threshold;

    MarketValueTier(long threshold) {
        this.threshold = threshold;
    }

    public static MarketValueTier from(BigDecimal totalMarketValue) {
        long value = totalMarketValue.longValue();
        if (value < MID.threshold) {
            return SMALL;
        }
        if (value < LARGE.threshold) {
            return MID;
        }
        if (value < MEGA.threshold) {
            return LARGE;
        }
        return MEGA;
    }
}
