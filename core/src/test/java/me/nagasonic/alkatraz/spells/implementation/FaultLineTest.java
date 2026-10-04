package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellAttackContext;
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
 * Spec section 9's double-charged spell: fault_line is a SUSTAIN field that is ALSO charged
 * against the TERRAIN budget, so both ceilings are asserted here and neither can absorb the
 * other's overspend.
 *
 * <p>Zero Bukkit mocks. Every figure is read straight off the bundled yml, and the block
 * arithmetic mirrors TerrainEditor.destroySphere's candidate enumeration, so the numbers the
 * implementation enforces and the numbers the test asserts are the same numbers.
 */
class FaultLineTest {

    private static YamlConfiguration spell() throws IOException {
        try (InputStream in = FaultLineTest.class.getResourceAsStream("/spells/fault_line.yml")) {
            assertNotNull(in, "bundled resource /spells/fault_line.yml is missing");
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

    /** How many 20-tick damage windows the fault walks, from the two keys that bound it. */
    private static int travelTicks(YamlConfiguration y) {
        int physical = y.getInt("crack_length") / Math.max(1, y.getInt("crack_step"));
        int declared = (int) Math.floor(y.getDouble("duration") * 20.0
                / SpellAttackContext.DAMAGE_INTERVAL_TICKS);
        return Math.max(0, Math.min(physical, declared));
    }

    @Test
    void roleIsTheDocumentedEarthSustainDeviation() throws IOException {
        YamlConfiguration y = spell();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "fault_line must declare a parseable role: key");
        assertEquals(SpellRole.SUSTAIN, role,
                "the crack keeps acting after the cast, so it declares SUSTAIN - the documented "
                        + "spec section 9 departure from EARTH's {TERRAIN, DENIAL, BURST} allowlist");
        assertEquals(Element.EARTH, Element.valueOf(y.getString("element")),
                "the element is what the allowlist is checked against");
        assertFalse(SpellBudgets.isAllowed(Element.EARTH, SpellRole.SUSTAIN),
                "this is exactly why it is a documented deviation rather than a silent one: the "
                        + "allowlist itself does not permit it");
        assertTrue(SpellBudgets.isAllowed(Element.EARTH, SpellRole.TERRAIN),
                "EARTH owns TERRAIN, which is the half of this spell that writes blocks");
        assertFalse(SpellBudgets.isAllowed(Element.EARTH, SpellRole.UTILITY),
                "earth is forbidden from UTILITY - there are no convenience spells");
    }

    @Test
    void bothBudgetsAreDeclaredSeparatelyFromEachOther() throws IOException {
        YamlConfiguration y = spell();
        assertTrue(y.isSet("power") && y.isSet("hit_count"),
                "the SUSTAIN side needs both keys for the health total to be assertable at all");
        assertTrue(y.isSet("terrain_budget"),
                "fault_line spends from the TERRAIN budget as well, so it declares one");
        int circle = y.getInt("level");
        assertEquals(7, circle, "the ceiling is the circle-7 one");
        assertEquals(SpellBudgets.terrainBudget(circle), y.getInt("terrain_budget"),
                "terrain_budget traces back to SpellBudgets.terrainBudget(7) = 950");
        assertEquals(950, y.getInt("terrain_budget"), "and 950 is that number");
        assertTrue(y.getInt("terrain_budget") <= SpellBudgets.terrainBudget(7),
                "the terrain spend is charged against the circle-7 TERRAIN budget of "
                        + SpellBudgets.terrainBudget(7));
    }

    @Test
    void ladderValuesMatchTheCircleSevenRow() throws IOException {
        YamlConfiguration y = spell();
        assertEquals(240, y.getInt("mana_cost"), "section 6 ladder circle 7 mana is 240");
        assertEquals(13, y.getLong("cooldown"), "section 6 ladder circle 7 cooldown is 13");
        assertEquals(144, y.getDouble("required_focus"), 1e-9, "section 6 ladder circle 7 focus is 144");
        assertTrue(y.getInt("required_focus") <= 0.85 * 170,
                "circle-7 focus must sit at or below the 85% ceiling of the 170 maxFocus - 144 is "
                        + "under the 144.5 ceiling and must not be raised back to 145");
        assertEquals(1.25, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 7 to the single point 25 ticks, so "
                        + "1.25 is the only value SpellBudgetsTest will accept here");
        assertTrue(SpellBudgets.castTimeRange(7).contains(25),
                "1.25s is 25 ticks, exactly the circle-7 cast-time window "
                        + SpellBudgets.castTimeRange(7).minTicks() + "-"
                        + SpellBudgets.castTimeRange(7).maxTicks());
    }

    @Test
    void levelAndRosterKeysAreTheAssignedOnes() throws IOException {
        YamlConfiguration y = spell();
        assertEquals(7, y.getInt("level"),
                "level drives required_circle, the spellbook requirement and both budget lookups");
        assertEquals("fault_line", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
        assertEquals("LLSLR", y.getString("code"), "cast code from the roster table");
        assertEquals(4146, y.getInt("gui_custom_model_data"),
                "4146 is fault_line's allocated gui_custom_model_data and is never reused");
    }

    @Test
    void cadenceIsTheSustainIntervalAndTheLifeTotalLandsOnTheBudget() throws IOException {
        YamlConfiguration y = spell();

        assertEquals(20L, SpellAttackContext.DAMAGE_INTERVAL_TICKS,
                "the field's repeat period IS the cadence constant, so the applications land on "
                        + "ticks 20, 40, 60 ... and nowhere else");
        assertEquals(10, travelTicks(y),
                "crack_length 40 / crack_step 4 and a 10 second life both give 10 windows");
        assertEquals(y.getInt("hit_count"), travelTicks(y),
                "hit_count must equal the number of damage windows so the total is assertable");

        double total = y.getDouble("power") * y.getInt("hit_count");
        assertEquals(76.0, total, 1e-9, "7.6 x 10 = 76.0");
        assertEquals(SpellBudgets.sustainBudget(7), 76.0, 1e-9,
                "the circle-7 SUSTAIN budget is 76, and this lands exactly on it");
        assertTrue(total <= SpellBudgets.sustainBudget(7),
                "the field deals " + total + " over its life, inside the circle-7 SUSTAIN budget");

        int blocksPerWindow = sphereBlockCount(y.getDouble("crack_radius"))
                + 2 * y.getInt("crack_width");
        assertEquals(33, sphereBlockCount(y.getDouble("crack_radius")),
                "a radius-2 sphere is 33 candidate blocks");
        assertEquals(37, blocksPerWindow, "33 destroyed plus a 4-block lip, two sides of crack_width 2");
        int terrainTotal = blocksPerWindow * travelTicks(y);
        assertEquals(370, terrainTotal, "10 windows x 37 blocks");
        assertTrue(terrainTotal <= y.getInt("terrain_budget"),
                "the crack's terrain spend is " + terrainTotal + ", inside the ceiling of "
                        + y.getInt("terrain_budget"));
    }
}
