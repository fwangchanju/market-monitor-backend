package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.common.exception.ConflictException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.NotFoundException;
import dev.eolmae.marketmonitor.domain.marketmap.dto.MarketMapScaleResponse;
import dev.eolmae.marketmonitor.domain.marketmap.dto.ScaleThresholdItem;
import dev.eolmae.marketmonitor.domain.marketmap.dto.ScaleThresholdRequest;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapScaleThreshold;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapScaleThresholdRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마켓맵 색상 스케일(사용자 설정 색상 구간) 조회/개별 CRUD. KOSPI/KOSDAQ 구분 없이 앱 전체에 단 하나의 설정만 존재한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class MarketMapScaleService {

    private final MarketMapScaleThresholdRepository marketMapScaleThresholdRepository;

    @Transactional(readOnly = true)
    public MarketMapScaleResponse getScale() {
        List<ScaleThresholdItem> thresholds = marketMapScaleThresholdRepository.findAll().stream()
                .sorted(Comparator.comparing(MarketMapScaleThreshold::getThresholdPercent))
                .map(this::toItem)
                .toList();
        return new MarketMapScaleResponse(thresholds);
    }

    public ScaleThresholdItem createThreshold(ScaleThresholdRequest request) {
        if (marketMapScaleThresholdRepository.existsByThresholdPercent(request.thresholdPercent())) {
            throw new ConflictException(ErrorCode.SCALE_THRESHOLD_DUPLICATE, request.thresholdPercent());
        }
        var entity = MarketMapScaleThreshold.create(request.thresholdPercent(), request.color(), request.colorLabel());
        return toItem(marketMapScaleThresholdRepository.save(entity));
    }

    public ScaleThresholdItem updateThreshold(Long id, ScaleThresholdRequest request) {
        var entity = marketMapScaleThresholdRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SCALE_THRESHOLD_NOT_FOUND, id));
        if (marketMapScaleThresholdRepository.existsByThresholdPercentAndIdNot(request.thresholdPercent(), id)) {
            throw new ConflictException(ErrorCode.SCALE_THRESHOLD_DUPLICATE, request.thresholdPercent());
        }
        entity.update(request.thresholdPercent(), request.color(), request.colorLabel());
        return toItem(entity);
    }

    public void deleteThreshold(Long id) {
        if (!marketMapScaleThresholdRepository.existsById(id)) {
            throw new NotFoundException(ErrorCode.SCALE_THRESHOLD_NOT_FOUND, id);
        }
        marketMapScaleThresholdRepository.deleteById(id);
    }

    private ScaleThresholdItem toItem(MarketMapScaleThreshold entity) {
        return new ScaleThresholdItem(entity.getId(), entity.getThresholdPercent(), entity.getColor(), entity.getColorLabel());
    }
}
