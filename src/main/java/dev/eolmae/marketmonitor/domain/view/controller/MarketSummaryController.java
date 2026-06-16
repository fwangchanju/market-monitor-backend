package dev.eolmae.marketmonitor.domain.view.controller;

import dev.eolmae.marketmonitor.domain.notification.service.MarketSummarySendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MarketSummaryController {

    private final MarketSummarySendService marketSummarySendService;

    @PostMapping("/send-market-summary")
    public void sendMarketSummary() {
        marketSummarySendService.sendMarketSummary();
    }
}
