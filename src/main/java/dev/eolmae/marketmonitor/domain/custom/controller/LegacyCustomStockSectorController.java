package dev.eolmae.marketmonitor.domain.custom.controller;

import dev.eolmae.marketmonitor.common.enums.Market;
import dev.eolmae.marketmonitor.domain.custom.dto.AliasRequest;
import dev.eolmae.marketmonitor.domain.custom.dto.BulkAssignRequest;
import dev.eolmae.marketmonitor.domain.custom.dto.BulkAssignResponse;
import dev.eolmae.marketmonitor.domain.custom.dto.SectorIdRequest;
import dev.eolmae.marketmonitor.domain.custom.dto.StockSectorListItem;
import dev.eolmae.marketmonitor.domain.custom.service.CustomStockSectorService;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Temporary response-shape compatibility for the frontend deployed before /api/custom. */
@RequestMapping("/api/admin/market-map/stock-categories")
@RestController
@RequiredArgsConstructor
public class LegacyCustomStockSectorController {

    private final CustomStockSectorService customStockSectorService;

    @GetMapping
    public SnapshotResponse<LegacyStockSectorListItem> getStockCategories() {
        SnapshotResponse<StockSectorListItem> response = customStockSectorService.getStockCategories();
        return new SnapshotResponse<>(
                response.snapshotTime(),
                response.items().stream()
                        .map(item -> new LegacyStockSectorListItem(
                                item.stockCode(),
                                item.market(),
                                item.stockName(),
                                item.alias(),
                                item.totalMarketValue(),
                                item.marketValueTier(),
                                item.industryName(),
                                item.parentSectorName(),
                                item.sectorName(),
                                item.sectorId()))
                        .toList());
    }

    @PutMapping("/{stockCode}")
    public void assign(@PathVariable String stockCode, @RequestBody @Valid SectorIdRequest request) {
        customStockSectorService.assign(stockCode, request.sectorId());
    }

    @DeleteMapping("/{stockCode}")
    public void unassign(@PathVariable String stockCode) {
        customStockSectorService.unassign(stockCode);
    }

    @PatchMapping("/bulk")
    public LegacyBulkAssignResponse bulkAssign(@RequestBody @Valid BulkAssignRequest request) {
        BulkAssignResponse response = customStockSectorService.bulkAssign(request.stockCodes(), request.sectorId());
        return new LegacyBulkAssignResponse(response.failedStockCodes(), response.sectorId());
    }

    @PatchMapping("/{stockCode}/alias")
    public void updateAlias(@PathVariable String stockCode, @RequestBody @Valid AliasRequest request) {
        customStockSectorService.updateAlias(stockCode, request.alias());
    }

    @DeleteMapping("/{stockCode}/alias")
    public void deleteAlias(@PathVariable String stockCode) {
        customStockSectorService.updateAlias(stockCode, null);
    }

    public record LegacyStockSectorListItem(
            String stockCode,
            Market market,
            String stockName,
            String alias,
            BigDecimal totalMarketValue,
            String marketValueTier,
            String originCategoryName,
            String parentCategoryName,
            String categoryName,
            Long categoryId) {}

    public record LegacyBulkAssignResponse(List<String> failedStockCodes, Long categoryId) {}
}
