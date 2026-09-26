package dev.eolmae.marketmonitor.domain.custom.service;

import dev.eolmae.marketmonitor.common.exception.ConflictException;
import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.NotFoundException;
import dev.eolmae.marketmonitor.domain.auth.service.CurrentUser;
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
        Long userId = CurrentUser.currentId();
        if (userId == null) {
            return getDefaultScale();
        }
        return getUserScale(userId);
    }

    /** 색상 스케일 기본값은 더 이상 백엔드가 들고 있지 않다 — 프론트가 내장 프리셋으로 폴백한다
     * (marketMapColorScale.ts). 빈 목록만 내려준다. */
    @Transactional(readOnly = true)
    public CustomScaleResponse getDefaultScale() {
        return new CustomScaleResponse(List.of());
    }

    @Transactional(readOnly = true)
    public CustomScaleResponse getUserScale(Long userId) {
        List<ScaleThresholdItem> thresholds = customScaleThresholdRepository.findAllByUserId(userId).stream()
                .sorted(Comparator.comparing(CustomScaleThreshold::getThresholdPercent))
                .map(this::toItem)
                .toList();
        return new CustomScaleResponse(thresholds);
    }

    public ScaleThresholdItem createThreshold(ScaleThresholdRequest request) {
        Long userId = CurrentUser.requireId();
        if (customScaleThresholdRepository.existsByUserIdAndThresholdPercent(userId, request.thresholdPercent())) {
            throw new ConflictException(ErrorCode.SCALE_THRESHOLD_DUPLICATE, request.thresholdPercent());
        }
        var entity =
                CustomScaleThreshold.create(userId, request.thresholdPercent(), request.color(), request.colorLabel());
        return toItem(customScaleThresholdRepository.save(entity));
    }

    public ScaleThresholdItem updateThreshold(Long id, ScaleThresholdRequest request) {
        Long userId = CurrentUser.requireId();
        var entity = customScaleThresholdRepository
                .findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SCALE_THRESHOLD_NOT_FOUND, id));
        if (customScaleThresholdRepository.existsByUserIdAndThresholdPercentAndIdNot(
                userId, request.thresholdPercent(), id)) {
            throw new ConflictException(ErrorCode.SCALE_THRESHOLD_DUPLICATE, request.thresholdPercent());
        }
        entity.update(request.thresholdPercent(), request.color(), request.colorLabel());
        return toItem(entity);
    }

    public void deleteThreshold(Long id) {
        Long userId = CurrentUser.requireId();
        if (customScaleThresholdRepository.findByIdAndUserId(id, userId).isEmpty()) {
            throw new NotFoundException(ErrorCode.SCALE_THRESHOLD_NOT_FOUND, id);
        }
        customScaleThresholdRepository.deleteByIdAndUserId(id, userId);
    }

    private ScaleThresholdItem toItem(CustomScaleThreshold entity) {
        return new ScaleThresholdItem(
                entity.getId(), entity.getThresholdPercent(), entity.getColor(), entity.getColorLabel());
    }
}
