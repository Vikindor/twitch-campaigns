package io.github.vikindor.twitchcampaigns;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

public record AppConfig(
        URI dropsApiUrl,
        URI rewardsApiUrl,
        CacheBackend cacheBackend,
        Path dropsLocalCachePath,
        Path rewardsLocalCachePath,
        String dropsGistId,
        String rewardsGistId,
        String dropsGistFilename,
        String rewardsGistFilename,
        String gistToken,
        String telegramBotToken,
        String telegramChatId,
        boolean telegramDebugSendLatestCampaign,
        Duration campaignCacheRetention
) {
    public static AppConfig fromEnv(Map<String, String> env) {
        var apiUrl = toUriOrNull(env.get("TWITCH_DROPS_API_URL"));
        var rewardsApiUrl = toUriOrNull(env.get("TWITCH_REWARDS_API_URL"));
        var backend = CacheBackend.from(env.getOrDefault("CACHE_BACKEND", "file"));
        var dropsLocalCachePath = Paths.get(env.getOrDefault("DROPS_LOCAL_CACHE_PATH", ".cache/twitch-drops-cache.json"));
        var rewardsLocalCachePath = Paths.get(env.getOrDefault("REWARDS_LOCAL_CACHE_PATH", ".cache/twitch-rewards-cache.json"));
        var dropsGistId = trimToNull(env.get("DROPS_GIST_ID"));
        var rewardsGistId = trimToNull(env.get("REWARDS_GIST_ID"));
        var dropsGistFilename = env.getOrDefault("DROPS_GIST_FILENAME", "twitch-drops-cache.json");
        var rewardsGistFilename = env.getOrDefault("REWARDS_GIST_FILENAME", "twitch-rewards-cache.json");
        var gistToken = firstNonBlank(env.get("GIST_TOKEN"), env.get("GITHUB_TOKEN"));
        var telegramBotToken = trimToNull(env.get("TELEGRAM_BOT_TOKEN"));
        var telegramChatId = trimToNull(env.get("TELEGRAM_CHAT_ID"));
        var telegramDebugSendLatestCampaign = Boolean.parseBoolean(
                env.getOrDefault("TELEGRAM_DEBUG_SEND_LATEST_CAMPAIGN", "true")
        );
        var campaignCacheRetention = Duration.ofDays(
                Long.parseLong(env.getOrDefault("CAMPAIGN_CACHE_RETENTION_DAYS", "7"))
        );

        return new AppConfig(
                apiUrl,
                rewardsApiUrl,
                backend,
                dropsLocalCachePath,
                rewardsLocalCachePath,
                dropsGistId,
                rewardsGistId,
                dropsGistFilename,
                rewardsGistFilename,
                gistToken,
                telegramBotToken,
                telegramChatId,
                telegramDebugSendLatestCampaign,
                campaignCacheRetention
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
            if (dropsGistId == null || dropsGistId.isBlank()) {
                throw new IllegalStateException("DROPS_GIST_ID is required when CACHE_BACKEND=gist");
            }

            if (rewardsGistId == null || rewardsGistId.isBlank()) {
                throw new IllegalStateException("REWARDS_GIST_ID is required when CACHE_BACKEND=gist");
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

        if (campaignCacheRetention.isNegative() || campaignCacheRetention.isZero()) {
            throw new IllegalStateException("CAMPAIGN_CACHE_RETENTION_DAYS must be greater than 0");
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
