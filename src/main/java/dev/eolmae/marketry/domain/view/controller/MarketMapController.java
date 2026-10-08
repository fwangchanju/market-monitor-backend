package dev.eolmae.marketry.domain.view.controller;

import dev.eolmae.marketry.domain.custom.dto.CustomScaleResponse;
import dev.eolmae.marketry.domain.custom.dto.CustomValueTierItem;
import dev.eolmae.marketry.domain.custom.service.CustomScaleService;
import dev.eolmae.marketry.domain.custom.service.CustomSectorService;
import dev.eolmae.marketry.domain.custom.service.CustomValueTierThresholdService;
import dev.eolmae.marketry.domain.view.dto.MarketMapResponse;
import dev.eolmae.marketry.domain.view.enums.ChangeRateBasis;
import dev.eolmae.marketry.domain.view.enums.ClassificationSource;
import dev.eolmae.marketry.domain.view.enums.MarketQuery;
import dev.eolmae.marketry.domain.view.service.MarketMapQueryService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/map")
@RestController
@RequiredArgsConstructor
public class MarketMapController {

    private final MarketMapQueryService marketMapQueryService;
    private final CustomSectorService customSectorService;
    private final CustomScaleService customScaleService;
    private final CustomValueTierThresholdService customValueTierThresholdService;

    @GetMapping
    public MarketMapResponse getMarketMap(
            @RequestParam MarketQuery market,
            @RequestParam(defaultValue = "false") boolean isCustom,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime snapshotTime,
            @RequestParam(defaultValue = "false") boolean nxtOnly,
            @RequestParam(defaultValue = "daily") String basis) {
        // nxtOnly는 모든 분류에서 NXT 거래 가능 종목만 남긴다.
        // basis=afterHours면 등락률을 그날 정규장 종가 대비로 계산한다(그 스냅샷 날짜의 15:40 이후에만 적용).
        // source가 없으면 옛 프런트 호환으로 isCustom(true=내 분류, false=거래소)을 따른다.
        ChangeRateBasis changeRateBasis = ChangeRateBasis.parse(basis);
        return switch (ClassificationSource.resolve(source, isCustom)) {
            case KRX -> marketMapQueryService.getDefaultMarketMap(market, snapshotTime, nxtOnly, changeRateBasis);
            case MARKETRY ->
                marketMapQueryService.getPublishedMarketMap(market, snapshotTime, nxtOnly, changeRateBasis);
            case MYMAP -> marketMapQueryService.getCustomMarketMap(market, snapshotTime, nxtOnly, changeRateBasis);
        };
    }

    @GetMapping("/value-tiers")
    public List<CustomValueTierItem> getValueTiers() {
        return customValueTierThresholdService.getDefaultValueTiers();
    }

    @GetMapping("/scale")
    public CustomScaleResponse getScale() {
        return customScaleService.getDefaultScale();
    }

    @PostMapping("/excluded-sectors/{sectorId}")
    public void registerExcludedSector(@PathVariable Long sectorId) {
        customSectorService.exclude(sectorId);
    }

    @DeleteMapping("/excluded-sectors/{sectorId}")
    public void unregisterExcludedSector(@PathVariable Long sectorId) {
        customSectorService.include(sectorId);
    }

    @DeleteMapping("/excluded-sectors")
    public void deleteExcludedSectors() {
        customSectorService.resetExcludes();
    }

    @DeleteMapping("/reset")
    public void resetMarketMapCustomizations() {
        customSectorService.resetExcludes();
    }
}
