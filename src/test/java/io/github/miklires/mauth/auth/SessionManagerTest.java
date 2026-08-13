package io.github.miklires.mauth.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionManagerTest {

    @Test
    void cachedSessionRequiresSameUuidAndIp() {
        UUID uuid = UUID.randomUUID();
        SessionManager.IpSession session = new SessionManager.IpSession("127.0.0.1", uuid, 1_000L);

        assertTrue(session.matches("127.0.0.1", uuid, 2_000L, 5_000L));
        assertFalse(session.matches("127.0.0.1", UUID.randomUUID(), 2_000L, 5_000L));
        assertFalse(session.matches("127.0.0.2", uuid, 2_000L, 5_000L));
    }

    @Test
    void cachedSessionExpires() {
        UUID uuid = UUID.randomUUID();
        SessionManager.IpSession session = new SessionManager.IpSession("127.0.0.1", uuid, 1_000L);

        assertFalse(session.matches("127.0.0.1", uuid, 7_000L, 5_000L));
    }
}
