package dev.eolmae.marketmonitor.domain.stock.service;

import dev.eolmae.marketmonitor.common.cache.CacheKey;
import dev.eolmae.marketmonitor.common.cache.CacheService;
import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.common.util.KstClock;
import dev.eolmae.marketmonitor.domain.stock.entity.SectorPriceSnapshot;
import dev.eolmae.marketmonitor.domain.stock.properties.MarketHoursProperties;
import dev.eolmae.marketmonitor.domain.stock.repository.SectorPriceSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 당일 종가 기준가 캐시. {@code stock_info} 캐시와 합치지 않는다 — 종목 마스터는 하루 한 번 싱크되고 종가는
 * 15:40에 정해지는 별개 주기라, 한 덩어리로 묶으면 한쪽이 바뀔 때 다른 쪽까지 버리게 된다.
 *
 * <p>적재는 {@code @Cacheable}에 맡긴다(첫 조회 때 DB에서 읽는다). 기동 훅도 스케줄도 따로 두지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClosingPriceCacheService implements CacheService<ClosingPrices> {

    private final SectorPriceSnapshotRepository sectorPriceSnapshotRepository;
    private final MarketHoursProperties marketHours;

    @Override
    @Cacheable(CacheKey.CLOSING_PRICE)
    @Transactional(readOnly = true)
    public ClosingPrices getCache() {
        return loadFor(KstClock.now().toLocalDate());
    }

    @Override
    @CacheEvict(value = CacheKey.CLOSING_PRICE, allEntries = true)
    public void evict() {}

    /**
     * baseDate의 {@code [closeWindowStart, afterHoursStart)} 구간에 있는 스냅샷 중 가장 늦은 시각의 가격을 읽는다.
     *
     * <p>아래쪽 경계가 없으면 안 된다 — "15:40 이전의 latest"라고만 하면 그 구간이 통째로 빈 날에 15:15 같은 장중
     * 가격이 기준가로 잡힌다. 구간을 16:00까지 늘려도 안 된다 — KRX 애프터마켓이 열려서 16:00은 이미 시간외
     * 체결가다. 구간이 빈 날은 기준가가 없는 날로 둔다.
     */
    @Transactional(readOnly = true)
    public ClosingPrices loadFor(LocalDate baseDate) {
        LocalDateTime windowStart = baseDate.atTime(marketHours.closeWindowStart());
        LocalDateTime windowEnd = baseDate.atTime(marketHours.afterHoursStart());

        Optional<SectorPriceSnapshot> latest =
                sectorPriceSnapshotRepository
                        .findFirstBySnapshotTimeGreaterThanEqualAndSnapshotTimeLessThanOrderBySnapshotTimeDesc(
                                windowStart, windowEnd);
        if (latest.isEmpty()) {
            log.warn("종가 윈도우에 스냅샷이 없다: baseDate={}, window=[{}, {})", baseDate, windowStart, windowEnd);
            return new ClosingPrices(baseDate, Map.of());
        }

        LocalDateTime closeSnapshotTime = latest.get().getSnapshotTime();
        Map<String, BigDecimal> priceByStockCode =
                sectorPriceSnapshotRepository
                        .findByMarketTypeInAndSnapshotTime(List.of(Market.values()), closeSnapshotTime)
                        .stream()
                        .collect(Collectors.toUnmodifiableMap(
                                SectorPriceSnapshot::getStockCode, SectorPriceSnapshot::getCurrentPrice));
        log.info(
                "종가 기준가 적재: baseDate={}, snapshotTime={}, 종목 수={}",
                baseDate,
                closeSnapshotTime,
                priceByStockCode.size());
        return new ClosingPrices(baseDate, priceByStockCode);
    }
}
