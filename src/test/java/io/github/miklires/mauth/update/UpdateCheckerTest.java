package io.github.miklires.mauth.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void comparesReleaseNumbers() {
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.0.9"));
        assertTrue(UpdateChecker.isNewer("v2.0", "1.9.9"));
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("0.9.9", "1.0.0"));
    }

    @Test
    void releaseWinsOverPrerelease() {
        assertTrue(UpdateChecker.isNewer("1.0.0", "1.0.0-rc.1"));
        assertFalse(UpdateChecker.isNewer("1.0.0-beta.2", "1.0.0"));
        assertTrue(UpdateChecker.isNewer("1.0.0-rc.2", "1.0.0-rc.1"));
    }

    @Test
    void ignoresNonVersionLabels() {
        assertFalse(UpdateChecker.isNewer("latest", "1.0.0"));
    }
}
