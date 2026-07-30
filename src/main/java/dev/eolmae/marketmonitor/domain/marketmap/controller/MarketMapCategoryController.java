package dev.eolmae.marketmonitor.domain.marketmap.controller;

import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryDeletePreview;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryItem;
import dev.eolmae.marketmonitor.domain.marketmap.dto.CreateCategoryRequest;
import dev.eolmae.marketmonitor.domain.marketmap.dto.ReorderCategoryRequest;
import dev.eolmae.marketmonitor.domain.marketmap.service.MarketMapCategoryService;
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

@RestController
@RequestMapping("/api/admin/market-map/categories")
@RequiredArgsConstructor
public class MarketMapCategoryController {

    private final MarketMapCategoryService marketMapCategoryService;

    @GetMapping
    public List<CategoryItem> getCategories() {
        return marketMapCategoryService.getCategories();
    }

    @PostMapping
    public CategoryItem create(@RequestBody CreateCategoryRequest request) {
        if (request.parentId() == null) {
            return marketMapCategoryService.createParent(request.name());
        }
        return marketMapCategoryService.createChild(request.name(), request.parentId());
    }

    @PatchMapping("/{id}/order")
    public void reorder(@PathVariable Long id, @RequestBody ReorderCategoryRequest request) {
        marketMapCategoryService.reorder(id, request.displayOrder());
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
