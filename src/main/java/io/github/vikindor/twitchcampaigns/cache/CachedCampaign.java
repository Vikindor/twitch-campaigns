package io.github.vikindor.twitchcampaigns.cache;

import io.github.vikindor.twitchcampaigns.model.CampaignReward;
import io.github.vikindor.twitchcampaigns.model.DropCampaign;

import java.time.Instant;
import java.util.List;

public record CachedCampaign(
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
        List<CampaignReward> rewards,
        Instant firstSeenAt
) {

    public static CachedCampaign from(DropCampaign campaign, Instant firstSeenAt) {
        return new CachedCampaign(
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
                firstSeenAt
        );
    }
}
