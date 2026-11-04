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
 *
 * <p>At the shipped defaults the natural layout WANTS 3696 blocks and only gets 3600, so the
 * arithmetic that guards the cap is the inclusive footprint minus the caster's own column - not
 * {@code crush_width x rift_length}. These tests therefore assert the counter's behaviour: the
 * wish exceeds the cap, and the counter truncates to exactly the cap.
 */
class WorldbreakerTest {

    private static YamlConfiguration spell() throws IOException {
        try (InputStream in = WorldbreakerTest.class.getResourceAsStream("/spells/worldbreaker.yml")) {
            assertNotNull(in, "bundled resource /spells/worldbreaker.yml is missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static YamlConfiguration options() throws IOException {
        try (InputStream in = WorldbreakerTest.class.getResourceAsStream("/spells/worldbreaker_options.yml")) {
            assertNotNull(in, "bundled resource /spells/worldbreaker_options.yml is missing");
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

    /** One slab layer, mirroring Worldbreaker.slabFootprint: both axes INCLUSIVE, minus the
     *  single column the caster is standing in (layer 1 would otherwise seal them in). */
    private static int slabLayer(int riftLength, YamlConfiguration y) {
        int halfLength = Math.max(1, riftLength / 2);
        int halfWidth = Math.max(0, y.getInt("crush_width") / 2);
        return (2 * halfLength + 1) * (2 * halfWidth + 1) - 1;
    }

    /** Every block the layout would touch with no cap at all: both faults, the slab, both chasms. */
    private static int wanted(int riftLength, int crushLift, YamlConfiguration y) {
        int digTicks = (int) Math.floor(riftLength / (double) y.getInt("rift_step"));
        int dug = 2 * digTicks * sphereBlockCount(y.getDouble("rift_radius"));
        int raised = slabLayer(riftLength, y) * crushLift;
        int chasms = 2 * sphereBlockCount(y.getDouble("chasm_radius"));
        return dug + raised + chasms;
    }

    /** What is LEFT in the one shared int[] after the cast, in Worldbreaker's own spend order:
     *  faults, then slab layers one at a time, then one chasm per rift - each clamped to the
     *  remainder, exactly as {@code Math.min(blocksLeft[0], n)} clamps at every call site. */
    private static int remainingAfterCast(int budget, int riftLength, int crushLift, YamlConfiguration y) {
        int digTicks = (int) Math.floor(riftLength / (double) y.getInt("rift_step"));
        // shortcut: the per-site clamp is skipped here because no shipped option digs more than
        // the cap on its own (the largest is world_end at 2508). Model it if that ever changes.
        int left = budget - 2 * digTicks * sphereBlockCount(y.getDouble("rift_radius"));
        int perLayer = slabLayer(riftLength, y);
        for (int layer = 1; layer <= crushLift && left > 0; layer++) left -= Math.min(left, perLayer);
        int perChasm = sphereBlockCount(y.getDouble("chasm_radius"));
        for (int rift = 0; rift < 2 && left > 0; rift++) left -= Math.min(left, perChasm);
        return left;
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
        assertEquals(57.2, y.getDouble("power"), 1e-9,
                "power is the section 6 circle-9 ladder value, verbatim");
        assertFalse(y.isSet("hit_count"),
                "the slab crushes each target on three separate ticks, so hit_count is absent "
                        + "and defaults to 1");
        assertEquals(3, y.getInt("crush_events"), "lift, fall, slab");
        assertTrue(y.getDouble("power") * y.getInt("crush_events") <= SpellBudgets.burstBudget(y.getInt("level")),
                "all three crush events together must sit inside the same-circle BURST budget");
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
    void terrainBudgetIsTheCircleNineCeiling() throws IOException {
        YamlConfiguration y = spell();
        assertEquals(SpellBudgets.terrainBudget(9), y.getInt("terrain_budget"));
        assertEquals(3600, y.getInt("terrain_budget"), "and 3600 is the new circle-9 ceiling");
    }

    @Test
    void theSharedAllowanceTruncatesTheNaturalLayoutToExactlyTheCap() throws IOException {
        YamlConfiguration y = spell();
        int cap = SpellBudgets.terrainBudget(y.getInt("level"));
        int length = y.getInt("rift_length");
        int lift = y.getInt("crush_lift");

        int digTicks = (int) Math.floor(length / (double) y.getInt("rift_step"));
        int dug = 2 * digTicks * sphereBlockCount(y.getDouble("rift_radius"));
        int raised = slabLayer(length, y) * lift;
        int chasms = 2 * sphereBlockCount(y.getDouble("chasm_radius"));

        // The four figures every comment in worldbreaker.yml and its options file quotes.
        assertEquals(19, digTicks, "digTicks floors rift_length / rift_step, so 38 / 2 = 19");
        assertEquals(57, sphereBlockCount(y.getDouble("rift_radius")), "a radius-2.4 sphere is 57");
        assertEquals(123, sphereBlockCount(y.getDouble("chasm_radius")), "a radius-3.0 sphere is 123");
        assertEquals(350, slabLayer(length, y),
                "39 x 11 with both axes inclusive, less the one column the caster stands in - NOT "
                        + "crush_width x rift_length, which is 380");
        assertEquals(1050, raised, "350 per layer x 3 layers");

        assertEquals(3462, dug + raised + chasms,
                "the natural layout at the shipped defaults wants 3462 of a 3600 cap");
        assertEquals(3462, cap - remainingAfterCast(cap, length, lift, y),
                "the one shared int[] spends 3462 of the 3600 ceiling");
        assertEquals(138, remainingAfterCast(cap, length, lift, y),
                "the default cast leaves 138 blocks of headroom");
    }

    @Test
    void bothWideningOptionsAskForMoreThanTheCapSoWideningTheSlabFailsCi() throws IOException {
        YamlConfiguration y = spell();
        YamlConfiguration o = options();
        int cap = y.getInt("terrain_budget");

        int longRift = o.getInt("options.rift_length.values.world_end.value");
        assertEquals(44, longRift, "world_end is the rift_length option that pays extra mana");
        assertTrue(wanted(longRift, y.getInt("crush_lift"), y) > cap,
                "rift_length " + longRift + " wants " + wanted(longRift, y.getInt("crush_lift"), y)
                        + ", over the cap, so it is clipped - not silently affordable");

        int tallLift = o.getInt("options.crush_lift.values.upthrust.value");
        assertEquals(5, tallLift, "upthrust is the crush_lift option that pays extra mana");
        assertTrue(wanted(y.getInt("rift_length"), tallLift, y) > cap,
                "crush_lift " + tallLift + " wants " + wanted(y.getInt("rift_length"), tallLift, y)
                        + ", over the cap, so it is clipped - not silently affordable");

        assertEquals(0, remainingAfterCast(cap, longRift, y.getInt("crush_lift"), y),
                "the widest rift still ends on the ceiling");
        assertEquals(0, remainingAfterCast(cap, y.getInt("rift_length"), tallLift, y),
                "the tallest slab still ends on the ceiling");
    }
}
