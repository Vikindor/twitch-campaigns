package io.github.vikindor.twitchcampaigns.rewards.model;

import java.time.Instant;
import java.util.List;

public record CachedRewardCampaign(
        String id,
        String gameId,
        String gameDisplayName,
        String brand,
        String name,
        String status,
        String summary,
        Instant startsAt,
        Instant endsAt,
        String imageUrl,
        String externalUrl,
        String aboutUrl,
        String requirementLabel,
        List<RewardItem> rewards,
        Instant firstSeenAt,
        Instant notifiedAt,
        Instant lastSeenAt
) {
    public static CachedRewardCampaign from(
            RewardCampaign campaign,
            Instant firstSeenAt,
            Instant notifiedAt,
            Instant lastSeenAt
    ) {
        return new CachedRewardCampaign(
                campaign.id(),
                campaign.gameId(),
                campaign.gameDisplayName(),
                campaign.brand(),
                campaign.name(),
                campaign.status(),
                campaign.summary(),
                campaign.startsAt(),
                campaign.endsAt(),
                campaign.imageUrl(),
                campaign.externalUrl(),
                campaign.aboutUrl(),
                campaign.requirementLabel(),
                campaign.rewards(),
                firstSeenAt,
                notifiedAt,
                lastSeenAt
        );
    }
}
