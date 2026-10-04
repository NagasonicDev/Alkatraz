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

    private static final String[] DARK_SPELLS = {"debuff", "dark_tendrils", "summon_zombies",
            "shadow_realm", "skeletal_knights"};

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
        for (String id : new String[]{"dark_tendrils", "shadow_realm"}) {
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
    void shadowRealmSustainTotalLandsInsideTheBudget() throws IOException {
        YamlConfiguration yml = load("shadow_realm");
        assertEquals(SpellRole.SUSTAIN, SpellRole.parse(yml.getString("role")),
                "the effect persists on the target after the cast ends");
        assertFalse(yml.isSet("power"),
                "spec section 7 lists '-' for shadow_realm's power; the base is damage_per_tick");

        double perHit = yml.getDouble("damage_per_tick");
        double duration = yml.getDouble("duration");
        long ticks = (long) (duration * 20);
        long oldHits = ticks / 5L;      // the old runTaskTimer period
        long newHits = ticks / 20L;     // SpellAttackContext.DAMAGE_INTERVAL_TICKS
        assertEquals(20, oldHits, "the old 5-tick scheduler period gave 20 damage events");
        assertEquals(5, newHits, "the 20-tick contract interval gives 5");
        assertEquals(60.0, perHit * oldHits, 1e-9, "20 x 3.0 = 60, over the budget");
        assertEquals(15.0, perHit * newHits, 1e-9, "5 x 3.0 = 15");
        assertTrue(perHit * oldHits > SpellBudgets.sustainBudget(5),
                "the old cadence was genuinely over the circle-5 SUSTAIN budget of "
                        + SpellBudgets.sustainBudget(5));
        assertTrue(perHit * newHits <= SpellBudgets.sustainBudget(5),
                "the 20-tick cadence brings it inside the budget");
    }

    @Test
    void theRepairedSpellIdsHoldDistinctModelData() throws IOException {
        int waterSphere = load("water_sphere").getInt("gui_custom_model_data");
        int skeletalKnights = load("skeletal_knights").getInt("gui_custom_model_data");
        int darkTendrils = load("dark_tendrils").getInt("gui_custom_model_data");
        assertEquals(4130, waterSphere, "water_sphere keeps 4130 - it is unchanged by the design");
        assertEquals(4167, skeletalKnights,
                "skeletal_knights was 4130, the same value as water_sphere - a live collision, "
                        + "and spec section 10.2 assigns it 4167");
        assertEquals(4118, darkTendrils, "dark_tendrils keeps 4118");
        assertTrue(waterSphere != skeletalKnights,
                "the water_sphere / skeletal_knights collision is repaired");
        assertTrue(darkTendrils != waterSphere, "dark_tendrils never collided to begin with");
    }

    @Test
    void ladderCostsMatchTheTable() throws IOException {
        assertEquals(24, load("debuff").getInt("mana_cost"), "circle-2 mana");
        assertEquals(48, load("dark_tendrils").getInt("mana_cost"), "circle-3 mana");
        assertEquals(75, load("summon_zombies").getInt("mana_cost"), "circle-4 mana");
        assertEquals(120, load("shadow_realm").getInt("mana_cost"), "circle-5 mana");
        assertEquals(165, load("skeletal_knights").getInt("mana_cost"), "circle-6 mana");
        assertEquals(11, load("skeletal_knights").getInt("cooldown"), "circle-6 cooldown");
        assertEquals(135, load("skeletal_knights").getInt("required_focus"), "circle-6 focus");
    }
}
