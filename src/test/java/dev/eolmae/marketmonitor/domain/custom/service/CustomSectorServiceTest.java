package dev.eolmae.marketmonitor.domain.custom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.eolmae.marketmonitor.common.exception.NotFoundException;
import dev.eolmae.marketmonitor.domain.auth.enums.Role;
import dev.eolmae.marketmonitor.domain.auth.service.AuthenticatedUserPrincipal;
import dev.eolmae.marketmonitor.domain.custom.entity.CustomSector;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomSectorRepository;
import dev.eolmae.marketmonitor.domain.custom.repository.CustomStockSectorRepository;
import dev.eolmae.marketmonitor.domain.stock.service.StockInfoCacheService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class CustomSectorServiceTest {

    private static final long USER_ID = 41L;

    private final CustomSectorRepository sectorRepository = Mockito.mock(CustomSectorRepository.class);
    private final CustomStockSectorRepository stockSectorRepository = Mockito.mock(CustomStockSectorRepository.class);
    private final StockInfoCacheService stockInfoCacheService = Mockito.mock(StockInfoCacheService.class);
    private final CustomSectorService service =
            new CustomSectorService(sectorRepository, stockSectorRepository, stockInfoCacheService);

    @BeforeEach
    void authenticateUser() {
        var principal = new AuthenticatedUserPrincipal(USER_ID, Role.USER);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createParent_저장할_섹터에_현재_사용자_id를_설정한다() {
        when(sectorRepository.existsByUserIdAndName(USER_ID, "반도체")).thenReturn(false);
        when(sectorRepository.save(any(CustomSector.class))).thenAnswer(invocation -> {
            CustomSector sector = invocation.getArgument(0);
            ReflectionTestUtils.setField(sector, "id", 12L);
            return sector;
        });

        var created = service.createParent("반도체");

        assertThat(created.id()).isEqualTo(12L);
        assertThat(created.name()).isEqualTo("반도체");
        verify(sectorRepository).existsByUserIdAndName(USER_ID, "반도체");
        verify(sectorRepository)
                .save(Mockito.argThat(sector -> sector.getUserId().equals(USER_ID)));
    }

    @Test
    void reparent_다른_사용자의_섹터를_찾지_않는다() {
        CustomSector ownSector = CustomSector.createParent(USER_ID, "내 섹터");
        ReflectionTestUtils.setField(ownSector, "id", 10L);
        when(sectorRepository.findAllByUserId(USER_ID)).thenReturn(List.of(ownSector));

        assertThatThrownBy(() -> service.reparent(99L, null)).isInstanceOf(NotFoundException.class);

        verify(sectorRepository).findAllByUserId(USER_ID);
    }

    @Test
    void rename_다른_사용자가_소유한_id를_사용하지_않는다() {
        when(sectorRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rename(99L, "이름 변경")).isInstanceOf(NotFoundException.class);

        verify(sectorRepository).findByIdAndUserId(99L, USER_ID);
    }
}
