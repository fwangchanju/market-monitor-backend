package dev.eolmae.marketmonitor.domain.custom.controller;

import dev.eolmae.marketmonitor.domain.auth.service.CurrentUser;
import dev.eolmae.marketmonitor.domain.custom.dto.CustomValueTierItem;
import dev.eolmae.marketmonitor.domain.custom.service.CustomValueTierThresholdService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/custom/value-tiers")
@RequiredArgsConstructor
public class CustomValueTierController {

    private final CustomValueTierThresholdService customValueTierThresholdService;

    @GetMapping
    public List<CustomValueTierItem> getValueTiers() {
        return customValueTierThresholdService.getValueTiers(CurrentUser.requireId());
    }
}
