package me.nagasonic.alkatraz.hooks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionTest {

    @Test
    void bypassOverridesRegionDenial() {
        assertTrue(Protection.decide(true, false));
    }

    @Test
    void noBypassFallsThroughToRegionResult() {
        assertFalse(Protection.decide(false, false));
        assertTrue(Protection.decide(false, true));
    }

    @Test
    void missingRegionQueryDefaultsToAllowed() {
        assertTrue(Protection.decide(false, true));
    }
}
