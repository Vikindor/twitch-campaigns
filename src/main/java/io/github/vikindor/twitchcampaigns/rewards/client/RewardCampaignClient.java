package io.github.vikindor.twitchcampaigns.rewards.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vikindor.twitchcampaigns.rewards.model.RewardCampaign;
import io.github.vikindor.twitchcampaigns.rewards.model.RewardItem;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RewardCampaignClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI apiUrl;

    public RewardCampaignClient(HttpClient httpClient, ObjectMapper objectMapper, URI apiUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
    }

    public List<RewardCampaign> fetchCampaigns() throws IOException, InterruptedException {
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
            throw new IOException("Failed to fetch rewards API. HTTP " + response.statusCode());
        }

        return extractCampaigns(objectMapper.readTree(response.body()));
    }

    public List<RewardCampaign> extractCampaigns(JsonNode root) {
        if (!root.isArray()) {
            throw new IllegalArgumentException("Expected rewards API root to be a JSON array");
        }

        List<RewardCampaign> campaigns = new ArrayList<>();
        for (JsonNode campaignNode : root) {
            JsonNode gameNode = campaignNode.path("game");
            JsonNode imageNode = campaignNode.path("image");
            JsonNode unlockRequirementsNode = campaignNode.path("unlockRequirements");

            campaigns.add(new RewardCampaign(
                    requiredText(campaignNode, "id"),
                    text(gameNode, "id"),
                    firstNonBlank(text(gameNode, "displayName"), text(campaignNode, "brand")),
                    text(campaignNode, "brand"),
                    text(campaignNode, "name"),
                    text(campaignNode, "status"),
                    text(campaignNode, "summary"),
                    instant(campaignNode, "startsAt"),
                    instant(campaignNode, "endsAt"),
                    text(imageNode, "image1xURL"),
                    text(campaignNode, "externalURL"),
                    text(campaignNode, "aboutURL"),
                    toRequirementLabel(
                            unlockRequirementsNode.path("minuteWatchedGoal").asInt(0),
                            unlockRequirementsNode.path("subsGoal").asInt(0)
                    ),
                    extractRewardItems(campaignNode.path("rewards"))
            ));
        }

        campaigns.sort(
                Comparator.comparing(RewardCampaign::startsAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(RewardCampaign::id)
        );

        return campaigns;
    }

    private static List<RewardItem> extractRewardItems(JsonNode rewardsNode) {
        List<RewardItem> rewards = new ArrayList<>();

        for (JsonNode rewardNode : rewardsNode) {
            rewards.add(new RewardItem(
                    requiredText(rewardNode, "id"),
                    text(rewardNode, "name"),
                    text(rewardNode.path("bannerImage"), "image1xURL"),
                    firstNonBlank(text(rewardNode, "redemptionURL"), text(rewardNode, "redemptionInstructions"))
            ));
        }

        return rewards;
    }

    private static String toRequirementLabel(int minuteWatchedGoal, int subsGoal) {
        String watchLabel = null;
        String subLabel = null;

        if (minuteWatchedGoal > 0) {
            if (minuteWatchedGoal % 60 == 0) {
                int hours = minuteWatchedGoal / 60;
                watchLabel = "Watch " + hours + "h";
            } else {
                watchLabel = "Watch " + minuteWatchedGoal + "m";
            }
        }

        if (subsGoal > 0) {
            subLabel = subsGoal == 1 ? "Sub" : "Subs x" + subsGoal;
        }

        if (watchLabel != null && subLabel != null) {
            return watchLabel + " + " + subLabel;
        }

        if (watchLabel != null) {
            return watchLabel;
        }

        if (subLabel != null) {
            return subLabel;
        }

        return "Reward";
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
