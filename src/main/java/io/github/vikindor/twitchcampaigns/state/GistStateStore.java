package io.github.vikindor.twitchcampaigns.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.function.Supplier;

public final class GistStateStore<T> implements StateStore<T> {
    private static final URI GITHUB_API_BASE_URI = URI.create("https://api.github.com");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String gistId;
    private final String gistFilename;
    private final String gistToken;
    private final Class<T> type;
    private final Supplier<T> emptyStateFactory;

    public GistStateStore(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String gistId,
            String gistFilename,
            String gistToken,
            Class<T> type,
            Supplier<T> emptyStateFactory
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.gistId = gistId;
        this.gistFilename = gistFilename;
        this.gistToken = gistToken;
        this.type = type;
        this.emptyStateFactory = emptyStateFactory;
    }

    @Override
    public T load() throws IOException, InterruptedException {
        HttpRequest request = baseRequestBuilder(gistUri()).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            throw new IOException("Gist not found: " + gistId);
        }

        ensureSuccess(response, "load gist cache");
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode fileContentNode = root.path("files").path(gistFilename).path("content");

        if (fileContentNode.isMissingNode() || fileContentNode.isNull() || fileContentNode.asText().isBlank()) {
            return emptyStateFactory.get();
        }

        return objectMapper.readValue(fileContentNode.asText(), type);
    }

    @Override
    public void save(T state) throws IOException, InterruptedException {
        String serializedState = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state);
        String body = objectMapper.writeValueAsString(
                Map.of("files", Map.of(gistFilename, Map.of("content", serializedState)))
        );

        HttpRequest request = baseRequestBuilder(gistUri())
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccess(response, "save gist cache");
    }

    private HttpRequest.Builder baseRequestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri)
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + gistToken)
                .header("X-GitHub-Api-Version", "2022-11-28");
    }

    private URI gistUri() {
        return GITHUB_API_BASE_URI.resolve("/gists/" + gistId);
    }

    private static void ensureSuccess(HttpResponse<String> response, String action) throws IOException {
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }

        throw new IOException("Failed to " + action + ". HTTP " + statusCode + ": " + response.body());
    }
}
