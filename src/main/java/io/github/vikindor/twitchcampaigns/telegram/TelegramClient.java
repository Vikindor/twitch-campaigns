package io.github.vikindor.twitchcampaigns.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public final class TelegramClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String botToken;

    public TelegramClient(HttpClient httpClient, ObjectMapper objectMapper, String botToken) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.botToken = botToken;
    }

    public void sendMessage(String chatId, String text) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of(
                "chat_id", chatId,
                "text", text,
                "parse_mode", "HTML",
                "disable_web_page_preview", false
        ));

        HttpRequest request = HttpRequest.newBuilder(apiUri("/sendMessage"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) {
            throw toRateLimitException(response.body());
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Telegram sendMessage failed. HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private TelegramRateLimitException toRateLimitException(String responseBody) {
        int retryAfterSeconds = 0;

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            retryAfterSeconds = root.path("parameters").path("retry_after").asInt(0);
        } catch (IOException ignored) {
        }

        return new TelegramRateLimitException(
                "Telegram sendMessage failed. HTTP 429: " + responseBody,
                retryAfterSeconds
        );
    }

    private URI apiUri(String method) {
        return URI.create("https://api.telegram.org/bot" + botToken + method);
    }
}
