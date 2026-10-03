package dev.eolmae.marketry.domain.auth.service;

import dev.eolmae.marketry.common.event.UserSignedUpEvent;
import dev.eolmae.marketry.domain.custom.entity.CustomSector;
import dev.eolmae.marketry.domain.custom.entity.CustomStockAlias;
import dev.eolmae.marketry.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketry.domain.custom.entity.CustomValueTierThreshold;
import dev.eolmae.marketry.domain.custom.entity.UserPreference;
import dev.eolmae.marketry.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockAliasRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketry.domain.custom.repository.CustomValueTierThresholdRepository;
import dev.eolmae.marketry.domain.custom.repository.UserPreferenceRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignupInitializationService {

    static final long INDUSTRY_USER_SYNC_LOCK = 941270361L;
    private static final long TEMPLATE_USER_ID = 1L;

    private final JdbcTemplate jdbcTemplate;
    private final CustomSectorRepository customSectorRepository;
    private final CustomStockSectorRepository customStockSectorRepository;
    private final CustomStockAliasRepository customStockAliasRepository;
    private final CustomValueTierThresholdRepository customValueTierThresholdRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    /** 템플릿 계정(user_id = 1)의 업종 분류, 종목 배정, 별칭, 시가총액 구간을 신규 사용자에게 복제한다. 스냅샷 이력, 저장 설정, 색상 구간은 복제하지 않는다. */
    @EventListener
    @Transactional
    public void onUserSignedUp(UserSignedUpEvent event) {
        Long userId = event.userId();
        // pg_advisory_xact_lock — 가입·업종·신규 종목 전파를 직렬화하는 PostgreSQL 세션 락. JPQL/QueryDSL에는
        // 대응하는 문법이 없다.
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, INDUSTRY_USER_SYNC_LOCK);
                statement.execute();
            }
            return null;
        });

        Map<Long, CustomSector> sectorByTemplateId = copySectors(userId);
        customStockSectorRepository.saveAll(customStockSectorRepository.findAllByIdUserId(TEMPLATE_USER_ID).stream()
                .map(source -> CustomStockSector.create(
                        userId,
                        source.getStockCode(),
                        sectorByTemplateId.get(source.getSectorId()).getId()))
                .toList());
        customStockAliasRepository.saveAll(customStockAliasRepository.findAllByIdUserId(TEMPLATE_USER_ID).stream()
                .map(source -> CustomStockAlias.create(userId, source.getStockCode(), source.getAlias()))
                .toList());
        customValueTierThresholdRepository.saveAll(
                customValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(TEMPLATE_USER_ID).stream()
                        .map(source -> CustomValueTierThreshold.create(
                                userId, source.getLabel(), source.getThresholdValue(), source.isExcludedByDefault()))
                        .toList());
        copyPreference(userId);
    }

    // 부모가 먼저 저장돼야 자식이 새 부모 id를 받으므로 depth 순으로 저장한다. IDENTITY라 save 즉시 id가 나온다.
    // 반환값은 템플릿 섹터 id → 새 섹터 — 종목 배정을 새 섹터에 연결할 때 쓴다.
    private Map<Long, CustomSector> copySectors(Long userId) {
        List<CustomSector> templateSectors = customSectorRepository.findAllByUserId(TEMPLATE_USER_ID).stream()
                .sorted(Comparator.comparingInt(CustomSector::getDepth).thenComparing(CustomSector::getId))
                .toList();
        Map<Long, CustomSector> sectorByTemplateId = new HashMap<>();
        for (CustomSector source : templateSectors) {
            CustomSector copy = source.hasNoParent()
                    ? CustomSector.createParent(userId, source.getName())
                    : CustomSector.createChild(userId, source.getName(), sectorByTemplateId.get(source.getParentId()));
            if (source.isExcluded()) {
                copy.exclude();
            }
            sectorByTemplateId.put(source.getId(), customSectorRepository.save(copy));
        }
        return sectorByTemplateId;
    }

    private void copyPreference(Long userId) {
        // 설정과 색상 구간은 템플릿에서 복제하지 않는다 — 비어 있으면 화면이 코드의 기본값(비로그인과 같은 값)을 쓴다.
        userPreferenceRepository.save(UserPreference.createEmpty(userId));
    }
}
