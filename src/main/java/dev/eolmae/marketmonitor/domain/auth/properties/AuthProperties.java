package dev.eolmae.marketmonitor.domain.auth.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private Google google = new Google();
    private String signupOwnerEmail = "";
    private String jwtSecret = "";
    private String frontendUrl = "http://localhost:5173";

    @Getter
    @Setter
    public static class Google {

        private String clientId = "";
        private String clientSecret = "";
    }
}
