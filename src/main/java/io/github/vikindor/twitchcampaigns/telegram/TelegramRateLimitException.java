package io.github.vikindor.twitchcampaigns.telegram;

import java.io.IOException;

public final class TelegramRateLimitException extends IOException {
    private final int retryAfterSeconds;

    public TelegramRateLimitException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
