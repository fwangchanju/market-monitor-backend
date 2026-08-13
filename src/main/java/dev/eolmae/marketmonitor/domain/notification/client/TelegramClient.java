package dev.eolmae.marketmonitor.domain.notification.client;

import dev.eolmae.marketmonitor.common.exception.ErrorCode;
import dev.eolmae.marketmonitor.common.exception.EscalateException;
import dev.eolmae.marketmonitor.domain.notification.dto.TelegramRequest;
import dev.eolmae.marketmonitor.domain.notification.properties.TelegramProperties;
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
    private final RestClient restClient;

    public void sendMessage(String chatId, String text) {
        try {
            restClient
                    .post()
                    .uri(botUrl("/sendMessage"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(TelegramRequest.of(chatId, text))
                    .retrieve()
                    .toBodilessEntity();

            log.debug("텔레그램 메시지 발송 완료: chatId={}", chatId);
        } catch (Exception e) {
            throw new EscalateException(ErrorCode.TELEGRAM_MESSAGE_SEND_FAILED, e);
        }
    }

    public void sendPhoto(String chatId, byte[] imageData) {
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("chat_id", chatId);
            form.add("photo", namedResource(imageData, "single_image.png"));

            restClient
                    .post()
                    .uri(botUrl("/sendPhoto"))
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("텔레그램 사진 발송 완료: chatId={}", chatId);
        } catch (Exception e) {
            throw new EscalateException(ErrorCode.TELEGRAM_IMAGE_SEND_FAILED, e);
        }
    }

    private String botUrl(String endpoint) {
        return BASE_URL + "/bot" + properties.botToken() + endpoint;
    }

    private ByteArrayResource namedResource(byte[] data, String filename) {
        return new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
