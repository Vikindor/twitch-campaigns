package io.github.vikindor.twitchcampaigns.rewards.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record RewardCacheState(
        Instant updatedAt,
        Map<String, CachedRewardCampaign> campaignsById
) {
    @JsonCreator
    public RewardCacheState {
        campaignsById = campaignsById == null ? new LinkedHashMap<>() : new LinkedHashMap<>(campaignsById);
    }

    public static RewardCacheState empty() {
        return new RewardCacheState(null, Map.of());
    }
}
