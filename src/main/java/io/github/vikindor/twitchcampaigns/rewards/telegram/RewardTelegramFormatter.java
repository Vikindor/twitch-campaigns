package io.github.vikindor.twitchcampaigns.rewards.telegram;

import io.github.vikindor.twitchcampaigns.rewards.model.RewardCampaign;
import io.github.vikindor.twitchcampaigns.rewards.model.RewardItem;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.StringJoiner;

public final class RewardTelegramFormatter {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private RewardTelegramFormatter() {
    }

    public static String formatCampaign(RewardCampaign campaign) {
        StringBuilder message = new StringBuilder();
        message.append("<b>").append(escapeHtml(safe(campaign.gameDisplayName()))).append("</b>\n");
        message.append(escapeHtml(safe(campaign.brand()))).append("\n\n");
        message.append(formatDateRange(campaign.startsAt(), campaign.endsAt(), campaign.imageUrl())).append("\n\n");
        message.append("Rewards | ").append(formatCampaignName(campaign.name(), firstNonBlank(campaign.externalUrl(), campaign.aboutUrl()))).append(":\n");
        message.append(formatRewards(campaign.requirementLabel(), campaign.rewards()));

        return message.toString();
    }

    private static String formatRewards(String requirementLabel, Iterable<RewardItem> rewards) {
        StringJoiner joiner = new StringJoiner("\n");
        boolean hasRewards = false;
        for (RewardItem reward : rewards) {
            hasRewards = true;
            joiner.add("- " + escapeHtml(safe(requirementLabel)) + ": " + escapeHtml(safe(reward.name())));
        }

        return hasRewards ? joiner.toString() : "- " + escapeHtml(safe(requirementLabel));
    }

    private static String formatDateRange(java.time.Instant startsAt, java.time.Instant endsAt, String posterUrl) {
        String start = formatInstant(startsAt);
        String end = formatInstant(endsAt);
        String posterLink = posterUrl == null || posterUrl.isBlank()
                ? "-"
                : "<a href=\"" + escapeHtmlAttribute(posterUrl) + "\">-</a>";

        return start + " " + posterLink + " " + end + " UTC";
    }

    private static String formatInstant(java.time.Instant value) {
        return value == null ? "-" : DATE_TIME_FORMATTER.format(value);
    }

    private static String formatCampaignName(String campaignName, String detailsUrl) {
        String safeName = escapeHtml(safe(campaignName));
        if (detailsUrl == null || detailsUrl.isBlank()) {
            return safeName;
        }

        return "<a href=\"" + escapeHtmlAttribute(detailsUrl) + "\">" + safeName + "</a>";
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String escapeHtmlAttribute(String value) {
        return escapeHtml(value).replace("\"", "&quot;");
    }
}
