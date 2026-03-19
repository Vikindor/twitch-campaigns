package io.github.vikindor.twitchcampaigns.cache;

import java.io.IOException;

public interface CampaignCacheStore {
    CacheState load() throws IOException, InterruptedException;

    void save(CacheState state) throws IOException, InterruptedException;
}
