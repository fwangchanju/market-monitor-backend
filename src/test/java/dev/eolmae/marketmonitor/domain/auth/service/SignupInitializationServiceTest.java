package dev.eolmae.marketmonitor.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.event.UserSignedUpEvent;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomScaleThreshold;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomSector;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockAlias;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomStockSector;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomValueTierThreshold;
import dev.eolmae.marketmonitor.domain.custom.entity.UserPreference;
import dev.eolmae.marketmonitor.domain.custom.enums.ColorLabel;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomScaleThresholdRepository;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomStockAliasRepository;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomValueTierThresholdRepository;
import dev.eolmae.marketmonitor.domain.custom.repository.UserPreferenceRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class SignupInitializationServiceTest {

    private static final long TEMPLATE_USER_ID = 1L;
    private static final long USER_ID = 41L;

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CustomSectorRepository customSectorRepository = mock(CustomSectorRepository.class);
    private final CustomStockSectorRepository customStockSectorRepository = mock(CustomStockSectorRepository.class);
    private final CustomStockAliasRepository customStockAliasRepository = mock(CustomStockAliasRepository.class);
    private final CustomScaleThresholdRepository customScaleThresholdRepository =
            mock(CustomScaleThresholdRepository.class);
    private final CustomValueTierThresholdRepository customValueTierThresholdRepository =
            mock(CustomValueTierThresholdRepository.class);
    private final UserPreferenceRepository userPreferenceRepository = mock(UserPreferenceRepository.class);
    private final SignupInitializationService service = new SignupInitializationService(
            jdbcTemplate,
            customSectorRepository,
            customStockSectorRepository,
            customStockAliasRepository,
            customScaleThresholdRepository,
            customValueTierThresholdRepository,
            userPreferenceRepository);

    // IDENTITY 채번 흉내 — save 순서대로 100, 101, … 을 붙이고, 저장된 섹터를 순서대로 모은다.
    private final AtomicLong nextSectorId = new AtomicLong(100);
    private final List<CustomSector> savedSectors = new ArrayList<>();

    @BeforeEach
    void stubSectorSave() {
        when(customSectorRepository.save(any(CustomSector.class))).thenAnswer(invocation -> {
            CustomSector sector = invocation.getArgument(0);
            ReflectionTestUtils.setField(sector, "id", nextSectorId.getAndIncrement());
            savedSectors.add(sector);
            return sector;
        });
    }

    @Test
    void 가입시_advisory_lock을_잡는다() {
        service.onUserSignedUp(new UserSignedUpEvent(USER_ID));

        verify(jdbcTemplate).execute(any(ConnectionCallback.class));
    }

    @Test
    void 섹터는_부모부터_저장하고_새_부모_id와_제외_여부를_이어받는다() {
        CustomSector root = sector(10L, CustomSector.createParent(TEMPLATE_USER_ID, "반도체"));
        CustomSector child = sector(11L, CustomSector.createChild(TEMPLATE_USER_ID, "메모리", root));
        child.exclude();
        // 조회 순서가 뒤섞여 와도 depth 순으로 저장해야 자식이 부모의 새 id를 받는다.
        when(customSectorRepository.findAllByUserId(TEMPLATE_USER_ID)).thenReturn(List.of(child, root));

        service.onUserSignedUp(new UserSignedUpEvent(USER_ID));

        assertThat(savedSectors)
                .extracting(
                        CustomSector::getUserId,
                        CustomSector::getName,
                        CustomSector::getParentId,
                        CustomSector::getDepth,
                        CustomSector::isExcluded)
                .containsExactly(tuple(USER_ID, "반도체", null, 0, false), tuple(USER_ID, "메모리", 100L, 1, true));
    }

    @Test
    void 종목_배정은_템플릿_섹터에_대응하는_새_섹터를_가리키고_새_엔티티로_저장된다() {
        CustomSector root = sector(10L, CustomSector.createParent(TEMPLATE_USER_ID, "반도체"));
        CustomSector child = sector(11L, CustomSector.createChild(TEMPLATE_USER_ID, "메모리", root));
        when(customSectorRepository.findAllByUserId(TEMPLATE_USER_ID)).thenReturn(List.of(root, child));
        when(customStockSectorRepository.findAllByIdUserId(TEMPLATE_USER_ID))
                .thenReturn(List.of(
                        CustomStockSector.create(TEMPLATE_USER_ID, "005930", 10L),
                        CustomStockSector.create(TEMPLATE_USER_ID, "000660", 11L)));

        service.onUserSignedUp(new UserSignedUpEvent(USER_ID));

        List<CustomStockSector> saved = captureSaveAll(customStockSectorRepository);
        assertThat(saved)
                .extracting(
                        CustomStockSector::getUserId, CustomStockSector::getStockCode, CustomStockSector::getSectorId)
                .containsExactly(tuple(USER_ID, "005930", 100L), tuple(USER_ID, "000660", 101L));
        // isNew가 참이어야 saveAll이 merge(행마다 SELECT) 대신 persist(배치 INSERT)로 나간다.
        assertThat(saved).allMatch(CustomStockSector::isNew);
    }

    @Test
    void 별칭과_색상_구간과_시가총액_구간과_설정을_복제한다() {
        when(customStockAliasRepository.findAllByIdUserId(TEMPLATE_USER_ID))
                .thenReturn(List.of(CustomStockAlias.create(TEMPLATE_USER_ID, "005930", "삼전")));
        when(customScaleThresholdRepository.findAllByUserId(TEMPLATE_USER_ID))
                .thenReturn(List.of(CustomScaleThreshold.create(
                        TEMPLATE_USER_ID, new BigDecimal("3.00"), "#ff0000", ColorLabel.RED)));
        when(customValueTierThresholdRepository.findAllByUserIdOrderByThresholdValueAsc(TEMPLATE_USER_ID))
                .thenReturn(List.of(CustomValueTierThreshold.create(TEMPLATE_USER_ID, "소형주", 0L, true)));
        UserPreference templatePreference = UserPreference.createEmpty(TEMPLATE_USER_ID);
        templatePreference.overwrite("{\"theme\":\"dark\"}");
        when(userPreferenceRepository.findById(TEMPLATE_USER_ID)).thenReturn(Optional.of(templatePreference));

        service.onUserSignedUp(new UserSignedUpEvent(USER_ID));

        List<CustomStockAlias> aliases = captureSaveAll(customStockAliasRepository);
        assertThat(aliases)
                .extracting(CustomStockAlias::getUserId, CustomStockAlias::getStockCode, CustomStockAlias::getAlias)
                .containsExactly(tuple(USER_ID, "005930", "삼전"));
        assertThat(aliases).allMatch(CustomStockAlias::isNew);
        List<CustomScaleThreshold> scales = captureSaveAll(customScaleThresholdRepository);
        assertThat(scales)
                .extracting(
                        CustomScaleThreshold::getUserId,
                        CustomScaleThreshold::getColor,
                        CustomScaleThreshold::getColorLabel)
                .containsExactly(tuple(USER_ID, "#ff0000", ColorLabel.RED));
        assertThat(scales.getFirst().getThresholdPercent()).isEqualByComparingTo("3.00");
        assertThat(captureSaveAll(customValueTierThresholdRepository))
                .extracting(
                        CustomValueTierThreshold::getUserId,
                        CustomValueTierThreshold::getLabel,
                        CustomValueTierThreshold::getThresholdValue,
                        CustomValueTierThreshold::isExcludedByDefault)
                .containsExactly(tuple(USER_ID, "소형주", 0L, true));
        assertThat(savedPreference())
                .extracting(UserPreference::getUserId, UserPreference::getPayload)
                .containsExactly(USER_ID, "{\"theme\":\"dark\"}");
    }

    @Test
    void 템플릿이_비어_있으면_아무것도_복제하지_않고_빈_설정을_만든다() {
        service.onUserSignedUp(new UserSignedUpEvent(USER_ID));

        assertThat(savedSectors).isEmpty();
        assertThat(captureSaveAll(customStockSectorRepository)).isEmpty();
        assertThat(captureSaveAll(customStockAliasRepository)).isEmpty();
        assertThat(captureSaveAll(customScaleThresholdRepository)).isEmpty();
        assertThat(captureSaveAll(customValueTierThresholdRepository)).isEmpty();
        assertThat(savedPreference())
                .extracting(UserPreference::getUserId, UserPreference::getPayload)
                .containsExactly(USER_ID, "{}");
    }

    private static CustomSector sector(Long id, CustomSector sector) {
        ReflectionTestUtils.setField(sector, "id", id);
        return sector;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> captureSaveAll(org.springframework.data.repository.CrudRepository<T, ?> repository) {
        ArgumentCaptor<Iterable<T>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        List<T> saved = new ArrayList<>();
        captor.getValue().forEach(saved::add);
        return saved;
    }

    private UserPreference savedPreference() {
        ArgumentCaptor<UserPreference> captor = ArgumentCaptor.forClass(UserPreference.class);
        verify(userPreferenceRepository).save(captor.capture());
        return captor.getValue();
    }
}
