package io.github.miklires.mauth.auth;

import java.time.Instant;
import java.util.Locale;

public enum DiscordMode {
    DISABLED,
    OPTIONAL,
    REQUIRED_AFTER_REGISTER,
    REQUIRED_FOR_NEW;

    public static DiscordMode parse(String raw) {
        try {
            return valueOf(raw.replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown discord mode: " + raw);
        }
    }

    public boolean requiresForRegistration() {
        return this == REQUIRED_AFTER_REGISTER || this == REQUIRED_FOR_NEW;
    }

    public boolean requiresLink(Instant registeredAt, long cutoff) {
        return switch (this) {
            case DISABLED, OPTIONAL -> false;
            case REQUIRED_AFTER_REGISTER -> true;
            case REQUIRED_FOR_NEW -> registeredAt != null && registeredAt.getEpochSecond() >= cutoff;
        };
    }
}
