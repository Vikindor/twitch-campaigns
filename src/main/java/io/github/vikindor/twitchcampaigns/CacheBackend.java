package io.github.vikindor.twitchcampaigns;

public enum CacheBackend {
    FILE,
    GIST;

    public static CacheBackend from(String value) {
        return CacheBackend.valueOf(value.trim().toUpperCase());
    }
}
