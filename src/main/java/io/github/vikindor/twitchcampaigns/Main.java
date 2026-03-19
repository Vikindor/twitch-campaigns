package io.github.vikindor.twitchcampaigns;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vikindor.twitchcampaigns.drops.client.DropCampaignClient;
import io.github.vikindor.twitchcampaigns.drops.model.CachedDropCampaign;
import io.github.vikindor.twitchcampaigns.drops.model.DropCacheState;
import io.github.vikindor.twitchcampaigns.drops.model.DropCampaign;
import io.github.vikindor.twitchcampaigns.drops.telegram.DropTelegramFormatter;
import io.github.vikindor.twitchcampaigns.rewards.client.RewardCampaignClient;
import io.github.vikindor.twitchcampaigns.rewards.model.CachedRewardCampaign;
import io.github.vikindor.twitchcampaigns.rewards.model.RewardCacheState;
import io.github.vikindor.twitchcampaigns.rewards.model.RewardCampaign;
import io.github.vikindor.twitchcampaigns.rewards.telegram.RewardTelegramFormatter;
import io.github.vikindor.twitchcampaigns.state.FileStateStore;
import io.github.vikindor.twitchcampaigns.state.GistStateStore;
import io.github.vikindor.twitchcampaigns.state.StateStore;
import io.github.vikindor.twitchcampaigns.support.ObjectMappers;
import io.github.vikindor.twitchcampaigns.telegram.TelegramClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        var config = AppConfig.fromEnv(System.getenv());
        config.validate();

        ObjectMapper objectMapper = ObjectMappers.create();
        HttpClient httpClient = HttpClient.newHttpClient();
        Clock clock = Clock.systemUTC();
        TelegramClient telegramClient = new TelegramClient(httpClient, objectMapper, config.telegramBotToken());

        StateStore<DropCacheState> dropStateStore = createDropStateStore(config, httpClient, objectMapper);
        StateStore<RewardCacheState> rewardStateStore = createRewardStateStore(config, httpClient, objectMapper);
        DropCampaignClient dropCampaignClient = new DropCampaignClient(httpClient, objectMapper, config.dropsApiUrl());
        RewardCampaignClient rewardCampaignClient = new RewardCampaignClient(httpClient, objectMapper, config.rewardsApiUrl());

        System.out.printf("Drops source: %s%n", config.dropsApiUrl());
        System.out.printf("Rewards source: %s%n", config.rewardsApiUrl());
        System.out.printf("Cache backend: %s%n", describeCache(config));
        System.out.printf("Telegram debug send latest campaign: %s%n", config.telegramDebugSendLatestCampaign());

        List<DropCampaign> dropCampaigns = dropCampaignClient.fetchCampaigns();
        DropCacheState previousDropState = dropStateStore.load();
        boolean dropBootstrapRun = previousDropState.campaignsById().isEmpty();
        List<DropCampaign> newDropCampaigns = findNewDropCampaigns(dropCampaigns, previousDropState);
        DropCacheState nextDropState = mergeDropState(previousDropState, dropCampaigns, clock);

        List<RewardCampaign> rewardCampaigns = rewardCampaignClient.fetchCampaigns();
        RewardCacheState previousRewardState = rewardStateStore.load();
        boolean rewardBootstrapRun = previousRewardState.campaignsById().isEmpty();
        List<RewardCampaign> newRewardCampaigns = findNewRewardCampaigns(rewardCampaigns, previousRewardState);
        RewardCacheState nextRewardState = mergeRewardState(previousRewardState, rewardCampaigns, clock);

        if (dropBootstrapRun) {
            System.out.println("Drop bootstrap run detected: cache is empty, skipping Telegram posts for new drops.");
        } else {
            sendNewDropCampaignsToTelegram(telegramClient, config.telegramChatId(), newDropCampaigns);
        }

        if (rewardBootstrapRun) {
            System.out.println("Reward bootstrap run detected: cache is empty, skipping Telegram posts for new rewards.");
        } else {
            sendNewRewardCampaignsToTelegram(telegramClient, config.telegramChatId(), newRewardCampaigns);
        }

        dropStateStore.save(nextDropState);
        rewardStateStore.save(nextRewardState);
        debugSendLatestDropCampaign(config, telegramClient, config.telegramChatId(), dropCampaigns);
        debugSendLatestRewardCampaign(config, telegramClient, config.telegramChatId(), rewardCampaigns);

        System.out.printf("Fetched %d drops, detected %d new.%n", dropCampaigns.size(), newDropCampaigns.size());
        for (DropCampaign campaign : newDropCampaigns) {
            System.out.printf(
                    "[NEW DROP] %s | %s | %s | %s%n",
                    campaign.id(),
                    campaign.gameDisplayName(),
                    campaign.name(),
                    campaign.startAt()
            );
        }

        System.out.printf("Fetched %d rewards, detected %d new.%n", rewardCampaigns.size(), newRewardCampaigns.size());
        for (RewardCampaign campaign : newRewardCampaigns) {
            System.out.printf(
                    "[NEW REWARD] %s | %s | %s | %s%n",
                    campaign.id(),
                    campaign.gameDisplayName(),
                    campaign.name(),
                    campaign.startsAt()
            );
        }
    }

    private static void sendNewDropCampaignsToTelegram(
            TelegramClient telegramClient,
            String telegramChatId,
            List<DropCampaign> newCampaigns
    ) throws Exception {
        if (newCampaigns.isEmpty()) {
            return;
        }

        for (DropCampaign campaign : newCampaigns) {
            telegramClient.sendMessage(telegramChatId, DropTelegramFormatter.formatCampaign(campaign));
            System.out.printf("Sent Telegram message for new drop: %s%n", campaign.id());
        }
    }

    private static void sendNewRewardCampaignsToTelegram(
            TelegramClient telegramClient,
            String telegramChatId,
            List<RewardCampaign> newCampaigns
    ) throws Exception {
        if (newCampaigns.isEmpty()) {
            return;
        }

        for (RewardCampaign campaign : newCampaigns) {
            telegramClient.sendMessage(telegramChatId, RewardTelegramFormatter.formatCampaign(campaign));
            System.out.printf("Sent Telegram message for new reward: %s%n", campaign.id());
        }
    }

    private static StateStore<DropCacheState> createDropStateStore(
            AppConfig config,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        return switch (config.cacheBackend()) {
            case FILE -> new FileStateStore<>(objectMapper, config.dropsLocalCachePath(), DropCacheState.class, DropCacheState::empty);
            case GIST -> new GistStateStore<>(
                    httpClient,
                    objectMapper,
                    config.dropsGistId(),
                    config.dropsGistFilename(),
                    config.gistToken(),
                    DropCacheState.class,
                    DropCacheState::empty
            );
        };
    }

    private static StateStore<RewardCacheState> createRewardStateStore(
            AppConfig config,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        return switch (config.cacheBackend()) {
            case FILE -> new FileStateStore<>(objectMapper, config.rewardsLocalCachePath(), RewardCacheState.class, RewardCacheState::empty);
            case GIST -> new GistStateStore<>(
                    httpClient,
                    objectMapper,
                    config.rewardsGistId(),
                    config.rewardsGistFilename(),
                    config.gistToken(),
                    RewardCacheState.class,
                    RewardCacheState::empty
            );
        };
    }

    private static String describeCache(AppConfig config) {
        return switch (config.cacheBackend()) {
            case FILE -> "file -> "
                    + config.dropsLocalCachePath().toAbsolutePath()
                    + " ; "
                    + config.rewardsLocalCachePath().toAbsolutePath();
            case GIST -> "gist -> "
                    + config.dropsGistId()
                    + " / "
                    + config.dropsGistFilename()
                    + " ; "
                    + config.rewardsGistId()
                    + " / "
                    + config.rewardsGistFilename();
        };
    }

    private static void debugSendLatestDropCampaign(
            AppConfig config,
            TelegramClient telegramClient,
            String telegramChatId,
            List<DropCampaign> campaigns
    ) throws Exception {
        if (!config.telegramDebugSendLatestCampaign() || campaigns.isEmpty()) {
            return;
        }

        DropCampaign latestCampaign = campaigns.get(campaigns.size() - 1);
        telegramClient.sendMessage(telegramChatId, DropTelegramFormatter.formatCampaign(latestCampaign));

        System.out.printf("Sent debug Telegram message for drop: %s%n", latestCampaign.id());
    }

    private static void debugSendLatestRewardCampaign(
            AppConfig config,
            TelegramClient telegramClient,
            String telegramChatId,
            List<RewardCampaign> campaigns
    ) throws Exception {
        if (!config.telegramDebugSendLatestCampaign() || campaigns.isEmpty()) {
            return;
        }

        RewardCampaign latestCampaign = campaigns.get(campaigns.size() - 1);
        telegramClient.sendMessage(telegramChatId, RewardTelegramFormatter.formatCampaign(latestCampaign));

        System.out.printf("Sent debug Telegram message for reward: %s%n", latestCampaign.id());
    }

    private static List<DropCampaign> findNewDropCampaigns(List<DropCampaign> currentCampaigns, DropCacheState previousState) {
        var knownIds = previousState.campaignsById().keySet();
        return currentCampaigns.stream()
                .filter(campaign -> !knownIds.contains(campaign.id()))
                .toList();
    }

    private static List<RewardCampaign> findNewRewardCampaigns(List<RewardCampaign> currentCampaigns, RewardCacheState previousState) {
        var knownIds = previousState.campaignsById().keySet();
        return currentCampaigns.stream()
                .filter(campaign -> !knownIds.contains(campaign.id()))
                .toList();
    }

    private static DropCacheState mergeDropState(DropCacheState previousState, List<DropCampaign> currentCampaigns, Clock clock) {
        Instant now = clock.instant();
        Map<String, CachedDropCampaign> merged = new LinkedHashMap<>(previousState.campaignsById());

        for (DropCampaign campaign : currentCampaigns) {
            CachedDropCampaign existing = merged.get(campaign.id());
            Instant firstSeenAt = existing == null ? now : existing.firstSeenAt();
            merged.put(campaign.id(), CachedDropCampaign.from(campaign, firstSeenAt));
        }

        return new DropCacheState(now, merged);
    }

    private static RewardCacheState mergeRewardState(RewardCacheState previousState, List<RewardCampaign> currentCampaigns, Clock clock) {
        Instant now = clock.instant();
        Map<String, CachedRewardCampaign> merged = new LinkedHashMap<>(previousState.campaignsById());

        for (RewardCampaign campaign : currentCampaigns) {
            CachedRewardCampaign existing = merged.get(campaign.id());
            Instant firstSeenAt = existing == null ? now : existing.firstSeenAt();
            merged.put(campaign.id(), CachedRewardCampaign.from(campaign, firstSeenAt));
        }

        return new RewardCacheState(now, merged);
    }
}
