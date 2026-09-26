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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마켓맵 색상 스케일(사용자 설정 색상 구간) 조회/개별 CRUD. KOSPI/KOSDAQ 구분 없이 앱 전체에 단 하나의 설정만 존재한다. */
@Service
@Transactional
@RequiredArgsConstructor
public class CustomScaleService {

    private final CustomScaleThresholdRepository customScaleThresholdRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public CustomScaleResponse getScale() {
        Long userId = CurrentUser.currentId();
        if (userId == null) {
            return getDefaultScale();
        }
        return getUserScale(userId);
    }

    @Transactional(readOnly = true)
    public CustomScaleResponse getDefaultScale() {
        // default_scale_threshold는 매핑된 JPA 엔티티가 없는 순수 참조 테이블(가입 시 복제만 되고
        // 앱 코드에서 별도로 관리하지 않음)이라 QueryDSL Q타입이 없다. 엔티티를 새로 추가하는 것은
        // 이 리팩터링 범위를 벗어나 PR 설명에 남기고 유지한다.
        List<ScaleThresholdItem> thresholds = jdbcTemplate.query(
                "SELECT threshold_percent, color, color_label FROM default_scale_threshold ORDER BY threshold_percent",
                (resultSet, rowNumber) -> new ScaleThresholdItem(
                        resultSet
                                .getBigDecimal("threshold_percent")
                                .movePointRight(2)
                                .longValue(),
                        resultSet.getBigDecimal("threshold_percent"),
                        resultSet.getString("color"),
                        resultSet.getString("color_label") == null
                                ? null
                                : dev.eolmae.marketmonitor.domain.custom.enums.ColorLabel.valueOf(
                                        resultSet.getString("color_label"))));
        return new CustomScaleResponse(thresholds);
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
