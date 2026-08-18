package dev.eolmae.marketmonitor.domain.marketmap.controller;

import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryDeletePreview;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryItem;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryNameRequest;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CreateCategoryRequest;
import dev.eolmae.marketmonitor.domain.marketmap.dto.ReparentRequest;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/admin/market-map/categories")
@RestController
@RequiredArgsConstructor
public class MarketMapCategoryController {

    private final MarketMapCategoryService marketMapCategoryService;

    @GetMapping
    public List<CategoryItem> getCategories() {
        return marketMapCategoryService.getCategories();
    }

    @PostMapping
    public CategoryItem create(@RequestBody @Valid CreateCategoryRequest request) {
        if (request.parentId() == null) {
            return marketMapCategoryService.createParent(request.name());
        }
        return marketMapCategoryService.createChild(request.name(), request.parentId());
    }

    @PatchMapping("/{id}/name")
    public void rename(@PathVariable Long id, @RequestBody @Valid CategoryNameRequest request) {
        marketMapCategoryService.rename(id, request.name());
    }

    @PatchMapping("/{id}/parent")
    public void reparent(@PathVariable Long id, @RequestBody ReparentRequest request) {
        marketMapCategoryService.reparent(id, request.categoryId());
    }

    @GetMapping("/{id}/delete-preview")
    public CategoryDeletePreview deletePreview(@PathVariable Long id) {
        return marketMapCategoryService.deletePreview(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        marketMapCategoryService.delete(id);
    }
}
