package me.nagasonic.alkatraz.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerVersionTest {

    @Test
    void aheadOfLatestReleaseIsNotOutdated() {
        assertFalse(UpdateChecker.isRemoteNewer("0.9.1", "0.9.2"), "a local build newer than the release must not warn");
    }

    @Test
    void olderBuildIsOutdated() {
        assertTrue(UpdateChecker.isRemoteNewer("0.9.2", "0.9.1"), "a newer release must warn");
    }

    @Test
    void sameVersionIsNotOutdated() {
        assertFalse(UpdateChecker.isRemoteNewer("0.9.1", "0.9.1"), "equal versions must not warn");
    }

    @Test
    void numericNotLexicographic() {
        assertTrue(UpdateChecker.isRemoteNewer("0.10.0", "0.9.0"), "0.10 is after 0.9, not before it");
    }

    @Test
    void leadingVAndSuffixAreTolerated() {
        assertTrue(UpdateChecker.isRemoteNewer("v0.10.0-beta", "0.9.0"), "a v prefix and suffix must not break parsing");
    }
}
