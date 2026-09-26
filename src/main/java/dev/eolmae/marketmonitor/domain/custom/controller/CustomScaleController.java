package dev.eolmae.marketmonitor.domain.custom.controller;

import dev.eolmae.marketmonitor.domain.auth.service.CurrentUser;
import dev.eolmae.marketmonitor.domain.custom.dto.ScaleThresholdItem;
import dev.eolmae.marketmonitor.domain.custom.dto.ScaleThresholdRequest;
import dev.eolmae.marketmonitor.domain.custom.service.CustomScaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/custom/scale")
@RestController
@RequiredArgsConstructor
public class CustomScaleController {

    private final CustomScaleService customScaleService;

    @GetMapping
    public dev.eolmae.marketmonitor.domain.custom.dto.CustomScaleResponse getScale() {
        return customScaleService.getUserScale(CurrentUser.requireId());
    }

    @PostMapping
    public ScaleThresholdItem create(@RequestBody @Valid ScaleThresholdRequest request) {
        return customScaleService.createThreshold(request);
    }

    @PutMapping("/{id}")
    public ScaleThresholdItem update(@PathVariable Long id, @RequestBody @Valid ScaleThresholdRequest request) {
        return customScaleService.updateThreshold(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        customScaleService.deleteThreshold(id);
    }
}
