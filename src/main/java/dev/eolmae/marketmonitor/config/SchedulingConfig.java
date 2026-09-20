package dev.eolmae.marketmonitor.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// MarketMonitorApplication에서 분리 — 수동 발송 테스트가 @SpringBootTest로 앱을 통째로 띄우면
// @EnableScheduling도 같이 살아나서, 캡처가 도는 몇 초 사이 정각 cron이 겹치면 수집·발송이 한 번 더
// 나간다. scheduling.enabled=false로 그 테스트에서만 끌 수 있게 별도 설정 클래스로 뺐다.
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
class SchedulingConfig {}
