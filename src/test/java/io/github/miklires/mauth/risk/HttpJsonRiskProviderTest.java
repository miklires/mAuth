package io.github.miklires.mauth.risk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpJsonRiskProviderTest {

    @Test
    void readsConfiguredBooleanField() {
        assertEquals(true, HttpJsonRiskProvider.readBoolean("{\"proxy\": true}", "proxy"));
        assertEquals(false, HttpJsonRiskProvider.readBoolean("{\"proxy\":false}", "proxy"));
        assertNull(HttpJsonRiskProvider.readBoolean("{\"vpn\":true}", "proxy"));
    }
}
