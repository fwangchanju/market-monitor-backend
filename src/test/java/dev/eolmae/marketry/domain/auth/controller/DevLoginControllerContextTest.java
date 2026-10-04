package dev.eolmae.marketry.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.eolmae.marketry.domain.auth.service.AppJwtService;
import dev.eolmae.marketry.domain.auth.service.AuthService;
import dev.eolmae.marketry.domain.notification.properties.MarketryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * DevLoginController(로컬 개발 전용 로그인)는 IP 화이트리스트 인증 브릿지(PR #132에서 제거)를 대체하므로
 * 운영 환경에 절대 떠서는 안 된다. 가드가 둘이다: {@code @Profile("!prod")}와
 * {@code auth.dev-login.enabled=true}(기본 application.properties에서 true로 고정, prod는
 * application-prod.properties가 false로 덮어씀). 이 둘이 실제로 독립적으로 작동하는지 빈 등록 여부로
 * 직접 확인한다.
 */
class DevLoginControllerContextTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DevLoginController.class)
            .withBean(AuthService.class, () -> mock(AuthService.class))
            .withBean(AppJwtService.class, () -> mock(AppJwtService.class))
            .withBean(AuthCookies.class, AuthCookies::new)
            .withBean(MarketryProperties.class, () -> new MarketryProperties("http://localhost:8081", 999999L));

    @Test
    void 프로퍼티가_true이고_prod_프로필이_아니면_빈이_등록된다() {
        runner.withPropertyValues("auth.dev-login.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(DevLoginController.class));
    }

    @Test
    void prod_프로필이면_프로퍼티가_true여도_빈이_등록되지_않는다() {
        runner.withPropertyValues("auth.dev-login.enabled=true", "spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(DevLoginController.class));
    }

    @Test
    void 프로퍼티가_false면_prod가_아니어도_빈이_등록되지_않는다() {
        runner.withPropertyValues("auth.dev-login.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DevLoginController.class));
    }

    @Test
    void 프로퍼티가_없으면_빈이_등록되지_않는다() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DevLoginController.class));
    }
}
