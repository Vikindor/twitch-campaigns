package io.github.vikindor.twitchcampaigns.drops.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vikindor.twitchcampaigns.drops.model.DropBenefit;
import io.github.vikindor.twitchcampaigns.drops.model.DropCampaign;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DropCampaignClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI apiUrl;

    public DropCampaignClient(HttpClient httpClient, ObjectMapper objectMapper, URI apiUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
    }

    public List<DropCampaign> fetchCampaigns() throws IOException, InterruptedException {
        if ("file".equalsIgnoreCase(apiUrl.getScheme())) {
            JsonNode root = objectMapper.readTree(java.nio.file.Path.of(apiUrl).toFile());
            return extractCampaigns(root);
        }

        HttpRequest request = HttpRequest.newBuilder(apiUrl)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to fetch drops API. HTTP " + response.statusCode());
        }

        return extractCampaigns(objectMapper.readTree(response.body()));
    }

    public List<DropCampaign> extractCampaigns(JsonNode root) {
        if (!root.isArray()) {
            throw new IllegalArgumentException("Expected drops API root to be a JSON array");
        }

        List<DropCampaign> campaigns = new ArrayList<>();
        for (JsonNode groupNode : root) {
            String fallbackGameId = text(groupNode, "gameId");
            String fallbackGameDisplayName = text(groupNode, "gameDisplayName");
            String fallbackGameBoxArtUrl = text(groupNode, "gameBoxArtURL");

            for (JsonNode rewardNode : groupNode.path("rewards")) {
                String campaignId = requiredText(rewardNode, "id");
                JsonNode gameNode = rewardNode.path("game");
                JsonNode ownerNode = rewardNode.path("owner");

                campaigns.add(new DropCampaign(
                        campaignId,
                        firstNonBlank(text(gameNode, "id"), fallbackGameId),
                        firstNonBlank(text(gameNode, "displayName"), fallbackGameDisplayName),
                        text(rewardNode, "name"),
                        text(ownerNode, "name"),
                        text(rewardNode, "status"),
                        instant(rewardNode, "startAt"),
                        instant(rewardNode, "endAt"),
                        fallbackGameBoxArtUrl,
                        text(rewardNode, "detailsURL"),
                        text(rewardNode, "imageURL"),
                        extractRewards(rewardNode)
                ));
            }
        }

        campaigns.sort(
                Comparator.comparing(DropCampaign::startAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DropCampaign::id)
        );

        return campaigns;
    }

    private static List<DropBenefit> extractRewards(JsonNode rewardNode) {
        Map<String, DropBenefit> rewards = new LinkedHashMap<>();

        for (JsonNode timeBasedDropNode : rewardNode.path("timeBasedDrops")) {
            String requirementLabel = toRequirementLabel(timeBasedDropNode.path("requiredMinutesWatched").asInt(0));

            for (JsonNode benefitEdgeNode : timeBasedDropNode.path("benefitEdges")) {
                String benefitName = text(benefitEdgeNode.path("benefit"), "name");
                if (benefitName != null) {
                    String key = requirementLabel + "|" + benefitName;
                    rewards.putIfAbsent(key, new DropBenefit(requirementLabel, benefitName));
                }
            }

            String dropName = text(timeBasedDropNode, "name");
            if (dropName != null && timeBasedDropNode.path("benefitEdges").isEmpty()) {
                String key = requirementLabel + "|" + dropName;
                rewards.putIfAbsent(key, new DropBenefit(requirementLabel, dropName));
            }
        }

        if (rewards.isEmpty()) {
            return List.of(new DropBenefit("Reward", requiredText(rewardNode, "name")));
        }

        return List.copyOf(rewards.values());
    }

    private static String toRequirementLabel(int requiredMinutesWatched) {
        if (requiredMinutesWatched <= 0) {
            return "Sub";
        }

        if (requiredMinutesWatched % 60 == 0) {
            int hours = requiredMinutesWatched / 60;
            return "Watch " + hours + "h";
        }

        return "Watch " + requiredMinutesWatched + "m";
    }

    private static String requiredText(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return value;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }

        return sanitize(valueNode.asText());
    }

    private static Instant instant(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        return value == null ? null : Instant.parse(value);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }

        String sanitized = value
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        return sanitized.isEmpty() ? null : sanitized;
    }
}
