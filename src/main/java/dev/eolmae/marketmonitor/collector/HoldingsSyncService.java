package dev.eolmae.marketmonitor.collector;

import dev.eolmae.marketmonitor.common.util.NumberParser;
import dev.eolmae.marketmonitor.domain.stock.HoldingsCache;
import dev.eolmae.marketmonitor.domain.stock.RegisterBy;
import dev.eolmae.marketmonitor.domain.stock.WatchStock;
import dev.eolmae.marketmonitor.domain.stock.WatchStockCacheService;
import dev.eolmae.marketmonitor.domain.stock.repository.StockMasterRepository;
import dev.eolmae.marketmonitor.domain.stock.repository.WatchStockRepository;
import dev.eolmae.marketmonitor.domain.user.AppUser;
import dev.eolmae.marketmonitor.domain.user.repository.AppUserRepository;
import dev.eolmae.marketmonitor.external.kiwoom.client.KiwoomApiClient;
import dev.eolmae.marketmonitor.external.kiwoom.dto.Kt00018Request;
import dev.eolmae.marketmonitor.external.kiwoom.dto.Kt00018Response;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingsSyncService {

    private final KiwoomApiClient kiwoomApiClient;
    private final AppUserRepository appUserRepository;
    private final StockMasterRepository stockMasterRepository;
    private final WatchStockRepository watchStockRepository;
    private final WatchStockCacheService watchStockCacheService;
    private final HoldingsCache holdingsCache;

    /**
     * kt00018 보유종목 동기화.
     * - 신규 보유종목 → watch_stock에 HOLDINGS로 등록
     * - 기존 HOLDINGS 중 보유 없는 종목 → 삭제
     * - 보유종목 목록(비중 포함) 반환 — 캐시 저장용
     */
    @Transactional
    public List<Kt00018Response.HoldingItem> sync() {
        AppUser user = appUserRepository.findAll().stream().findFirst().orElse(null);
        if (user == null) {
            log.warn("보유종목 동기화 스킵: 등록된 사용자 없음");
            return List.of();
        }

        Kt00018Response response = kiwoomApiClient.post(Kt00018Request.defaults(), Kt00018Response.class);
        if (response.holdings() == null || response.holdings().isEmpty()) {
            log.info("보유종목 없음 — HOLDINGS 전체 삭제");
            watchStockRepository.deleteAll(watchStockRepository.findByRegisterBy(RegisterBy.HOLDINGS));
            return List.of();
        }

        // 동일 종목 여러 행(복수 매수 로트) → stockCode 기준 dedup, poss_rt 합산
        Map<String, Double> holdingsMap = response.holdings().stream()
                .collect(Collectors.toMap(
                        Kt00018Response.HoldingItem::stockCode, h -> parseDouble(h.possRt()), Double::sum));

        Set<String> currentHoldingCodes = holdingsMap.keySet();

        // 보유 없는 HOLDINGS 삭제
        watchStockRepository.findByRegisterBy(RegisterBy.HOLDINGS).stream()
                .filter(ws -> !currentHoldingCodes.contains(ws.getStock().getStockCode()))
                .forEach(watchStockRepository::delete);

        // 신규 보유종목 등록
        for (String stockCode : currentHoldingCodes) {
            if (watchStockRepository.existsByUserUserKeyAndStockStockCode(user.getUserKey(), stockCode)) {
                continue;
            }
            stockMasterRepository
                    .findById(stockCode)
                    .ifPresentOrElse(
                            stock -> watchStockRepository.save(WatchStock.create(user, stock, RegisterBy.HOLDINGS)),
                            () -> log.warn("보유종목이 종목마스터에 없음: stockCode={}", stockCode));
        }

        watchStockCacheService.evict();
        log.info("보유종목 동기화 완료: 보유종목={}", currentHoldingCodes);

        // 평가금액(현재 가치) 기준 정렬된 보유종목 목록 → holdingsCache 갱신 및 반환
        // 비중(poss_rt)은 계좌 내 상대값이라 여러 계좌(app key)를 동시에 쓰게 되면 비교 기준이 될 수 없음
        List<Kt00018Response.HoldingItem> sorted = response.holdings().stream()
                .collect(Collectors.toMap(
                        Kt00018Response.HoldingItem::stockCode, h -> h, (a, b) -> a // dedup — 첫 번째 항목 유지
                        ))
                .values()
                .stream()
                .sorted((a, b) ->
                        NumberParser.parseBigDecimal(b.evltAmt()).compareTo(NumberParser.parseBigDecimal(a.evltAmt())))
                .toList();
        holdingsCache.update(sorted);
        return sorted;
    }

    private static double parseDouble(String value) {
        try {
            return value == null ? 0.0 : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
