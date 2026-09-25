package dev.eolmae.marketmonitor.domain.custom.controller;

import dev.eolmae.marketmonitor.domain.custom.dto.AliasRequest;
import dev.eolmae.marketmonitor.domain.custom.dto.BulkAssignRequest;
import dev.eolmae.marketmonitor.domain.custom.dto.BulkAssignResponse;
import dev.eolmae.marketmonitor.domain.custom.dto.SectorIdRequest;
import dev.eolmae.marketmonitor.domain.custom.dto.StockSectorListItem;
import dev.eolmae.marketmonitor.domain.custom.service.CustomStockSectorService;
import dev.eolmae.marketmonitor.domain.view.dto.SnapshotResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/custom/stock-sectors")
@RestController
@RequiredArgsConstructor
public class CustomStockSectorController {

    private final CustomStockSectorService customStockSectorService;

    @GetMapping
    public SnapshotResponse<StockSectorListItem> getStockCategories() {
        return customStockSectorService.getStockCategories();
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
    public BulkAssignResponse bulkAssign(@RequestBody @Valid BulkAssignRequest request) {
        return customStockSectorService.bulkAssign(request.stockCodes(), request.sectorId());
    }

    @PatchMapping("/{stockCode}/alias")
    public void updateAlias(@PathVariable String stockCode, @RequestBody @Valid AliasRequest request) {
        customStockSectorService.updateAlias(stockCode, request.alias());
    }

    @DeleteMapping("/{stockCode}/alias")
    public void deleteAlias(@PathVariable String stockCode) {
        customStockSectorService.updateAlias(stockCode, null);
    }
}
