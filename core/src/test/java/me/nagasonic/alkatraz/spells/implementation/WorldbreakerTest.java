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
 * Section 6 ladder and the circle-9 TERRAIN ceiling for worldbreaker.
 *
 * <p>Zero Bukkit mocks. The block arithmetic is recomputed here from the yml rather than read off
 * the spell, so the ceiling the implementation enforces with one shared int[] and the figure the
 * budget is charged at are two independent statements of the same number.
 */
class WorldbreakerTest {

    private static YamlConfiguration spell() throws IOException {
        try (InputStream in = WorldbreakerTest.class.getResourceAsStream("/spells/worldbreaker.yml")) {
            assertNotNull(in, "bundled resource /spells/worldbreaker.yml is missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** Mirrors TerrainEditor.destroySphere's candidate enumeration, per dig site. */
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
        assertNotNull(role, "worldbreaker must declare a parseable role: key");
        assertEquals(SpellRole.TERRAIN, role, "the spell is judged on the blocks it moves");
        assertEquals(Element.EARTH, Element.valueOf(y.getString("element")),
                "the element is what the allowlist is checked against");
        assertTrue(SpellBudgets.isAllowed(Element.EARTH, role), "TERRAIN is in EARTH's allowlist");
        assertFalse(SpellBudgets.isAllowed(Element.EARTH, SpellRole.UTILITY),
                "earth is forbidden from UTILITY - there are no convenience spells");
    }

    @Test
    void powerIsTheLadderValueAndThereIsNoHitCount() throws IOException {
        YamlConfiguration y = spell();
        assertEquals(28.6, y.getDouble("power"), 1e-9,
                "power is the section 6 circle-9 ladder value, verbatim");
        assertFalse(y.isSet("hit_count"),
                "the slab crushes each target exactly once, so hit_count is absent and defaults to 1");
        assertTrue(y.getDouble("power") <= SpellBudgets.burstBudget(y.getInt("level")),
                "the declared single power key must sit inside the same-circle BURST budget");
    }

    @Test
    void ladderValuesMatchTheCircleNineRow() throws IOException {
        YamlConfiguration y = spell();
        assertEquals(600, y.getInt("mana_cost"), "section 6 ladder circle 9 mana is 600");
        assertEquals(20, y.getLong("cooldown"), "section 6 ladder circle 9 cooldown is 20");
        assertEquals(161, y.getDouble("required_focus"), 1e-9,
                "SpellBudgets.focusFor(9) is 161. The spell file said 162, but the 85% ceiling at "
                        + "circle 9 is 0.85 x 190 = 161.5 and focusCapsStayAtOrBelowEightyFivePercent"
                        + "OfMaxFocus would fail on 162 - the ladder gives way, not the rule");
        assertTrue(y.getDouble("required_focus") <= 0.85 * 190,
                "circle-9 focus must sit at or below 161.5");
        assertEquals(2.0, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 9 to the single point 40 ticks, so "
                        + "2.0 is the only value SpellBudgetsTest will accept here");
        assertTrue(SpellBudgets.castTimeRange(9).contains(40),
                "2.0s is 40 ticks, exactly the circle-9 cast-time window "
                        + SpellBudgets.castTimeRange(9).minTicks() + "-"
                        + SpellBudgets.castTimeRange(9).maxTicks());
    }

    @Test
    void levelAndRosterKeysAreTheAssignedOnes() throws IOException {
        YamlConfiguration y = spell();
        assertEquals(9, y.getInt("level"),
                "level drives required_circle, the spellbook requirement and the budget lookup");
        assertEquals("worldbreaker", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
        assertEquals("LRLLR", y.getString("code"), "cast code from the roster table");
        assertEquals(4148, y.getInt("gui_custom_model_data"),
                "4148 is worldbreaker's allocated gui_custom_model_data and is never reused");
    }

    @Test
    void bothFaultsAndTheSlabShareOneCeiling() throws IOException {
        YamlConfiguration y = spell();
        assertEquals(SpellBudgets.terrainBudget(9), y.getInt("terrain_budget"),
                "terrain_budget traces back to SpellBudgets.terrainBudget(9) = 2000");
        assertEquals(2000, y.getInt("terrain_budget"), "and 2000 is the circle-9 ceiling");

        int step = Math.max(1, y.getInt("rift_step"));
        int digTicks = y.getInt("rift_length") / step;
        assertEquals(15, digTicks, "rift_length 30 / rift_step 2 = 15 dig ticks per fault");
        int perSite = sphereBlockCount(y.getDouble("rift_radius"));
        assertEquals(33, perSite, "a radius-2 sphere is 33 candidate blocks");
        int faultBlocks = 2 * digTicks * perSite;
        assertEquals(990, faultBlocks, "two faults x 15 sites x 33 blocks");
        int slabBlocks = y.getInt("crush_width") * y.getInt("rift_length") * y.getInt("crush_lift");
        assertEquals(900, slabBlocks, "the slab places crush_width x rift_length x crush_lift");
        int natural = faultBlocks + slabBlocks;
        assertEquals(1890, natural, "990 dug plus 900 raised");
        assertTrue(natural <= y.getInt("terrain_budget"),
                "ONE shared allowance covers both faults and the slab: " + natural + " of "
                        + y.getInt("terrain_budget"));

        // The paid 'world_end' option asks for more than the ceiling, which is why it carries a
        // mana_cost impact and why both loops share one int[] counter rather than one each.
        int worldEndTicks = 44 / step;
        int worldEndBlocks = 2 * worldEndTicks * perSite + y.getInt("crush_width") * 44
                * y.getInt("crush_lift");
        assertEquals(22, worldEndTicks, "44 / 2 = 22 dig ticks per fault");
        assertEquals(2772, worldEndBlocks, "1452 dug plus 1320 raised");
        assertTrue(worldEndBlocks > y.getInt("terrain_budget"),
                worldEndBlocks + " blocks is over the ceiling of " + y.getInt("terrain_budget"));
        assertEquals(y.getInt("terrain_budget"), Math.min(worldEndBlocks, y.getInt("terrain_budget")),
                "the shared int[] allowance truncates at the budget rather than overshooting it");
    }
}
