package io.github.vikindor.twitchcampaigns.cache;

import io.github.vikindor.twitchcampaigns.model.DropCampaign;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CampaignChangeDetector {

    private CampaignChangeDetector() {
    }

    public static List<DropCampaign> findNewCampaigns(List<DropCampaign> currentCampaigns, CacheState previousState) {
        var knownIds = previousState.campaignsById().keySet();
        return currentCampaigns.stream()
                .filter(campaign -> !knownIds.contains(campaign.id()))
                .toList();
    }

    public static CacheState merge(CacheState previousState, List<DropCampaign> currentCampaigns, Clock clock) {
        Instant now = clock.instant();
        Map<String, CachedCampaign> merged = new LinkedHashMap<>(previousState.campaignsById());

        for (DropCampaign campaign : currentCampaigns) {
            CachedCampaign existing = merged.get(campaign.id());
            Instant firstSeenAt = existing == null ? now : existing.firstSeenAt();
            merged.put(campaign.id(), CachedCampaign.from(campaign, firstSeenAt));
        }

        return new CacheState(now, merged);
    }
}
