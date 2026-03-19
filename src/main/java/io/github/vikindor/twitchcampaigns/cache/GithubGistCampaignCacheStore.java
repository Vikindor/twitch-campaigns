package io.github.vikindor.twitchcampaigns.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public final class GithubGistCampaignCacheStore implements CampaignCacheStore {
    private static final URI GITHUB_API_BASE_URI = URI.create("https://api.github.com");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String gistId;
    private final String gistFilename;
    private final String githubToken;

    public GithubGistCampaignCacheStore(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String gistId,
            String gistFilename,
            String githubToken
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.gistId = gistId;
        this.gistFilename = gistFilename;
        this.githubToken = githubToken;
    }

    @Override
    public CacheState load() throws IOException, InterruptedException {
        HttpRequest request = baseRequestBuilder(gistUri()).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            throw new IOException("Gist not found: " + gistId);
        }

        ensureSuccess(response, "load gist cache");
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode fileContentNode = root.path("files").path(gistFilename).path("content");

        if (fileContentNode.isMissingNode() || fileContentNode.isNull() || fileContentNode.asText().isBlank()) {
            return CacheState.empty();
        }

        return objectMapper.readValue(fileContentNode.asText(), CacheState.class);
    }

    @Override
    public void save(CacheState state) throws IOException, InterruptedException {
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
                .header("Authorization", "Bearer " + githubToken)
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
