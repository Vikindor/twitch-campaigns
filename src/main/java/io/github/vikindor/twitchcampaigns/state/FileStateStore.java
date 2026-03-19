package io.github.vikindor.twitchcampaigns.state;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

public final class FileStateStore<T> implements StateStore<T> {
    private final ObjectMapper objectMapper;
    private final Path path;
    private final Class<T> type;
    private final Supplier<T> emptyStateFactory;

    public FileStateStore(ObjectMapper objectMapper, Path path, Class<T> type, Supplier<T> emptyStateFactory) {
        this.objectMapper = objectMapper;
        this.path = path;
        this.type = type;
        this.emptyStateFactory = emptyStateFactory;
    }

    @Override
    public T load() throws IOException {
        if (Files.notExists(path)) {
            return emptyStateFactory.get();
        }

        String content = Files.readString(path);
        if (content.isBlank()) {
            return emptyStateFactory.get();
        }

        return objectMapper.readValue(content, type);
    }

    @Override
    public void save(T state) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), state);
    }
}
