package io.github.vikindor.twitchcampaigns.telegram;

import io.github.vikindor.twitchcampaigns.model.CampaignReward;
import io.github.vikindor.twitchcampaigns.model.DropCampaign;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.StringJoiner;

public final class TelegramMessageFormatter {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private TelegramMessageFormatter() {
    }

    public static String formatDebugLatestCampaign(DropCampaign campaign) {
        StringBuilder message = new StringBuilder();
        message.append("<b>").append(escapeHtml(safe(campaign.gameDisplayName()))).append("</b>\n");
        message.append(escapeHtml(safe(campaign.ownerName()))).append("\n\n");
        message.append(formatDateRange(campaign)).append("\n\n");
        message.append(escapeHtml(safe(campaign.name()))).append(" | Rewards:\n");
        message.append(formatRewards(campaign));

        if (campaign.detailsUrl() != null && !campaign.detailsUrl().isBlank()) {
            message.append("\n\nDetails: ").append(escapeHtml(campaign.detailsUrl()));
        }

        return message.toString();
    }

    private static String formatInstant(java.time.Instant value) {
        return value == null ? "-" : DATE_TIME_FORMATTER.format(value);
    }

    private static String formatDateRange(DropCampaign campaign) {
        String start = formatInstant(campaign.startAt());
        String end = formatInstant(campaign.endAt());
        String posterLink = campaign.gameBoxArtUrl() == null || campaign.gameBoxArtUrl().isBlank()
                ? "-"
                : "<a href=\"" + escapeHtmlAttribute(campaign.gameBoxArtUrl()) + "\">-</a>";

        return start + " " + posterLink + " " + end + " UTC";
    }

    private static String formatRewards(DropCampaign campaign) {
        if (campaign.rewards() == null || campaign.rewards().isEmpty()) {
            return "- " + escapeHtml(campaign.name());
        }

        StringJoiner joiner = new StringJoiner("\n");
        for (CampaignReward reward : campaign.rewards()) {
            joiner.add("- " + escapeHtml(safe(reward.requirementLabel())) + ": " + escapeHtml(safe(reward.rewardName())));
        }
        return joiner.toString();
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
