package me.nagasonic.alkatraz.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Guards {@link Skin#fromURL}: only sessionserver.mojang.com URLs may be fetched.
 * Any other host must be rejected before the URL is opened or cached, so a
 * caller-controlled string can never be used for SSRF.
 */
class SkinAllowlistTest {

    @Test
    void fromUrlRejectsInternalMetadataAddress() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Skin.fromURL("http://169.254.169.254/latest/meta-data"));
        if (e.getMessage() != null) {
            org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("sessionserver"));
        }
    }

    @Test
    void fromUrlRejectsWrongHost() {
        assertThrows(IllegalArgumentException.class,
                () -> Skin.fromURL("https://example.com/session/minecraft/profile/x"));
    }

    @Test
    void fromUrlRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Skin.fromURL(null));
    }
}
