package io.github.vikindor.twitchcampaigns.drops.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record DropCacheState(
        Instant updatedAt,
        Map<String, CachedDropCampaign> campaignsById
) {
    @JsonCreator
    public DropCacheState {
        campaignsById = campaignsById == null ? new LinkedHashMap<>() : new LinkedHashMap<>(campaignsById);
    }

    public static DropCacheState empty() {
        return new DropCacheState(null, Map.of());
    }
}
