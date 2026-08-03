package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapStockCategory;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapStockCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 종목의 카테고리 배정/재배정. */
@Service
@RequiredArgsConstructor
@Transactional
public class MarketMapStockCategoryService {

    private final MarketMapStockCategoryRepository marketMapStockCategoryRepository;
    private final MarketMapCategoryRepository marketMapCategoryRepository;

    public void assign(String stockCode, Long categoryId) {
        if (!marketMapCategoryRepository.existsById(categoryId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        marketMapStockCategoryRepository
                .findById(stockCode)
                .ifPresentOrElse(
                        stockCategory -> stockCategory.reassign(categoryId),
                        () -> marketMapStockCategoryRepository.save(MarketMapStockCategory.create(stockCode, categoryId)));
    }
}
