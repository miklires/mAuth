package io.github.miklires.mauth.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordModeTest {

    @Test
    void oldAccountsStayOptional() {
        long cutoff = 2_000;
        assertFalse(DiscordMode.REQUIRED_FOR_NEW.requiresLink(Instant.ofEpochSecond(1_999), cutoff));
        assertTrue(DiscordMode.REQUIRED_FOR_NEW.requiresLink(Instant.ofEpochSecond(2_000), cutoff));
    }

    @Test
    void modes() {
        Instant registered = Instant.now();
        assertFalse(DiscordMode.DISABLED.requiresLink(registered, 0));
        assertFalse(DiscordMode.OPTIONAL.requiresLink(registered, 0));
        assertTrue(DiscordMode.REQUIRED_AFTER_REGISTER.requiresLink(registered, Long.MAX_VALUE));
    }
}
