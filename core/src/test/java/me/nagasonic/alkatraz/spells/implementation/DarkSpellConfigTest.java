package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YML-only assertions, so no Bukkit mock is needed. getResourceAsStream plus
 * YamlConfiguration, following AlkatrazConfigPathsTest.
 */
class DarkSpellConfigTest {

    private static final String[] DARK_SPELLS = {"debuff", "dark_tendrils", "summon_zombies"};

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = DarkSpellConfigTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void everyDarkSpellDeclaresARoleAllowedForItsElement() throws IOException {
        for (String id : DARK_SPELLS) {
            YamlConfiguration yml = load(id);
            SpellRole role = SpellRole.parse(yml.getString("role"));
            assertNotNull(role, id + " must declare a role: key");
            assertTrue(SpellBudgets.isAllowed(Element.DARK, role),
                    id + " declares " + role + " but DARK may not use it");
        }
    }

    @Test
    void darkHasNoDefensiveOption() throws IOException {
        assertFalse(SpellBudgets.isAllowed(Element.DARK, SpellRole.DENIAL),
                "DARK is forbidden from DENIAL - its power is taken, never given");
        for (String id : DARK_SPELLS) {
            assertFalse(SpellRole.DENIAL == SpellRole.parse(load(id).getString("role")),
                    id + " must not be a denial spell");
        }
    }

    @Test
    void darkCarriesNoTerrainSpell() throws IOException {
        assertFalse(SpellBudgets.isAllowed(Element.DARK, SpellRole.TERRAIN),
                "DARK and LIGHT together carry no TERRAIN spells at all");
        for (String id : DARK_SPELLS) {
            assertFalse(SpellRole.TERRAIN == SpellRole.parse(load(id).getString("role")),
                    id + " must not be TERRAIN");
        }
    }

    @Test
    void tendrilsBurstPowerMatchesTheTable() throws IOException {
        assertEquals(5.5, load("dark_tendrils").getDouble("power"), 1e-9,
                "dark_tendrils is circle 3 after the 2-applications-collapse-to-1 fix");
        assertTrue(load("dark_tendrils").getDouble("power") <= SpellBudgets.burstBudget(3),
                "the deduped tendril burst must sit inside the circle-3 BURST budget of "
                        + SpellBudgets.burstBudget(3));
    }

    @Test
    void darkTendrilsKeepsItsPinnedModelData() throws IOException {
        assertEquals(4118, load("dark_tendrils").getInt("gui_custom_model_data"),
                "dark_tendrils keeps 4118");
        assertTrue(load("dark_tendrils").getInt("gui_custom_model_data")
                        != load("water_sphere").getInt("gui_custom_model_data"),
                "dark_tendrils never collided with water_sphere");
    }

    @Test
    void ladderCostsMatchTheTable() throws IOException {
        assertEquals(24, load("debuff").getInt("mana_cost"), "circle-2 mana");
        assertEquals(48, load("dark_tendrils").getInt("mana_cost"), "circle-3 mana");
        assertEquals(75, load("summon_zombies").getInt("mana_cost"), "circle-4 mana");
    }
}
