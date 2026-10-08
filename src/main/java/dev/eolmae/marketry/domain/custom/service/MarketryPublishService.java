package dev.eolmae.marketry.domain.custom.service;

import dev.eolmae.marketry.common.exception.BadRequestException;
import dev.eolmae.marketry.common.exception.ErrorCode;
import dev.eolmae.marketry.common.exception.NotFoundException;
import dev.eolmae.marketry.domain.auth.service.CurrentUser;
import dev.eolmae.marketry.domain.custom.dto.SnapshotItem;
import dev.eolmae.marketry.domain.custom.entity.CustomSnapshot;
import dev.eolmae.marketry.domain.custom.repository.CustomSnapshotRepository;
import dev.eolmae.marketry.domain.notification.properties.MarketryProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 운영자의 분류를 MARKETRY 고정본(발행 사용자의 분류 데이터)으로 올리고, 이전 버전으로 되돌린다. */
@Service
@Transactional
@RequiredArgsConstructor
public class MarketryPublishService {

    private final CustomSnapshotRepository customSnapshotRepository;
    private final CustomSectorTreeService customSectorTreeService;
    private final MarketryProperties marketryProperties;

    /** 호출한 운영자의 분류(약칭 포함)를 새 고정본 버전으로 저장하고 발행 사용자의 분류 데이터를 그 내용으로 바꾼다. 운영자 본인 데이터는 읽기만 한다. */
    public SnapshotItem publish(String label) {
        Long adminId = CurrentUser.requireId();
        Long publishedUserId = marketryProperties.publishedUserId();
        String snapshotJson = customSectorTreeService.serializeCurrentSnapshot(adminId);
        CustomSnapshot saved =
                customSnapshotRepository.save(CustomSnapshot.create(publishedUserId, label, snapshotJson));
        customSectorTreeService.restore(snapshotJson, publishedUserId, saved.getId());
        return toItem(saved);
    }

    @Transactional(readOnly = true)
    public List<SnapshotItem> getVersions() {
        return customSnapshotRepository
                .findAllByUserIdOrderByCreatedAtDesc(marketryProperties.publishedUserId())
                .stream()
                .filter(snapshot -> customSectorTreeService.isCurrentSnapshotFormat(snapshot.getSnapshotJson()))
                .map(this::toItem)
                .toList();
    }

    public void restoreVersion(Long snapshotId) {
        Long publishedUserId = marketryProperties.publishedUserId();
        CustomSnapshot snapshot = customSnapshotRepository
                .findByIdAndUserId(snapshotId, publishedUserId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.SNAPSHOT_NOT_FOUND, snapshotId));
        if (!customSectorTreeService.isCurrentSnapshotFormat(snapshot.getSnapshotJson())) {
            throw new BadRequestException(ErrorCode.SNAPSHOT_FORMAT_UNSUPPORTED);
        }
        customSectorTreeService.restore(snapshot.getSnapshotJson(), publishedUserId, snapshotId);
    }

    private SnapshotItem toItem(CustomSnapshot snapshot) {
        return new SnapshotItem(
                snapshot.getId(), snapshot.getLabel(), snapshot.getCreatedAt(), snapshot.getUpdatedAt());
    }
}
