package io.github.vikindor.twitchcampaigns.rewards.model;

import java.time.Instant;
import java.util.List;

public record RewardCampaign(
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
        List<RewardItem> rewards
) {
}
