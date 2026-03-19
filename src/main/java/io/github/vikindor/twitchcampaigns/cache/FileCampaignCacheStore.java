package io.github.vikindor.twitchcampaigns.cache;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileCampaignCacheStore implements CampaignCacheStore {
    private final ObjectMapper objectMapper;
    private final Path path;

    public FileCampaignCacheStore(ObjectMapper objectMapper, Path path) {
        this.objectMapper = objectMapper;
        this.path = path;
    }

    @Override
    public CacheState load() throws IOException {
        if (Files.notExists(path)) {
            return CacheState.empty();
        }

        String content = Files.readString(path);
        if (content.isBlank()) {
            return CacheState.empty();
        }

        return objectMapper.readValue(content, CacheState.class);
    }

    @Override
    public void save(CacheState state) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), state);
    }
}
