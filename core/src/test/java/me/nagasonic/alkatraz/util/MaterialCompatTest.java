package me.nagasonic.alkatraz.util;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MaterialCompatTest {

    @Test
    void resolvesTurtleScuteAcrossRename() {
        Material expected = Material.matchMaterial("TURTLE_SCUTE");
        if (expected == null) expected = Material.matchMaterial("SCUTE");
        assertNotNull(expected);

        assertEquals(expected, MaterialCompat.resolve("TURTLE_SCUTE", Material.BARRIER));
    }
}
