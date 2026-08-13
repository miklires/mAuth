package io.github.miklires.mauth.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {

    private final TotpService service = new TotpService(null);

    @Test
    void verifiesRfc6238Sha1Vector() {
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertTrue(service.verifyTotp(secret, "287082", 59_000L));
        assertFalse(service.verifyTotp(secret, "287083", 59_000L));
    }

    @Test
    void rejectsMalformedCode() {
        assertFalse(service.verifyTotp("GEZDGNBVGY3TQOJQ", "12345", 0));
        assertFalse(service.verifyTotp("GEZDGNBVGY3TQOJQ", "abcdef", 0));
    }
}
