package io.github.vikindor.twitchcampaigns;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vikindor.twitchcampaigns.cache.CampaignCacheStore;
import io.github.vikindor.twitchcampaigns.cache.CampaignChangeDetector;
import io.github.vikindor.twitchcampaigns.cache.CacheState;
import io.github.vikindor.twitchcampaigns.cache.FileCampaignCacheStore;
import io.github.vikindor.twitchcampaigns.cache.GithubGistCampaignCacheStore;
import io.github.vikindor.twitchcampaigns.client.TwitchDropsApiClient;
import io.github.vikindor.twitchcampaigns.model.DropCampaign;
import io.github.vikindor.twitchcampaigns.support.ObjectMappers;
import io.github.vikindor.twitchcampaigns.telegram.TelegramClient;
import io.github.vikindor.twitchcampaigns.telegram.TelegramMessageFormatter;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        var config = AppConfig.fromEnv(System.getenv());
        config.validate();

        ObjectMapper objectMapper = ObjectMappers.create();
        HttpClient httpClient = HttpClient.newHttpClient();
        Clock clock = Clock.systemUTC();

        CampaignCacheStore cacheStore = createCacheStore(config, httpClient, objectMapper);
        TwitchDropsApiClient apiClient = new TwitchDropsApiClient(httpClient, objectMapper, config.dropsApiUrl());

        System.out.printf("Drops source: %s%n", config.dropsApiUrl());
        System.out.printf("Cache backend: %s%n", describeCache(config));
        System.out.printf("Telegram debug send latest campaign: %s%n", config.telegramDebugSendLatestCampaign());

        List<DropCampaign> campaigns = apiClient.fetchCampaigns();
        CacheState previousState = cacheStore.load();
        boolean bootstrapRun = previousState.campaignsById().isEmpty();
        List<DropCampaign> newCampaigns = CampaignChangeDetector.findNewCampaigns(campaigns, previousState);
        CacheState nextState = CampaignChangeDetector.merge(previousState, campaigns, clock);

        if (bootstrapRun) {
            System.out.println("Bootstrap run detected: cache is empty, skipping Telegram posts for new campaigns.");
        } else {
            sendNewCampaignsToTelegram(config, httpClient, objectMapper, newCampaigns);
        }
        cacheStore.save(nextState);
        debugSendLatestCampaign(config, httpClient, objectMapper, campaigns);

        System.out.printf("Fetched %d campaigns, detected %d new.%n", campaigns.size(), newCampaigns.size());
        for (DropCampaign campaign : newCampaigns) {
            System.out.printf(
                    "[NEW] %s | %s | %s | %s%n",
                    campaign.id(),
                    campaign.gameDisplayName(),
                    campaign.name(),
                    campaign.startAt()
            );
        }
    }

    private static void sendNewCampaignsToTelegram(
            AppConfig config,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            List<DropCampaign> newCampaigns
    ) throws Exception {
        if (newCampaigns.isEmpty()) {
            return;
        }

        TelegramClient telegramClient = new TelegramClient(httpClient, objectMapper, config.telegramBotToken());
        for (DropCampaign campaign : newCampaigns) {
            String text = TelegramMessageFormatter.formatDebugLatestCampaign(campaign);
            telegramClient.sendMessage(config.telegramChatId(), text);
            System.out.printf("Sent Telegram message for new campaign: %s%n", campaign.id());
        }
    }

    private static CampaignCacheStore createCacheStore(
            AppConfig config,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        return switch (config.cacheBackend()) {
            case FILE -> new FileCampaignCacheStore(objectMapper, config.localCachePath());
            case GIST -> new GithubGistCampaignCacheStore(
                    httpClient,
                    objectMapper,
                    config.gistId(),
                    config.gistFilename(),
                    config.gistToken()
            );
        };
    }

    private static String describeCache(AppConfig config) {
        return switch (config.cacheBackend()) {
            case FILE -> "file -> " + config.localCachePath().toAbsolutePath();
            case GIST -> "gist -> " + config.gistId() + "/" + config.gistFilename();
        };
    }

    private static void debugSendLatestCampaign(
            AppConfig config,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            List<DropCampaign> campaigns
    ) throws Exception {
        if (!config.telegramDebugSendLatestCampaign() || campaigns.isEmpty()) {
            return;
        }

        DropCampaign latestCampaign = campaigns.get(campaigns.size() - 1);
        String text = TelegramMessageFormatter.formatDebugLatestCampaign(latestCampaign);

        TelegramClient telegramClient = new TelegramClient(httpClient, objectMapper, config.telegramBotToken());
        telegramClient.sendMessage(config.telegramChatId(), text);

        System.out.printf("Sent debug Telegram message for campaign: %s%n", latestCampaign.id());
    }
}
