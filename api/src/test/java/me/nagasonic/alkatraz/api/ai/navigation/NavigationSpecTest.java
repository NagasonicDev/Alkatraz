package me.nagasonic.alkatraz.api.ai.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NavigationSpecTest {

    @Test
    void defaultSpec_constructs() {
        assertNotNull(new NavigationSpec.Default());
    }

    @Test
    void fixedType_wrapsKind() {
        NavigationSpec.FixedType s = new NavigationSpec.FixedType(NavigationKind.AMPHIBIOUS);
        assertEquals(NavigationKind.AMPHIBIOUS, s.kind());
        assertThrows(NullPointerException.class, () -> new NavigationSpec.FixedType(null));
    }

    @Test
    void navigationKind_hasFourKinds() {
        assertEquals(4, NavigationKind.values().length);
        assertNotNull(NavigationKind.GROUND);
        assertNotNull(NavigationKind.FLYING);
        assertNotNull(NavigationKind.WATER);
        assertNotNull(NavigationKind.AMPHIBIOUS);
    }
}