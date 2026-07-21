package dev.eolmae.marketmonitor.domain.view.controller;

import dev.eolmae.marketmonitor.domain.notification.service.MarketSummaryRenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MarketSummaryRenderController {

    private final MarketSummaryRenderService marketSummaryRenderService;

    @PostMapping("/render-market-summary")
    public void sendMarketSummary() {
        marketSummaryRenderService.sendMarketSummary();
    }
}
