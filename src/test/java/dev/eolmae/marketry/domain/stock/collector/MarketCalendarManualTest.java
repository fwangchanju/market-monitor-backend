package dev.eolmae.marketry.domain.stock.collector;

import static org.assertj.core.api.Assertions.assertThat;

import dev.eolmae.marketry.common.enums.Country;
import dev.eolmae.marketry.common.enums.Zone;
import dev.eolmae.marketry.domain.stock.client.TossMarketCalendarClient;
import dev.eolmae.marketry.domain.stock.entity.MarketCalendar;
import dev.eolmae.marketry.domain.stock.enums.MarketCalendarStatus;
import dev.eolmae.marketry.domain.stock.properties.TossProperties;
import dev.eolmae.marketry.domain.stock.repository.MarketCalendarRepository;
import dev.eolmae.marketry.domain.stock.service.MarketCalendarService;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * 지정 날짜 또는 날짜 범위의 실제 토스 시간표를 요청하고 market_calendar에 적재한다. 일반 test와 CI에서 제외된다.
 *
 * <p>실행 조건: V9 스키마를 승인받아 먼저 적용하고, DB_URL / POSTGRES_PASSWORD 및
 * TOSS_CLIENT_ID / TOSS_CLIENT_SECRET을 프로세스 환경에 주입한다. 토스 허용 IP 등록이 필요하다.
 * 보호 env 파일은 읽지 않는다. Flyway와 DDL 생성은 꺼져 있으며 실제 데이터는 롤백하지 않는다.
 *
 * <p>실행 명령: ./gradlew.bat marketCalendarTest -PcalendarDate=2026-10-09
 * 또는 ./gradlew.bat marketCalendarTest -Dcalendar.date=2026-10-09.
 * CALENDAR_DATE 환경변수로도 날짜를 지정할 수 있다. 입력하지 않으면 현재 Asia/Seoul 날짜를 사용한다.
 * 날짜 형식은 yyyy-MM-dd이다. 이 전용 태스크는 다른 수동 테스트나 텔레그램을 실행하지 않는다.
 *
 * <p>범위 조회는 calendar.date를 시작일로, calendar.end-date를 종료일로 지정한다.
 * 종료일에 today를 지정하면 실행 시점의 Asia/Seoul 날짜까지 조회한다. 양 끝 날짜를 포함하고
 * 주말·공휴일도 조회한다. 날짜별 호출 사이에 1초를 대기하고 같은 클라이언트의 토큰을 재사용한다.
 * IntelliJ의 IntelliJ IDEA 테스트 실행기에서 VM options에
 * -Dcalendar.date=2026-09-01 -Dcalendar.end-date=today를 넣어 이 테스트를 실행한다.
 * Gradle에서는 marketCalendarTest -PcalendarDate=2026-09-01 -PcalendarEndDate=today로 실행한다.
 */
@Tag("manual")
@ActiveProfiles("prod")
@SpringBootTest(
        classes = MarketCalendarManualTest.ManualConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "scheduling.enabled=false",
            "spring.flyway.enabled=false",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.show-sql=false",
            "logging.level.org.hibernate.SQL=OFF",
            "logging.level.org.hibernate.orm.jdbc.bind=OFF",
            "logging.level.org.springframework.web.client=OFF",
            "logging.level.org.springframework.boot.context.config=OFF"
        })
class MarketCalendarManualTest {
    private static final Duration REQUEST_INTERVAL = Duration.ofSeconds(1);

    @Autowired
    private MarketCalendarCollector collector;

    @Autowired
    private MarketCalendarRepository repository;

    @Value("${calendar.date:${CALENDAR_DATE:}}")
    private String requestedDate;

    @Value("${calendar.end-date:}")
    private String requestedEndDate;

    @Test
    void 지정일의토스시간표를실제로조회하고저장한다() throws InterruptedException {
        LocalDate today = LocalDate.now(Zone.KST.zoneId());
        LocalDate startDate = parseDate(requestedDate, today);
        LocalDate endDate = parseDate(requestedEndDate, startDate);
        assertThat(endDate).as("종료일은 시작일보다 빠를 수 없습니다.").isAfterOrEqualTo(startDate);

        // 휴장일도 당일 응답을 저장해야 범위 내 모든 날짜의 수집 여부를 판단할 수 있다.
        for (LocalDate date = startDate; date.isAfter(endDate) == false; date = date.plusDays(1)) {
            if (date.isAfter(startDate)) {
                Thread.sleep(REQUEST_INTERVAL);
            }
            collectAndVerify(date);
        }
        System.out.printf("COMPLETE: country=KR start=%s end=%s%n", startDate, endDate);
    }

    private void collectAndVerify(LocalDate date) {
        assertThat(collector.collect(date))
                .as("%s 토스 시간표 조회와 저장 성공 (이전 날짜의 적재는 유지됩니다.)", date)
                .isTrue();
        MarketCalendar calendar = repository
                .findByCountryAndDate(Country.KR, date)
                .orElseThrow(() -> new AssertionError("요청 날짜의 시간표 행이 없습니다."));

        assertThat(calendar.getCountry()).isEqualTo(Country.KR);
        assertThat(calendar.getDate()).isEqualTo(date);
        assertThat(calendar.getStatus()).isIn(MarketCalendarStatus.TRADING_DAY, MarketCalendarStatus.HOLIDAY);
        if (calendar.getStatus() == MarketCalendarStatus.HOLIDAY) {
            assertThat(calendar.getIntegrated()).isNull();
        } else {
            assertThat(calendar.getIntegrated()).isNotNull();
        }
        System.out.printf("PASS: country=KR date=%s status=%s%n", date, calendar.getStatus());
    }

    private LocalDate parseDate(String value, LocalDate defaultDate) {
        if (value.isBlank()) {
            return defaultDate;
        }
        if (value.equals("today")) {
            return LocalDate.now(Zone.KST.zoneId());
        }
        if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") == false) {
            throw new AssertionError("calendar.date / calendar.end-date는 yyyy-MM-dd 또는 today여야 합니다.");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new AssertionError("calendar.date / calendar.end-date는 유효한 yyyy-MM-dd 또는 today여야 합니다.");
        }
    }

    // API와 저장에 필요한 빈만 등록하여 앱의 runner·수집·발송 경로를 실행하지 않는다.
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan("dev.eolmae.marketry")
    @EnableConfigurationProperties(TossProperties.class)
    @EnableJpaRepositories(
            basePackageClasses = MarketCalendarRepository.class,
            includeFilters =
                    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = MarketCalendarRepository.class))
    @Import({MarketCalendarCollector.class, MarketCalendarService.class, TossMarketCalendarClient.class})
    static class ManualConfiguration {
        @Bean
        RestClient restClient() {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(Duration.ofSeconds(10));
            return RestClient.builder().requestFactory(requestFactory).build();
        }
    }
}
