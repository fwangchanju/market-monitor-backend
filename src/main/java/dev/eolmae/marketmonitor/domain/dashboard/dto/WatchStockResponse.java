package dev.eolmae.marketmonitor.domain.dashboard.dto;

import dev.eolmae.marketmonitor.common.enums.Market;

public record WatchStockResponse(
        String stockCode,
        String stockName,
        Market marketType,

        // DB WatchStock.isPrimary
        boolean isPrimaryByUser,

        // 실제 화면에서 isPrimary 로 보일 종목 여부
        boolean isPrimary) {}
