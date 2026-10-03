package dev.eolmae.marketmonitor.domain.stock.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 종가 기준가를 읽는 쪽.
 *
 * <p>오늘 종가는 {@link ClosingPriceCacheService}의 캐시를 쓴다. 캐시 안의 {@link ClosingPrices#baseDate()}가 오늘이 아니면
 * (20:00에 적재된 어제 값이 다음 날까지 남은 경우) 버리고 다시 적재한다 — 어제 종가로 오늘 등락률을 계산하는 사고를 막는다.
 *
 * <p>지난 날짜의 종가는 주말·휴장일처럼 장이 끝난 뒤 다음 개장까지 마지막 스냅샷(전 거래일)을 계속 보여줄 때 필요하다. 지난
 * 날짜의 종가는 바뀌지 않으므로 날짜별로 기억해 두고, 오래된 것부터 버려 최근 몇 날짜만 남긴다. 기준가가 비어 있는 결과는
 * 기억하지 않는다(나중에 데이터가 채워질 수 있다).
 *
 * <p>오늘 날짜는 호출부가 넘긴다(테스트에서 시각을 고정할 수 있게).
 */
@Service
@RequiredArgsConstructor
public class ClosingPriceReader {

    private static final int REMEMBERED_PAST_DATES = 3;

    private final ClosingPriceCacheService closingPriceCacheService;
    private final Map<LocalDate, ClosingPrices> pastClosingPrices = new ConcurrentHashMap<>();

    public ClosingPrices closingPricesFor(LocalDate baseDate, LocalDate today) {
        ClosingPrices cached = closingPriceCacheService.getCache();
        if (cached.isFor(baseDate)) {
            return cached;
        }
        if (baseDate.equals(today)) {
            closingPriceCacheService.evict();
            return closingPriceCacheService.getCache();
        }
        return pastClosingPricesFor(baseDate);
    }

    private ClosingPrices pastClosingPricesFor(LocalDate baseDate) {
        ClosingPrices remembered = pastClosingPrices.get(baseDate);
        if (remembered != null) {
            return remembered;
        }
        ClosingPrices loaded = closingPriceCacheService.loadFor(baseDate);
        if (loaded.priceByStockCode().isEmpty()) {
            return loaded;
        }
        rememberPastDate(baseDate, loaded);
        return loaded;
    }

    private void rememberPastDate(LocalDate baseDate, ClosingPrices closingPrices) {
        pastClosingPrices.put(baseDate, closingPrices);
        while (pastClosingPrices.size() > REMEMBERED_PAST_DATES) {
            pastClosingPrices.keySet().stream().min(LocalDate::compareTo).ifPresent(pastClosingPrices::remove);
        }
    }
}
