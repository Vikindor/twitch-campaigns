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
import io.github.vikindor.twitchcampaigns.telegram.TelegramRateLimitException;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Main {
    private record SendOutcome<T>(T state, boolean rateLimited, int sentCount) {
    }

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
        DropCacheState nextDropState = mergeDropState(previousDropState, dropCampaigns, clock);
        List<DropCampaign> newDropCampaigns = findNewDropCampaigns(dropCampaigns, previousDropState);
        List<DropCampaign> pendingDropCampaigns = findPendingDropCampaigns(dropCampaigns, nextDropState);

        List<RewardCampaign> rewardCampaigns = rewardCampaignClient.fetchCampaigns();
        RewardCacheState previousRewardState = rewardStateStore.load();
        boolean rewardBootstrapRun = previousRewardState.campaignsById().isEmpty();
        RewardCacheState nextRewardState = mergeRewardState(previousRewardState, rewardCampaigns, clock);
        List<RewardCampaign> newRewardCampaigns = findNewRewardCampaigns(rewardCampaigns, previousRewardState);
        List<RewardCampaign> pendingRewardCampaigns = findPendingRewardCampaigns(rewardCampaigns, nextRewardState);

        boolean telegramRateLimited = false;
        int sentDropNotifications = 0;
        int sentRewardNotifications = 0;

        if (dropBootstrapRun) {
            System.out.println("Drop bootstrap run detected: cache is empty, skipping Telegram posts for new drops.");
        } else {
            SendOutcome<DropCacheState> dropSendOutcome = sendPendingDropCampaignsToTelegram(
                    telegramClient,
                    config.telegramChatId(),
                    pendingDropCampaigns,
                    nextDropState,
                    clock
            );
            nextDropState = dropSendOutcome.state();
            telegramRateLimited = dropSendOutcome.rateLimited();
            sentDropNotifications = dropSendOutcome.sentCount();
        }

        if (rewardBootstrapRun) {
            System.out.println("Reward bootstrap run detected: cache is empty, skipping Telegram posts for new rewards.");
        } else if (telegramRateLimited) {
            System.out.println("[429] Skipping reward Telegram posts because Telegram rate limit was already hit while sending drops.");
        } else {
            SendOutcome<RewardCacheState> rewardSendOutcome = sendPendingRewardCampaignsToTelegram(
                    telegramClient,
                    config.telegramChatId(),
                    pendingRewardCampaigns,
                    nextRewardState,
                    clock
            );
            nextRewardState = rewardSendOutcome.state();
            telegramRateLimited = rewardSendOutcome.rateLimited();
            sentRewardNotifications = rewardSendOutcome.sentCount();
        }

        dropStateStore.save(nextDropState);
        rewardStateStore.save(nextRewardState);

        if (!telegramRateLimited) {
            debugSendLatestDropCampaign(config, telegramClient, config.telegramChatId(), dropCampaigns);
            debugSendLatestRewardCampaign(config, telegramClient, config.telegramChatId(), rewardCampaigns);
        } else {
            System.out.println("[429] Skipping debug Telegram messages because Telegram rate limit was hit earlier in the run.");
        }

        System.out.printf("Fetched %d drops, detected %d new.%n", dropCampaigns.size(), newDropCampaigns.size());
        System.out.printf(
                "Drop notifications: new in this fetch %d, sent %d, pending %d%n",
                newDropCampaigns.size(),
                sentDropNotifications,
                findPendingDropCampaigns(dropCampaigns, nextDropState).size()
        );
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
        System.out.printf(
                "Reward notifications: new in this fetch %d, sent %d, pending %d%n",
                newRewardCampaigns.size(),
                sentRewardNotifications,
                findPendingRewardCampaigns(rewardCampaigns, nextRewardState).size()
        );
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

    private static SendOutcome<DropCacheState> sendPendingDropCampaignsToTelegram(
            TelegramClient telegramClient,
            String telegramChatId,
            List<DropCampaign> pendingCampaigns,
            DropCacheState state,
            Clock clock
    ) throws Exception {
        if (pendingCampaigns.isEmpty()) {
            return new SendOutcome<>(state, false, 0);
        }

        DropCacheState currentState = state;
        int sentCount = 0;

        for (DropCampaign campaign : pendingCampaigns) {
            try {
                telegramClient.sendMessage(telegramChatId, DropTelegramFormatter.formatCampaign(campaign));
                currentState = markDropCampaignAsNotified(currentState, campaign.id(), clock.instant());
                sentCount++;
                System.out.printf("Sent Telegram message for pending drop: %s%n", campaign.id());
            } catch (TelegramRateLimitException exception) {
                System.out.printf(
                        "[429] Telegram rate limit hit while sending drops. retry_after=%d. Saving progress and ending run successfully.%n",
                        exception.retryAfterSeconds()
                );
                return new SendOutcome<>(currentState, true, sentCount);
            }
        }

        return new SendOutcome<>(currentState, false, sentCount);
    }

    private static SendOutcome<RewardCacheState> sendPendingRewardCampaignsToTelegram(
            TelegramClient telegramClient,
            String telegramChatId,
            List<RewardCampaign> pendingCampaigns,
            RewardCacheState state,
            Clock clock
    ) throws Exception {
        if (pendingCampaigns.isEmpty()) {
            return new SendOutcome<>(state, false, 0);
        }

        RewardCacheState currentState = state;
        int sentCount = 0;

        for (RewardCampaign campaign : pendingCampaigns) {
            try {
                telegramClient.sendMessage(telegramChatId, RewardTelegramFormatter.formatCampaign(campaign));
                currentState = markRewardCampaignAsNotified(currentState, campaign.id(), clock.instant());
                sentCount++;
                System.out.printf("Sent Telegram message for pending reward: %s%n", campaign.id());
            } catch (TelegramRateLimitException exception) {
                System.out.printf(
                        "[429] Telegram rate limit hit while sending rewards. retry_after=%d. Saving progress and ending run successfully.%n",
                        exception.retryAfterSeconds()
                );
                return new SendOutcome<>(currentState, true, sentCount);
            }
        }

        return new SendOutcome<>(currentState, false, sentCount);
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

    private static List<DropCampaign> findPendingDropCampaigns(List<DropCampaign> currentCampaigns, DropCacheState state) {
        return currentCampaigns.stream()
                .filter(campaign -> {
                    CachedDropCampaign cachedCampaign = state.campaignsById().get(campaign.id());
                    return cachedCampaign != null && cachedCampaign.notifiedAt() == null;
                })
                .toList();
    }

    private static List<RewardCampaign> findNewRewardCampaigns(List<RewardCampaign> currentCampaigns, RewardCacheState previousState) {
        var knownIds = previousState.campaignsById().keySet();
        return currentCampaigns.stream()
                .filter(campaign -> !knownIds.contains(campaign.id()))
                .toList();
    }

    private static List<RewardCampaign> findPendingRewardCampaigns(List<RewardCampaign> currentCampaigns, RewardCacheState state) {
        return currentCampaigns.stream()
                .filter(campaign -> {
                    CachedRewardCampaign cachedCampaign = state.campaignsById().get(campaign.id());
                    return cachedCampaign != null && cachedCampaign.notifiedAt() == null;
                })
                .toList();
    }

    private static DropCacheState mergeDropState(DropCacheState previousState, List<DropCampaign> currentCampaigns, Clock clock) {
        Instant now = clock.instant();
        Map<String, CachedDropCampaign> merged = new LinkedHashMap<>();

        for (DropCampaign campaign : currentCampaigns) {
            CachedDropCampaign existing = previousState.campaignsById().get(campaign.id());
            Instant firstSeenAt = existing == null ? now : existing.firstSeenAt();
            Instant notifiedAt = existing == null ? null : existing.notifiedAt();
            merged.put(campaign.id(), CachedDropCampaign.from(campaign, firstSeenAt, notifiedAt));
        }

        return new DropCacheState(now, merged);
    }

    private static RewardCacheState mergeRewardState(RewardCacheState previousState, List<RewardCampaign> currentCampaigns, Clock clock) {
        Instant now = clock.instant();
        Map<String, CachedRewardCampaign> merged = new LinkedHashMap<>();

        for (RewardCampaign campaign : currentCampaigns) {
            CachedRewardCampaign existing = previousState.campaignsById().get(campaign.id());
            Instant firstSeenAt = existing == null ? now : existing.firstSeenAt();
            Instant notifiedAt = existing == null ? null : existing.notifiedAt();
            merged.put(campaign.id(), CachedRewardCampaign.from(campaign, firstSeenAt, notifiedAt));
        }

        return new RewardCacheState(now, merged);
    }

    private static DropCacheState markDropCampaignAsNotified(DropCacheState state, String campaignId, Instant notifiedAt) {
        Map<String, CachedDropCampaign> updatedCampaigns = new LinkedHashMap<>(state.campaignsById());
        CachedDropCampaign existing = updatedCampaigns.get(campaignId);

        if (existing == null) {
            return state;
        }

        updatedCampaigns.put(
                campaignId,
                new CachedDropCampaign(
                        existing.id(),
                        existing.gameId(),
                        existing.gameDisplayName(),
                        existing.name(),
                        existing.ownerName(),
                        existing.status(),
                        existing.startAt(),
                        existing.endAt(),
                        existing.gameBoxArtUrl(),
                        existing.detailsUrl(),
                        existing.imageUrl(),
                        existing.rewards(),
                        existing.firstSeenAt(),
                        notifiedAt
                )
        );
        return new DropCacheState(state.updatedAt(), updatedCampaigns);
    }

    private static RewardCacheState markRewardCampaignAsNotified(RewardCacheState state, String campaignId, Instant notifiedAt) {
        Map<String, CachedRewardCampaign> updatedCampaigns = new LinkedHashMap<>(state.campaignsById());
        CachedRewardCampaign existing = updatedCampaigns.get(campaignId);

        if (existing == null) {
            return state;
        }

        updatedCampaigns.put(
                campaignId,
                new CachedRewardCampaign(
                        existing.id(),
                        existing.gameId(),
                        existing.gameDisplayName(),
                        existing.brand(),
                        existing.name(),
                        existing.status(),
                        existing.summary(),
                        existing.startsAt(),
                        existing.endsAt(),
                        existing.imageUrl(),
                        existing.externalUrl(),
                        existing.aboutUrl(),
                        existing.requirementLabel(),
                        existing.rewards(),
                        existing.firstSeenAt(),
                        notifiedAt
                )
        );
        return new RewardCacheState(state.updatedAt(), updatedCampaigns);
    }
}
