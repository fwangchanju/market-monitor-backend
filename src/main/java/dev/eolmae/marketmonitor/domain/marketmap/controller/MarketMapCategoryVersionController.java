package dev.eolmae.marketmonitor.domain.marketmap.controller;

import dev.eolmae.marketmonitor.domain.marketmap.dto.VersionItem;
import dev.eolmae.marketmonitor.domain.marketmap.dto.VersionLabelRequest;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryVersionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/admin/market-map/versions")
@RestController
@RequiredArgsConstructor
public class MarketMapCategoryVersionController {

    private final MarketMapCategoryVersionService marketMapCategoryVersionService;

    @GetMapping
    public List<VersionItem> getVersions() {
        return marketMapCategoryVersionService.getVersions();
    }

    @GetMapping("/current")
    public VersionItem current() {
        return marketMapCategoryVersionService.currentVersion();
    }

    @PostMapping
    public VersionItem save(@RequestBody @Valid VersionLabelRequest request) {
        return marketMapCategoryVersionService.save(request.label());
    }

    @PatchMapping("/{id}")
    public VersionItem overwrite(@PathVariable Long id, @RequestBody @Valid VersionLabelRequest request) {
        return marketMapCategoryVersionService.overwrite(id, request.label());
    }

    @PostMapping("/{id}/restore")
    public void restore(@PathVariable Long id) {
        marketMapCategoryVersionService.restore(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        marketMapCategoryVersionService.delete(id);
    }
}
