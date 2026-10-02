package dev.eolmae.marketmonitor.domain.stock.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 오늘 종가 기준가를 읽는 쪽. 캐시 안의 {@link ClosingPrices#baseDate()}가 오늘이 아니면(20:00에 적재된 어제 값이
 * 다음 날까지 남은 경우) 버리고 다시 적재한다 — 어제 종가로 오늘 등락률을 계산하는 사고를 막는다.
 *
 * <p>오늘 날짜는 호출부가 넘긴다(테스트에서 시각을 고정할 수 있게).
 */
@Service
@RequiredArgsConstructor
public class ClosingPriceReader {

    private final ClosingPriceCacheService closingPriceCacheService;

    public ClosingPrices closingPricesFor(LocalDate today) {
        ClosingPrices cached = closingPriceCacheService.getCache();
        if (cached.isFor(today)) {
            return cached;
        }
        closingPriceCacheService.evict();
        return closingPriceCacheService.getCache();
    }
}
