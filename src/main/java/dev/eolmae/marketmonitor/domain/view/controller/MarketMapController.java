package dev.eolmae.marketmonitor.domain.view.controller;

import dev.eolmae.marketmonitor.domain.custom.dto.CustomScaleResponse;
import dev.eolmae.marketmonitor.domain.custom.dto.CustomValueTierItem;
import dev.eolmae.marketmonitor.domain.custom.service.CustomScaleService;
import dev.eolmae.marketmonitor.domain.custom.service.CustomSectorService;
import dev.eolmae.marketmonitor.domain.custom.service.CustomValueTierThresholdService;
import dev.eolmae.marketmonitor.domain.view.dto.MarketMapResponse;
import dev.eolmae.marketmonitor.domain.view.enums.MarketQuery;
import dev.eolmae.marketmonitor.domain.view.service.MarketMapQueryService;
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
            @RequestParam boolean isCustom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime snapshotTime) {
        return isCustom
                ? marketMapQueryService.getCustomMarketMap(market, snapshotTime)
                : marketMapQueryService.getDefaultMarketMap(market, snapshotTime);
    }

    @GetMapping("/value-tiers")
    public List<CustomValueTierItem> getValueTiers() {
        return customValueTierThresholdService.getDefaultValueTiers();
    }

    @GetMapping("/scale")
    public CustomScaleResponse getScale() {
        return customScaleService.getDefaultScale();
    }

    @PostMapping("/excluded-categories/{categoryId}")
    public void registerExcludedCategory(@PathVariable Long categoryId) {
        customSectorService.exclude(categoryId);
    }

    @DeleteMapping("/excluded-categories/{categoryId}")
    public void unregisterExcludedCategory(@PathVariable Long categoryId) {
        customSectorService.include(categoryId);
    }

    @DeleteMapping("/excluded-categories")
    public void deleteExcludedCategories() {
        customSectorService.resetExcludes();
    }

    @DeleteMapping("/reset")
    public void resetMarketMapCustomizations() {
        customSectorService.resetExcludes();
    }
}
