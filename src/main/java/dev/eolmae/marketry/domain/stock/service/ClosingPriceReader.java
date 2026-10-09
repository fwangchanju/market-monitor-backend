package dev.eolmae.marketry.domain.stock.service;

import dev.eolmae.marketry.common.util.KstClock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 현재 날짜와 무관하게 스냅샷의 날짜에 귀속된 종가를 읽는다. */
@Service
@RequiredArgsConstructor
public class ClosingPriceReader {

    private final ClosingPriceCacheService closingPriceCacheService;

    public ClosingPrices closingPricesFor(LocalDate baseDate, LocalDate today) {
        return closingPriceCacheService.getFor(baseDate, KstClock.now());
    }
}
