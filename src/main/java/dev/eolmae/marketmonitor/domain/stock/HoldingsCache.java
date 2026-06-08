package dev.eolmae.marketmonitor.domain.stock;

import dev.eolmae.marketmonitor.external.kiwoom.dto.Kt00018Response;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HoldingsCache {

    private volatile List<Kt00018Response.HoldingItem> holdings = List.of();

    public void update(List<Kt00018Response.HoldingItem> sorted) {
        this.holdings = sorted;
    }

    public String topStockCode() {
        return holdings.isEmpty() ? null : holdings.getFirst().stockCode();
    }

    public List<Kt00018Response.HoldingItem> getAll() {
        return holdings;
    }
}
