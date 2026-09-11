package dev.eolmae.marketmonitor.domain.notification.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * TelegramProperties는 record라 프로퍼티 이름을 하나라도 틀리면 조용히 0/null로 바인딩되고, 그
 * 뒤에야 {@link dev.eolmae.marketmonitor.domain.notification.schedule.TelegramSendSchedule}의 기동
 * 검증이 실패한다 — 드러나는 시점이 배포 직후 컨테이너 기동 실패다. 이 레포에는 @Tag("manual")이 아닌
 * @SpringBootTest가 없어서 ./gradlew test로는 이 배선이 한 번도 돌지 않는다. DB도 외부 API도 필요
 * 없어 CI에서 도는 이 테스트로 배선 자체를 검증한다.
 */
class TelegramPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "telegram.bot-token=token",
                    "telegram.chat-id=chat-id",
                    "telegram.developer-chat-id=dev-chat-id",
                    // application.properties의 네 값을 그대로 쓴다.
                    "telegram.send-minute=10",
                    "telegram.send-interval-minutes=15",
                    "telegram.map-interval-minutes=120",
                    "telegram.before-minutes=15");

    @Test
    void application_properties와_같은_이름으로_바인딩된다() {
        contextRunner.run(context -> {
            TelegramProperties properties = context.getBean(TelegramProperties.class);
            assertThat(properties.botToken()).isEqualTo("token");
            assertThat(properties.chatId()).isEqualTo("chat-id");
            assertThat(properties.developerChatId()).isEqualTo("dev-chat-id");
            assertThat(properties.sendMinute()).isEqualTo(10);
            assertThat(properties.sendIntervalMinutes()).isEqualTo(15);
            assertThat(properties.mapIntervalMinutes()).isEqualTo(120);
            assertThat(properties.beforeMinutes()).isEqualTo(15);
        });
    }

    @EnableConfigurationProperties(TelegramProperties.class)
    static class TestConfig {}
}
