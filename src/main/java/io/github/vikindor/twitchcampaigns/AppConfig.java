package io.github.vikindor.twitchcampaigns;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public record AppConfig(
        URI dropsApiUrl,
        URI rewardsApiUrl,
        CacheBackend cacheBackend,
        Path dropsLocalCachePath,
        Path rewardsLocalCachePath,
        String gistId,
        String dropsGistFilename,
        String rewardsGistFilename,
        String gistToken,
        String telegramBotToken,
        String telegramChatId,
        boolean telegramDebugSendLatestCampaign
) {
    public static AppConfig fromEnv(Map<String, String> env) {
        var apiUrl = toUriOrNull(env.get("TWITCH_DROPS_API_URL"));
        var rewardsApiUrl = toUriOrNull(env.get("TWITCH_REWARDS_API_URL"));
        var backend = CacheBackend.from(env.getOrDefault("CACHE_BACKEND", "file"));
        var dropsLocalCachePath = Paths.get(env.getOrDefault("DROPS_LOCAL_CACHE_PATH", ".cache/twitch-drops-cache.json"));
        var rewardsLocalCachePath = Paths.get(env.getOrDefault("REWARDS_LOCAL_CACHE_PATH", ".cache/twitch-rewards-cache.json"));
        var gistId = trimToNull(env.get("GIST_ID"));
        var dropsGistFilename = env.getOrDefault("DROPS_GIST_FILENAME", "twitch-drops-cache.json");
        var rewardsGistFilename = env.getOrDefault("REWARDS_GIST_FILENAME", "twitch-rewards-cache.json");
        var gistToken = firstNonBlank(env.get("GIST_TOKEN"), env.get("GITHUB_TOKEN"));
        var telegramBotToken = trimToNull(env.get("TELEGRAM_BOT_TOKEN"));
        var telegramChatId = trimToNull(env.get("TELEGRAM_CHAT_ID"));
        var telegramDebugSendLatestCampaign = Boolean.parseBoolean(
                env.getOrDefault("TELEGRAM_DEBUG_SEND_LATEST_CAMPAIGN", "true")
        );

        return new AppConfig(
                apiUrl,
                rewardsApiUrl,
                backend,
                dropsLocalCachePath,
                rewardsLocalCachePath,
                gistId,
                dropsGistFilename,
                rewardsGistFilename,
                trimToNull(gistToken),
                telegramBotToken,
                telegramChatId,
                telegramDebugSendLatestCampaign
        );
    }

    public void validate() {
        if (dropsApiUrl == null) {
            throw new IllegalStateException("TWITCH_DROPS_API_URL is required");
        }

        if (rewardsApiUrl == null) {
            throw new IllegalStateException("TWITCH_REWARDS_API_URL is required");
        }

        if (cacheBackend == CacheBackend.GIST) {
            if (gistId == null || gistId.isBlank()) {
                throw new IllegalStateException("GIST_ID is required when CACHE_BACKEND=gist");
            }

            if (gistToken == null || gistToken.isBlank()) {
                throw new IllegalStateException("GIST_TOKEN is required when CACHE_BACKEND=gist");
            }
        }

        if (telegramBotToken == null || telegramBotToken.isBlank()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN is required");
        }

        if (telegramChatId == null || telegramChatId.isBlank()) {
            throw new IllegalStateException("TELEGRAM_CHAT_ID is required");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstNonBlank(String first, String second) {
        String firstTrimmed = trimToNull(first);
        return firstTrimmed != null ? firstTrimmed : trimToNull(second);
    }

    private static URI toUriOrNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : URI.create(trimmed);
    }
}
