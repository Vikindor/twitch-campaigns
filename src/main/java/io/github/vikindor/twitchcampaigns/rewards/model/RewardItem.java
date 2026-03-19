package io.github.vikindor.twitchcampaigns.rewards.model;

public record RewardItem(
        String id,
        String name,
        String imageUrl,
        String redemptionUrl
) {
}
