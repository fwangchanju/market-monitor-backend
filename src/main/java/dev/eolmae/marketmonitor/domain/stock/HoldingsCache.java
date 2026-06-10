package dev.eolmae.marketmonitor.domain.stock;

import dev.eolmae.marketmonitor.domain.stock.dto.AccountBalanceResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HoldingsCache {

    private volatile List<AccountBalanceResponse.HoldingItem> holdings = List.of();

    public void update(List<AccountBalanceResponse.HoldingItem> sorted) {
        this.holdings = sorted;
    }

    public String topStockCode() {
        return holdings.isEmpty() ? null : holdings.getFirst().stockCode();
    }

    public List<AccountBalanceResponse.HoldingItem> getAll() {
        return holdings;
    }
}
