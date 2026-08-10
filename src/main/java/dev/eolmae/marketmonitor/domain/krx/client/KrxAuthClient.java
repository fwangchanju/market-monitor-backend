package dev.eolmae.marketmonitor.domain.krx.client;

import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.krx.enums.KrxResponseCode;
import java.net.CookieManager;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "krx.enabled", havingValue = "true")
public class KrxAuthClient {

    private static final String LOGIN_PAGE = "https://data.krx.co.kr/contents/MDC/COMS/client/MDCCOMS001.cmd";
    private static final String LOGIN_JSP = "https://data.krx.co.kr/contents/MDC/COMS/client/view/login.jsp?site=mdc";
    private static final String LOGIN_URL = "https://data.krx.co.kr/contents/MDC/COMS/client/MDCCOMS001D1.cmd";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    @Value("${krx.login-id}")
    private String loginId;

    @Value("${krx.login-pw}")
    private String loginPw;

    private volatile String cachedCookie;

    public String getCookie() {
        if (cachedCookie == null) {
            cachedCookie = login();
        }
        return cachedCookie;
    }

    public String refreshCookie() {
        log.info("KRX 세션 만료 — 재로그인 시도");
        cachedCookie = login();
        return cachedCookie;
    }

    private String login() {
        CookieManager cookieManager = new CookieManager();
        OkHttpClient client = new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(cookieManager))
                .build();

        try {
            client.newCall(new Request.Builder()
                            .url(LOGIN_PAGE)
                            .header("User-Agent", USER_AGENT)
                            .build())
                    .execute()
                    .close();

            client.newCall(new Request.Builder()
                            .url(LOGIN_JSP)
                            .header("User-Agent", USER_AGENT)
                            .header("Referer", LOGIN_PAGE)
                            .build())
                    .execute()
                    .close();

            String responseBody = doLoginRequest(client, false);

            if (KrxResponseCode.DUPLICATE_LOGIN.includedIn(responseBody)) {
                log.info("KRX 중복 로그인 감지 — skipDup=Y 재시도");
                responseBody = doLoginRequest(client, true);
            }

            if (!KrxResponseCode.LOGIN_SUCCESS.includedIn(responseBody)) {
                throw new EscalateException(ErrorCode.KRX_LOGIN_FAILED, responseBody);
            }

            String cookie = cookieManager.getCookieStore().getCookies().stream()
                    .map(c -> c.getName() + "=" + c.getValue())
                    .collect(Collectors.joining("; "));

            log.info("KRX 로그인 성공");
            return cookie;

        } catch (Exception e) {
            throw EscalateException.wrap(ErrorCode.KRX_LOGIN_FAILED, e);
        }
    }

    private String doLoginRequest(OkHttpClient client, boolean skipDup) throws Exception {
        FormBody.Builder form = new FormBody.Builder()
                .add("mbrNm", "")
                .add("telNo", "")
                .add("di", "")
                .add("certType", "")
                .add("mbrId", loginId)
                .add("pw", loginPw);
        if (skipDup) {
            form.add("skipDup", "Y");
        }

        try (Response response = client.newCall(new Request.Builder()
                        .url(LOGIN_URL)
                        .post(form.build())
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", LOGIN_PAGE)
                        .build())
                .execute()) {
            return response.body().string();
        }
    }
}
