package io.github.vikindor.twitchcampaigns.cache;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record CacheState(
        Instant updatedAt,
        Map<String, CachedCampaign> campaignsById
) {

    @JsonCreator
    public CacheState {
        campaignsById = campaignsById == null ? new LinkedHashMap<>() : new LinkedHashMap<>(campaignsById);
    }

    public static CacheState empty() {
        return new CacheState(null, Map.of());
    }
}
