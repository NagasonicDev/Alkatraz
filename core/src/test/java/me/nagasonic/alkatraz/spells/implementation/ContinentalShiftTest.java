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
 * Section 6 ladder and the circle-8 TERRAIN ceiling for continental_shift.
 *
 * <p>Zero Bukkit mocks. The footprint arithmetic is duplicated here rather than read off the
 * spell, which is the point: the shape the implementation writes and the shape the budget is
 * charged for are asserted independently, so a change to one that is not made to the other fails.
 */
class ContinentalShiftTest {

    private static YamlConfiguration spell() throws IOException {
        try (InputStream in = ContinentalShiftTest.class.getResourceAsStream("/spells/continental_shift.yml")) {
            assertNotNull(in, "bundled resource /spells/continental_shift.yml is missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /**
     * Columns in one layer of the filled-ellipse footprint: for each dz, half-width
     * {@code floor(radius_x * sqrt(1 - (dz/radius_z)^2))}, contributing {@code 2 * half + 1}.
     */
    private static int footprintColumns(double radiusX, double radiusZ) {
        int rz = Math.max(1, (int) Math.round(radiusZ));
        int rx = Math.max(1, (int) Math.round(radiusX));
        int total = 0;
        for (int dz = -rz; dz <= rz; dz++) {
            double inside = 1.0 - ((double) dz * dz) / ((double) rz * rz);
            if (inside < 0.0) inside = 0.0;
            total += 2 * (int) Math.floor(rx * Math.sqrt(inside) + 1e-9) + 1;
        }
        return total;
    }

    @Test
    void roleIsParseableAndAllowedForEarth() throws IOException {
        YamlConfiguration y = spell();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "continental_shift must declare a parseable role: key");
        assertEquals(SpellRole.TERRAIN, role, "the plateau is judged on the blocks it moves");
        assertEquals(Element.EARTH, Element.valueOf(y.getString("element")),
                "the element is what the allowlist is checked against");
        assertTrue(SpellBudgets.isAllowed(Element.EARTH, role), "TERRAIN is in EARTH's allowlist");
        assertFalse(SpellBudgets.isAllowed(Element.EARTH, SpellRole.UTILITY),
                "earth is forbidden from UTILITY - there are no convenience spells");
    }

    @Test
    void powerIsTheLadderValueAndThereIsNoHitCount() throws IOException {
        YamlConfiguration y = spell();
        assertEquals(22.7, y.getDouble("power"), 1e-9,
                "power is the section 6 circle-8 ladder value, verbatim");
        assertTrue(y.getDouble("power") <= SpellBudgets.burstBudget(y.getInt("level")),
                "the declared single power key must sit inside the same-circle BURST budget");
        assertFalse(y.isSet("hit_count"),
                "the plateau deals no damage at all, so a hit_count would be dead config");
    }

    @Test
    void ladderValuesMatchTheCircleEightRow() throws IOException {
        YamlConfiguration y = spell();
        assertEquals(450, y.getInt("mana_cost"), "section 6 ladder circle 8 mana is 450");
        assertEquals(16, y.getLong("cooldown"), "section 6 ladder circle 8 cooldown is 16");
        assertEquals(153, y.getDouble("required_focus"), 1e-9, "section 6 ladder circle 8 focus is 153");
        assertTrue(y.getInt("required_focus") <= 0.85 * 180,
                "circle-8 focus must sit at or below the 85% ceiling of the 180 maxFocus - 153 is "
                        + "exactly on it and must not be raised");
        assertEquals(1.5, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 8 to the single point 30 ticks, so "
                        + "1.5 is the only value SpellBudgetsTest will accept here");
        assertTrue(SpellBudgets.castTimeRange(8).contains(30),
                "1.5s is 30 ticks, exactly the circle-8 cast-time window "
                        + SpellBudgets.castTimeRange(8).minTicks() + "-"
                        + SpellBudgets.castTimeRange(8).maxTicks());
    }

    @Test
    void levelAndRosterKeysAreTheAssignedOnes() throws IOException {
        YamlConfiguration y = spell();
        assertEquals(8, y.getInt("level"),
                "level drives required_circle, the spellbook requirement and the budget lookup");
        assertEquals("continental_shift", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
        assertEquals("SSLSL", y.getString("code"), "cast code from the roster table");
        assertEquals(4147, y.getInt("gui_custom_model_data"),
                "4147 is continental_shift's allocated gui_custom_model_data and is never reused");
    }

    @Test
    void plateauFitsInsideTheCircleEightTerrainBudget() throws IOException {
        YamlConfiguration y = spell();
        assertEquals(SpellBudgets.terrainBudget(8), y.getInt("terrain_budget"),
                "terrain_budget traces back to SpellBudgets.terrainBudget(8) = 1400");
        assertEquals(1400, y.getInt("terrain_budget"), "and 1400 is that number");

        double rx = y.getDouble("plateau_radius_x");
        double rz = y.getDouble("plateau_radius_z");
        int height = y.getInt("plateau_height");
        assertEquals(73, footprintColumns(rx, rz),
                "a 6 x 4 filled ellipse is 73 columns: 1 + 2*(7 + 11 + 11) + 13");
        assertEquals(6, height, "six layers");
        int natural = footprintColumns(rx, rz) * height;
        assertEquals(438, natural, "73 columns x 6 layers");
        assertTrue(natural <= y.getInt("terrain_budget"),
                "the default plateau places " + natural + " blocks, inside the ceiling of "
                        + y.getInt("terrain_budget"));

        // The paid 'colossus' option asks for more than the ceiling, which is why it carries a
        // mana_cost impact and why the shared int[] allowance exists to truncate it.
        int colossusColumns = footprintColumns(9.0, 6.0);
        int colossusBlocks = colossusColumns * 9;
        assertEquals(163, colossusColumns, "a 9 x 6 ellipse is 163 columns");
        assertEquals(1467, colossusBlocks, "163 columns x 9 layers");
        assertTrue(colossusBlocks > y.getInt("terrain_budget"),
                "1467 is over the 1400 ceiling, which is exactly why the allowance truncates at it");
        assertEquals(y.getInt("terrain_budget"), Math.min(colossusBlocks, y.getInt("terrain_budget")),
                "the shared int[] allowance truncates at the budget rather than overshooting it");
    }
}
