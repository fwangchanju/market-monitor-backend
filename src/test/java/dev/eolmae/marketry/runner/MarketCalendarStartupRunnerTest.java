package dev.eolmae.marketry.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.eolmae.marketry.domain.stock.scheduler.MarketCalendarScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MarketCalendarStartupRunnerTest {

    private final MarketCalendarScheduler scheduler = mock(MarketCalendarScheduler.class);
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MarketCalendarStartupRunner.class)
            .withBean(MarketCalendarScheduler.class, () -> scheduler);

    @Test
    void 로컬_기동은_외부_시간표_조회_진입점을_만들지_않는다() {
        contextRunner.withPropertyValues("spring.profiles.active=local").run(context -> {
            assertThat(context).doesNotHaveBean(MarketCalendarStartupRunner.class);
            verifyNoInteractions(scheduler);
        });
    }

    @Test
    void 기본_프로필_기동은_외부_시간표_조회_진입점을_만들지_않는다() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(MarketCalendarStartupRunner.class);
            verifyNoInteractions(scheduler);
        });
    }

    @Test
    void 운영_수동_검증에서_스케줄링을_끄면_기동_조회도_생략한다() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod", "scheduling.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MarketCalendarStartupRunner.class);
                    verifyNoInteractions(scheduler);
                });
    }

    @Test
    void 운영_기동은_시간표_초기화를_호출한다() {
        contextRunner.withPropertyValues("spring.profiles.active=prod").run(context -> {
            assertThat(context).hasSingleBean(MarketCalendarStartupRunner.class);
            context.getBean(MarketCalendarStartupRunner.class).run(new DefaultApplicationArguments());
            verify(scheduler).initialize(any());
        });
    }
}
