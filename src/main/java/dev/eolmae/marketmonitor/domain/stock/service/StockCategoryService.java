package dev.eolmae.marketmonitor.domain.stock.service;

import dev.eolmae.marketmonitor.domain.stock.entity.StockCategory;
import dev.eolmae.marketmonitor.domain.stock.repository.StockCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class StockCategoryService {

    private final StockCategoryRepository stockCategoryRepository;

    public void reassign(String stockCode, String categoryName) {
        stockCategoryRepository
                .findById(stockCode)
                .ifPresentOrElse(
                        category -> category.update(categoryName),
                        () -> stockCategoryRepository.save(StockCategory.create(stockCode, categoryName)));
    }

    public void delete(String stockCode) {
        stockCategoryRepository.deleteById(stockCode);
    }

    public void deleteAll() {
        stockCategoryRepository.deleteAllInBatch();
    }
}
