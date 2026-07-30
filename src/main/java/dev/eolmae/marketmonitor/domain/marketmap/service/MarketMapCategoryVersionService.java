package dev.eolmae.marketmonitor.domain.marketmap.service;

import dev.eolmae.marketmonitor.domain.marketmap.dto.CategoryTreeNode;
import dev.eolmae.marketmonitor.domain.marketmap.dto.VersionItem;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategory;
import dev.eolmae.marketmonitor.domain.marketmap.entity.MarketMapCategoryVersion;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryRepository;
import dev.eolmae.marketmonitor.domain.marketmap.repository.MarketMapCategoryVersionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 카테고리 버전(백업) 저장/덮어쓰기/불러오기/삭제. */
@Service
@RequiredArgsConstructor
@Transactional
public class MarketMapCategoryVersionService {

    private final MarketMapCategoryVersionRepository marketMapCategoryVersionRepository;
    private final MarketMapCategoryRepository marketMapCategoryRepository;
    private final MarketMapCategoryTreeService marketMapCategoryTreeService;

    @Transactional(readOnly = true)
    public List<VersionItem> getVersions() {
        return marketMapCategoryVersionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toItem)
                .toList();
    }

    /** 현재 라이브 트리가 매칭 중인 버전. 저장 이력이 없다면 버전은 존재하지 않는다.
     * 카테고리에 태깅된 version_id가 있어도 그 버전이 이후 삭제됐을 수 있기 때문에(삭제 로직 참고) 버전 테이블 실제 존재 여부까지 확인 */
    @Transactional(readOnly = true)
    public VersionItem currentVersion() {
        Long versionId = marketMapCategoryRepository
                .findFirstByOrderByIdAsc()
                .map(MarketMapCategory::getVersionId)
                .orElse(null);
        if (versionId == null) {
            return null;
        }
        return marketMapCategoryVersionRepository.findById(versionId).map(this::toItem).orElse(null);
    }

    public VersionItem save(String label) {
        String snapshotJson = marketMapCategoryTreeService.serializeCurrentSnapshot();
        MarketMapCategoryVersion saved =
                marketMapCategoryVersionRepository.save(MarketMapCategoryVersion.create(label, snapshotJson));
        tagLiveCategories(saved.getId());
        return toItem(saved);
    }

    public VersionItem overwrite(Long versionId, String label) {
        MarketMapCategoryVersion version = marketMapCategoryVersionRepository
                .findById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String snapshotJson = marketMapCategoryTreeService.serializeCurrentSnapshot();
        version.overwrite(label, snapshotJson);
        tagLiveCategories(versionId);
        return toItem(version);
    }

    public void restore(Long versionId) {
        MarketMapCategoryVersion version = marketMapCategoryVersionRepository
                .findById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<CategoryTreeNode> tree = marketMapCategoryTreeService.parseJson(version.getSnapshotJson());
        marketMapCategoryTreeService.restore(tree, versionId);
    }

    /** 라이브 카테고리의 version_id는 정리하지 않는다
     * 삭제할 때마다 카테고리 전체를 버전을 변경하는(null) 행위를 하지 않기 위함 */
    public void delete(Long versionId) {
        marketMapCategoryVersionRepository.deleteById(versionId);
    }

    private void tagLiveCategories(Long versionId) {
        marketMapCategoryRepository.findAll().forEach(category -> category.tagVersion(versionId));
    }

    private VersionItem toItem(MarketMapCategoryVersion version) {
        return new VersionItem(version.getId(), version.getLabel(), version.getCreatedAt(), version.getUpdatedAt());
    }
}
