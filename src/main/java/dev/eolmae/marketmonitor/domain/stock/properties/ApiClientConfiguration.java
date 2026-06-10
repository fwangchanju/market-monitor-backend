package dev.eolmae.marketmonitor.domain.stock.properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ApiClientConfiguration {

    @Bean
    public RestClient restClient() {
        return RestClient
                .create(); // 이렇게 써도 되나? .create() 라고 되어 있으니까 왠지 RestClient 쓰는데 마다 객체를 생성하는건 아닌가 하는 생각이 드네. RateLimiter
        // Bean 도 여기에 두는게 더 나으려나?
    }
}
