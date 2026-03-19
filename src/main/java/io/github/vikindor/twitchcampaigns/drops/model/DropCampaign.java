package io.github.vikindor.twitchcampaigns.drops.model;

import java.time.Instant;
import java.util.List;

public record DropCampaign(
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
        List<DropBenefit> rewards
) {
}
