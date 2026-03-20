package io.github.vikindor.twitchcampaigns.drops.telegram;

import io.github.vikindor.twitchcampaigns.drops.model.DropBenefit;
import io.github.vikindor.twitchcampaigns.drops.model.DropCampaign;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.StringJoiner;

public final class DropTelegramFormatter {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private DropTelegramFormatter() {
    }

    public static String formatCampaign(DropCampaign campaign) {
        StringBuilder message = new StringBuilder();
        message.append("<b>").append(escapeHtml(safe(campaign.gameDisplayName()))).append("</b>\n");
        message.append(escapeHtml(safe(campaign.ownerName()))).append("\n\n");
        message.append(formatDateRange(campaign.startAt(), campaign.endAt(), campaign.gameBoxArtUrl())).append("\n\n");
        message.append("Drops | ").append(formatCampaignName(campaign.name(), campaign.detailsUrl())).append(":\n");
        message.append(formatRewards(campaign.rewards(), campaign.name()));

        return message.toString();
    }

    private static String formatRewards(Iterable<DropBenefit> rewards, String campaignName) {
        StringJoiner joiner = new StringJoiner("\n");
        boolean hasRewards = false;
        for (DropBenefit reward : rewards) {
            hasRewards = true;
            joiner.add("- " + escapeHtml(safe(reward.requirementLabel())) + ": " + escapeHtml(safe(reward.name())));
        }

        return hasRewards ? joiner.toString() : "- " + escapeHtml(safe(campaignName));
    }

    private static String formatDateRange(java.time.Instant startAt, java.time.Instant endAt, String posterUrl) {
        String start = formatInstant(startAt);
        String end = formatInstant(endAt);
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
