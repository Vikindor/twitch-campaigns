package io.github.vikindor.twitchcampaigns.drops.model;

import java.time.Instant;
import java.util.List;

public record CachedDropCampaign(
        String id,
        String gameId,
        String gameDisplayName,
        String name,
        String ownerName,
        String status,
        Instant startAt,
        Instant endAt,
        String gameBoxArtUrl,
        String detailsUrl,
        String imageUrl,
        List<DropBenefit> rewards,
        Instant firstSeenAt,
        Instant notifiedAt
) {
    public static CachedDropCampaign from(DropCampaign campaign, Instant firstSeenAt, Instant notifiedAt) {
        return new CachedDropCampaign(
                campaign.id(),
                campaign.gameId(),
                campaign.gameDisplayName(),
                campaign.name(),
                campaign.ownerName(),
                campaign.status(),
                campaign.startAt(),
                campaign.endAt(),
                campaign.gameBoxArtUrl(),
                campaign.detailsUrl(),
                campaign.imageUrl(),
                campaign.rewards(),
                firstSeenAt,
                notifiedAt
        );
    }
}
