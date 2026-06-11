package dev.eolmae.marketmonitor.domain.notification.client;

import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramClient {

    private static final String BASE_URL = "https://api.telegram.org";

    private final TelegramProperties properties;
    private final RestClient restClient = RestClient.create(); // TODO RestClient Bean 사용

    public void sendMessage(String chatId, String text) {
        try {
            restClient
                    .post()
                    .uri(BASE_URL + "/bot" + properties.botToken() + "/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of( // TODO Map 대신 DTO
                            "chat_id", chatId,
                            "text", text,
                            "parse_mode", "HTML"))
                    .retrieve()
                    .toBodilessEntity();

            log.debug("텔레그램 메시지 발송 완료: chatId={}", chatId);
        } catch (Exception e) {
            log.error("텔레그램 메시지 발송 실패: chatId={}", chatId, e);
        }
    }

    public void sendPhoto(String chatId, byte[] imageData, String caption) {
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("chat_id", chatId);
            form.add("photo", new ByteArrayResource(imageData) {
                @Override
                public String getFilename() {
                    return "dashboard.png";
                }
            });
            if (caption != null && !caption.isBlank()) {
                form.add("caption", caption);
            }

            restClient
                    .post()
                    .uri(BASE_URL + "/bot" + properties.botToken() + "/sendPhoto")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("텔레그램 사진 발송 완료: chatId={}", chatId);
        } catch (Exception e) {
            log.error("텔레그램 사진 발송 실패: chatId={}", chatId, e);
        }
    }
}
