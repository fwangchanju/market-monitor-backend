package dev.eolmae.marketmonitor.domain.custom.service;

import dev.eolmae.marketmonitor.common.exception.ConflictException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.NotFoundException;
import dev.eolmae.marketmonitor.domain.custom.dto.CustomScaleResponse;
import dev.eolmae.marketmonitor.domain.custom.dto.ScaleThresholdItem;
import dev.eolmae.marketmonitor.domain.custom.dto.ScaleThresholdRequest;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomScaleThreshold;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomScaleThresholdRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마켓맵 색상 스케일(사용자 설정 색상 구간) 조회/개별 CRUD. KOSPI/KOSDAQ 구분 없이 앱 전체에 단 하나의 설정만 존재한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class CustomScaleService {

    private final CustomScaleThresholdRepository customScaleThresholdRepository;

    @Transactional(readOnly = true)
    public CustomScaleResponse getScale() {
        List<ScaleThresholdItem> thresholds = customScaleThresholdRepository.findAll().stream()
                .sorted(Comparator.comparing(CustomScaleThreshold::getThresholdPercent))
                .map(this::toItem)
                .toList();
        return new CustomScaleResponse(thresholds);
    }

    public ScaleThresholdItem createThreshold(ScaleThresholdRequest request) {
        if (customScaleThresholdRepository.existsByThresholdPercent(request.thresholdPercent())) {
            throw new ConflictException(ErrorCode.SCALE_THRESHOLD_DUPLICATE, request.thresholdPercent());
        }
        var entity = CustomScaleThreshold.create(request.thresholdPercent(), request.color(), request.colorLabel());
        return toItem(customScaleThresholdRepository.save(entity));
    }

    public ScaleThresholdItem updateThreshold(Long id, ScaleThresholdRequest request) {
        var entity = customScaleThresholdRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SCALE_THRESHOLD_NOT_FOUND, id));
        if (customScaleThresholdRepository.existsByThresholdPercentAndIdNot(request.thresholdPercent(), id)) {
            throw new ConflictException(ErrorCode.SCALE_THRESHOLD_DUPLICATE, request.thresholdPercent());
        }
        entity.update(request.thresholdPercent(), request.color(), request.colorLabel());
        return toItem(entity);
    }

    public void deleteThreshold(Long id) {
        if (!customScaleThresholdRepository.existsById(id)) {
            throw new NotFoundException(ErrorCode.SCALE_THRESHOLD_NOT_FOUND, id);
        }
        customScaleThresholdRepository.deleteById(id);
    }

    private ScaleThresholdItem toItem(CustomScaleThreshold entity) {
        return new ScaleThresholdItem(
                entity.getId(), entity.getThresholdPercent(), entity.getColor(), entity.getColorLabel());
    }
}
