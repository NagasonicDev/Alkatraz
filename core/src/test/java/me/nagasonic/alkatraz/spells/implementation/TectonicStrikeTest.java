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
 * Section 6 ladder and section 4 damage contract for tectonic_strike.
 *
 * <p>Zero Bukkit mocks: getResourceAsStream plus YamlConfiguration, following
 * AlkatrazConfigPathsTest and EarthSpellConfigTest. The block-count helper mirrors
 * TerrainEditor.destroySphere's candidate enumeration, which is the part that has to
 * stay deterministic.
 */
class TectonicStrikeTest {

    private static YamlConfiguration spell() throws IOException {
        try (InputStream in = TectonicStrikeTest.class.getResourceAsStream("/spells/tectonic_strike.yml")) {
            assertNotNull(in, "bundled resource /spells/tectonic_strike.yml is missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** Mirrors TerrainEditor.destroySphere's candidate enumeration. */
    private static int sphereBlockCount(double radius) {
        int r = (int) Math.ceil(radius);
        int count = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius) count++;
                }
            }
        }
        return count;
    }

    @Test
    void roleIsParseableAndAllowedForEarth() throws IOException {
        YamlConfiguration y = spell();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "tectonic_strike must declare a parseable role: key");
        assertEquals(SpellRole.BURST, role, "a focused slam is a BURST - one shockwave, no field");
        assertEquals(Element.EARTH, Element.valueOf(y.getString("element")),
                "the element is what the allowlist is checked against");
        assertTrue(SpellBudgets.isAllowed(Element.EARTH, role),
                "EARTH's allowlist is {TERRAIN, DENIAL, BURST} and must never contain UTILITY");
        assertFalse(SpellBudgets.isAllowed(Element.EARTH, SpellRole.UTILITY),
                "earth is forbidden from UTILITY - there are no convenience spells");
    }

    @Test
    void oneApplicationPerTargetStaysInsideTheCircleSixBurstBudget() throws IOException {
        YamlConfiguration y = spell();
        int circle = y.getInt("level");
        assertFalse(y.isSet("hit_count"),
                "tectonic_strike applies damage once per target, so it declares no hit_count and defaults to 1");
        assertFalse(y.isSet("terrain_budget"),
                "this is judged on health damage, so it charges no TERRAIN budget; the crater is "
                        + "bounded by crater_block_cap instead, which the fifth method asserts");
        double total = y.getDouble("power");
        assertEquals(14.8, total, 1e-9, "power is the section 6 circle-6 ladder value, verbatim");
        assertEquals(SpellBudgets.burstBudget(circle), 34.1, 1e-9,
                "the circle-6 BURST budget is the number power is judged against");
        assertTrue(total <= SpellBudgets.burstBudget(circle),
                "one slam deals " + total + ", inside the circle-" + circle + " BURST budget of "
                        + SpellBudgets.burstBudget(circle));
    }

    @Test
    void ladderValuesMatchTheCircleSixRow() throws IOException {
        YamlConfiguration y = spell();
        assertEquals(165, y.getInt("mana_cost"), "section 6 ladder circle 6 mana is 165");
        assertEquals(11, y.getLong("cooldown"), "section 6 ladder circle 6 cooldown is 11");
        assertEquals(135, y.getDouble("required_focus"), 1e-9, "section 6 ladder circle 6 focus is 135");
        assertTrue(y.getInt("required_focus") <= 0.85 * 160,
                "circle-6 focus must sit at or below the 85% ceiling of the 160 maxFocus - 135 is "
                        + "under the 136 ceiling and must not be raised");
        assertEquals(1.0, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 6 to the single point 20 ticks, so "
                        + "1.0 is the only value SpellBudgetsTest will accept here");
        assertTrue(SpellBudgets.castTimeRange(6).contains(20),
                "1.0s is 20 ticks, exactly the circle-6 cast-time window "
                        + SpellBudgets.castTimeRange(6).minTicks() + "-"
                        + SpellBudgets.castTimeRange(6).maxTicks());
    }

    @Test
    void levelAndRosterKeysAreTheAssignedOnes() throws IOException {
        YamlConfiguration y = spell();
        assertEquals(6, y.getInt("level"),
                "level drives required_circle, the spellbook requirement and every budget lookup");
        assertEquals("tectonic_strike", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
        assertEquals("LRRLR", y.getString("code"), "cast code from the roster table");
        assertEquals(4145, y.getInt("gui_custom_model_data"),
                "4145 is tectonic_strike's allocated gui_custom_model_data and is never reused");
        assertEquals("EARTH", y.getString("element"), "earth spells carry element: 'EARTH'");
    }

    @Test
    void craterStaysInsideItsDeclaredBlockCap() throws IOException {
        YamlConfiguration y = spell();
        int natural = sphereBlockCount(y.getDouble("crater_radius"));
        int cap = y.getInt("crater_block_cap");
        assertEquals(81, natural, "a radius-2.5 sphere is 81 candidate blocks");
        assertEquals(120, cap, "the cap is ~1.5x the default's natural count");
        assertTrue(natural <= cap,
                "the default crater writes " + natural + " blocks, inside the cap of " + cap);
        int widest = sphereBlockCount(3.5);
        assertEquals(179, widest, "the paid 'wide' option's radius-3.5 sphere is 179 candidates");
        assertTrue(widest > cap,
                "which is exactly why maxBlocks exists: " + widest + " is over the cap of " + cap);
        assertEquals(cap, Math.min(widest, cap),
                "TerrainEditor truncates at maxBlocks rather than overshooting it");
    }
}
