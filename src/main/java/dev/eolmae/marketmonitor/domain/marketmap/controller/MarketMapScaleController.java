package dev.eolmae.marketmonitor.domain.marketmap.controller;

import dev.eolmae.marketmonitor.domain.marketmap.dto.ScaleThresholdItem;
import dev.eolmae.marketmonitor.domain.marketmap.dto.ScaleThresholdRequest;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapScaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/admin/market-map/scale")
@RestController
@RequiredArgsConstructor
public class MarketMapScaleController {

    private final MarketMapScaleService marketMapScaleService;

    @PostMapping
    public ScaleThresholdItem create(@RequestBody @Valid ScaleThresholdRequest request) {
        return marketMapScaleService.createThreshold(request);
    }

    @PutMapping("/{id}")
    public ScaleThresholdItem update(@PathVariable Long id, @RequestBody @Valid ScaleThresholdRequest request) {
        return marketMapScaleService.updateThreshold(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        marketMapScaleService.deleteThreshold(id);
    }
}
