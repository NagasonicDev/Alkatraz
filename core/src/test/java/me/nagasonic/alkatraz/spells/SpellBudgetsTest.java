package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.spells.util.SpellBudgets.CastTimeRange;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellBudgetsTest {

    /**
     * Every shipped spell yml under {@code /spells}, excluding the {@code _options}
     * companions. Listed explicitly so a new spell cannot silently escape the
     * cast-time guard by never being named here.
     *
     * <p>The 29 circle 1-9 spells added by the 2026-10-03 expansion are named too: they
     * are in {@code /spells} and the guard is only meaningful while it covers them.
     * {@code dispel} is absent on purpose - it is wave 9's T31 and its yml does not
     * exist yet.
     */
    private static final String[] SHIPPED_SPELL_FILES = {
            "air_blades", "air_burst", "apotheosis", "arcane_resonance", "arcane_ward", "barrier",
            "blink", "buff", "choir_of_light", "continental_shift", "crown_of_dawn", "cyclone_nest", "dark_tendrils",
            "debuff", "detect", "disguise", "echo_cast", "earthen_wall", "earthsplitter",
            "earth_spike", "earth_throw", "everdrain", "fault_line", "fireball", "fire_blast",
            "fire_wall", "flame_lance", "flaming_volley", "geyser", "heal", "inferno_pilgrimage",
            "inferno_rite", "lesser_heal", "light_bolt", "light_buff", "magic_missile",
            "meteor_shower", "oblivion", "radiance", "radiant_brand", "shadow_realm",
            "skeletal_knights", "skywrath", "stealth", "stormcaller", "summon_zombies",
            "sunfire_barrage", "swift", "tectonic_strike", "thousand_waves", "thunderclap",
            "tide_of_the_deep", "tornado", "tremor", "tsunami", "umbral_volley", "undertow",
            "updraft", "void_anchor", "warp", "water_pulse", "water_sphere", "waystone_recall",
            "whirlpool", "wind_barrier", "wind_vortex", "worldbreaker"
    };

    @Test
    void burstBudgetMatchesTheSpecLadder() {
        double[] expected = {1.5, 3.9, 6.1, 9.0, 13.9, 22.1, 34.1, 48.0, 64.1, 90.0};
        for (int circle = 0; circle <= 9; circle++) {
            assertEquals(expected[circle], SpellBudgets.burstBudget(circle), 1e-9,
                    "circle " + circle + " burst budget");
        }
    }

    @Test
    void outOfRangeCirclesClamp() {
        assertEquals(SpellBudgets.burstBudget(0), SpellBudgets.burstBudget(-5), 1e-9,
                "a negative circle clamps to circle 0");
        assertEquals(SpellBudgets.burstBudget(9), SpellBudgets.burstBudget(99), 1e-9,
                "a circle above 9 clamps to circle 9");
    }

    @Test
    void budgetsAreMonotonic() {
        for (int circle = 1; circle <= 9; circle++) {
            assertTrue(SpellBudgets.burstBudget(circle) > SpellBudgets.burstBudget(circle - 1),
                    "burst budget must rise at circle " + circle);
        }
    }

    @Test
    void statPointsAndPowerMatchTheSpecLadder() {
        int[] points = {0, 5, 10, 17, 27, 37, 52, 67, 87, 112};
        double[] power = {1.5, 3.0, 4.2, 5.5, 7.5, 10.8, 14.8, 18.9, 22.7, 28.6};
        for (int circle = 0; circle <= 9; circle++) {
            assertEquals(points[circle], SpellBudgets.statPointsFor(circle),
                    "circle " + circle + " cumulative stat points");
            assertEquals(power[circle], SpellBudgets.powerFor(circle), 1e-9,
                    "circle " + circle + " power");
        }
    }

    @Test
    void affinityAndMultiplierMatchTheSpecLadder() {
        double[] affinity = {0, 28, 45, 63, 85, 105, 130, 154, 182, 215};
        double[] multiplier = {1.00, 1.28, 1.45, 1.63, 1.85, 2.05, 2.30, 2.54, 2.82, 3.15};
        for (int circle = 0; circle <= 9; circle++) {
            assertEquals(affinity[circle], SpellBudgets.affinityFor(circle), 1e-9,
                    "circle " + circle + " affinity");
            assertEquals(multiplier[circle], SpellBudgets.affinityMultiplierFor(circle), 0.005,
                    "circle " + circle + " affinity multiplier");
        }
    }

    @Test
    void manaCooldownAndFocusMatchTheSpecLadder() {
        int[] mana = {5, 15, 24, 48, 75, 120, 165, 240, 450, 600};
        int[] cooldown = {2, 4, 5, 6, 7, 9, 11, 13, 16, 20};
        int[] focus = {0, 20, 40, 65, 90, 115, 135, 144, 153, 161};
        for (int circle = 0; circle <= 9; circle++) {
            assertEquals(mana[circle], SpellBudgets.manaFor(circle),
                    "circle " + circle + " mana cost");
            assertEquals(cooldown[circle], SpellBudgets.cooldownFor(circle),
                    "circle " + circle + " cooldown");
            assertEquals(focus[circle], SpellBudgets.focusFor(circle),
                    "circle " + circle + " required focus");
        }
    }

    @Test
    void castTimeRangeMatchesThePerCircleTable() {
        int[] minTicks = {5, 5, 5, 5, 5, 5, 20, 25, 30, 40};
        int[] maxTicks = {5, 15, 30, 50, 50, 40, 20, 25, 30, 40};
        for (int circle = 0; circle <= 9; circle++) {
            CastTimeRange range = SpellBudgets.castTimeRange(circle);
            assertNotNull(range, "circle " + circle + " has a cast time range");
            assertEquals(minTicks[circle], range.minTicks(),
                    "circle " + circle + " minimum cast ticks");
            assertEquals(maxTicks[circle], range.maxTicks(),
                    "circle " + circle + " maximum cast ticks");
            assertEquals(minTicks[circle] / 20.0, range.minSeconds(), 1e-9,
                    "circle " + circle + " minimum cast seconds");
            assertEquals(maxTicks[circle] / 20.0, range.maxSeconds(), 1e-9,
                    "circle " + circle + " maximum cast seconds");
        }
    }

    @Test
    void castTimeFloorIsFiveTicksAtEveryCircleBelowSix() {
        for (int circle = 0; circle <= 5; circle++) {
            assertEquals(5, SpellBudgets.castTimeRange(circle).minTicks(),
                    "circle " + circle + " keeps instant casts legal");
        }
    }

    @Test
    void castTimeCeilingRisesFromCircleOneToThreeThenEasesAtFive() {
        assertTrue(SpellBudgets.castTimeRange(1).maxTicks() < SpellBudgets.castTimeRange(2).maxTicks(),
                "circle 2 may be committed to longer than circle 1");
        assertTrue(SpellBudgets.castTimeRange(2).maxTicks() < SpellBudgets.castTimeRange(3).maxTicks(),
                "circle 3 may be committed to longer than circle 2");
        assertEquals(SpellBudgets.castTimeRange(3).maxTicks(), SpellBudgets.castTimeRange(4).maxTicks(),
                "circles 3 and 4 share the same ceiling");
        assertTrue(SpellBudgets.castTimeRange(5).maxTicks() < SpellBudgets.castTimeRange(4).maxTicks(),
                "circle 5 tightens back down");
    }

    @Test
    void circlesSixThroughNineCollapseToOneFixedValue() {
        for (int circle = 6; circle <= 9; circle++) {
            CastTimeRange range = SpellBudgets.castTimeRange(circle);
            assertEquals(range.maxTicks(), range.minTicks(),
                    "circle " + circle + " has a fixed cast time, not a range");
        }
    }

    @Test
    void castTimeRangeContainsIsInclusiveAtBothEnds() {
        for (int circle = 0; circle <= 9; circle++) {
            CastTimeRange range = SpellBudgets.castTimeRange(circle);
            assertTrue(range.contains(range.minTicks()),
                    "circle " + circle + " contains its own minimum");
            assertTrue(range.contains(range.maxTicks()),
                    "circle " + circle + " contains its own maximum");
            assertFalse(range.contains(range.minTicks() - 1),
                    "circle " + circle + " excludes one tick below its minimum");
            assertFalse(range.contains(range.maxTicks() + 1),
                    "circle " + circle + " excludes one tick above its maximum");
        }
    }

    @Test
    void castTimeRangeClampsOutOfRangeCircles() {
        assertEquals(SpellBudgets.castTimeRange(0), SpellBudgets.castTimeRange(-1),
                "a negative circle clamps to circle 0");
        assertEquals(SpellBudgets.castTimeRange(9), SpellBudgets.castTimeRange(10),
                "a circle above 9 clamps to circle 9");
    }

    @Test
    void deprecatedCastTimeForReturnsTheCeilingSeconds() {
        for (int circle = 0; circle <= 9; circle++) {
            assertEquals(SpellBudgets.castTimeRange(circle).maxSeconds(),
                    SpellBudgets.castTimeFor(circle), 1e-9,
                    "circle " + circle + " deprecated cast time delegates to the range ceiling");
        }
    }

    @Test
    void windUpTicksIsTheFixedValueFromCircleSix() {
        int[] expected = {20, 25, 30, 40};
        for (int circle = 6; circle <= 9; circle++) {
            assertEquals(expected[circle - 6], SpellBudgets.windUpTicks(circle),
                    "circle " + circle + " wind-up ticks");
            assertEquals(SpellBudgets.castTimeRange(circle).maxTicks(),
                    SpellBudgets.windUpTicks(circle),
                    "circle " + circle + " wind-up is the range ceiling");
        }
    }

    @Test
    void noWindUpContractAppliesBelowCircleSix() {
        for (int circle = 0; circle <= 5; circle++) {
            for (int ticks = 0; ticks <= 200; ticks++) {
                assertFalse(SpellBudgets.isPaidOverWindUp(circle, ticks),
                        "circle " + circle + " has no wind-up contract, so " + ticks
                                + " ticks can never be paid over");
            }
        }
    }

    @Test
    void windUpIsPaidOverOnlyBeyondDoubleTheStandard() {
        for (int circle = 6; circle <= 9; circle++) {
            int standard = SpellBudgets.windUpTicks(circle);
            assertFalse(SpellBudgets.isPaidOverWindUp(circle, standard),
                    "circle " + circle + " at its own standard wind-up is not paid over");
            assertFalse(SpellBudgets.isPaidOverWindUp(circle, 2 * standard),
                    "circle " + circle + " at exactly twice its standard wind-up is not paid over");
            assertTrue(SpellBudgets.isPaidOverWindUp(circle, 2 * standard + 1),
                    "circle " + circle + " one tick past twice its standard wind-up is paid over");
            assertTrue(SpellBudgets.isPaidOverWindUp(circle, 10 * standard),
                    "circle " + circle + " far past twice its standard wind-up is paid over");
        }
    }

    @Test
    void circleAgnosticWindUpCheckBeatsEveryHighCircleContract() {
        assertFalse(SpellBudgets.isPaidOverWindUp(40),
                "40 ticks is exactly twice the circle 6 standard, not paid over");
        assertTrue(SpellBudgets.isPaidOverWindUp(41),
                "41 ticks beats every high circle's wind-up contract");
    }

    @Test
    void everyShippedSpellCastTimeFallsInsideItsCircleRange() {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (String file : SHIPPED_SPELL_FILES) {
            YamlConfiguration yaml = loadSpell(file);
            int level = yaml.getInt("level");
            double castSeconds = yaml.getDouble("cast_time");
            scanned++;
            int ticks = (int) Math.round(castSeconds * 20.0);
            CastTimeRange range = SpellBudgets.castTimeRange(level);
            if (!range.contains(ticks)) {
                offenders.add(file + " is level " + level + " with cast_time " + castSeconds
                        + "s (" + ticks + " ticks), outside " + range.minTicks() + "-" + range.maxTicks());
            }
        }
        assertEquals(SHIPPED_SPELL_FILES.length, scanned,
                "every shipped spell yml was scanned");
        assertTrue(offenders.isEmpty(),
                "shipped cast_time outside the circle range: " + offenders);
    }

    private static YamlConfiguration loadSpell(String file) {
        try (InputStream in = SpellBudgetsTest.class.getResourceAsStream("/spells/" + file + ".yml")) {
            assertNotNull(in, "shipped spell " + file + ".yml is on the classpath");
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (Exception e) {
            throw new AssertionError("could not read shipped spell " + file + ".yml", e);
        }
    }

    @Test
    void sustainTerrainAndDenialMatchTheSpecLadder() {
        int[] sustain = {0, 0, 0, 14, 22, 36, 54, 76, 102, 144};
        int[] terrain = {0, 0, 0, 120, 220, 380, 620, 950, 1400, 2000};
        int[] denial = {0, 0, 0, 900, 1400, 2100, 3000, 4200, 5600, 7500};
        for (int circle = 0; circle <= 9; circle++) {
            assertEquals(sustain[circle], SpellBudgets.sustainBudget(circle), 1e-9,
                    "circle " + circle + " sustain budget");
            assertEquals(terrain[circle], SpellBudgets.terrainBudget(circle),
                    "circle " + circle + " terrain budget");
            assertEquals(denial[circle], SpellBudgets.denialBudget(circle),
                    "circle " + circle + " denial budget");
        }
    }

    @Test
    void circlesBelowThreeHaveNoNonBurstBudget() {
        for (int circle = 0; circle <= 2; circle++) {
            assertEquals(0.0, SpellBudgets.sustainBudget(circle), 1e-9,
                    "circle " + circle + " has no sustain budget");
            assertEquals(0, SpellBudgets.terrainBudget(circle),
                    "circle " + circle + " has no terrain budget");
            assertEquals(0, SpellBudgets.denialBudget(circle),
                    "circle " + circle + " has no denial budget");
        }
    }

    @Test
    void focusStaysUnderTheEightyFivePercentCeilingFromCircleSix() {
        int[] maxFocus = {0, 110, 120, 130, 140, 150, 160, 170, 180, 190};
        for (int circle = 6; circle <= 9; circle++) {
            assertTrue(SpellBudgets.focusFor(circle) <= 0.85 * maxFocus[circle],
                    "circle " + circle + " requires " + SpellBudgets.focusFor(circle)
                            + " focus, over the 0.85 x " + maxFocus[circle] + " ceiling");
        }
    }

    @Test
    void fireCannotSustain() {
        assertFalse(SpellBudgets.isAllowed(Element.FIRE, SpellRole.SUSTAIN),
                "FIRE is forbidden from SUSTAIN - fire has no ticking damage");
        assertTrue(SpellBudgets.isAllowed(Element.FIRE, SpellRole.BURST), "fire owns burst");
        assertTrue(SpellBudgets.isAllowed(Element.FIRE, SpellRole.TERRAIN), "fire owns terrain");
    }

    @Test
    void waterAirAndLightCannotReshapeLand() {
        assertFalse(SpellBudgets.isAllowed(Element.WATER, SpellRole.TERRAIN), "water never reshapes land");
        assertFalse(SpellBudgets.isAllowed(Element.AIR, SpellRole.TERRAIN), "air never reshapes land");
        assertFalse(SpellBudgets.isAllowed(Element.LIGHT, SpellRole.TERRAIN), "light never reshapes land");
    }

    @Test
    void earthHasNoConvenienceSpells() {
        assertFalse(SpellBudgets.isAllowed(Element.EARTH, SpellRole.UTILITY),
                "EARTH is forbidden from UTILITY - earth has no convenience spells");
    }

    @Test
    void darkHasNoDefensiveOption() {
        assertFalse(SpellBudgets.isAllowed(Element.DARK, SpellRole.DENIAL),
                "DARK is forbidden from DENIAL - its power is taken, never given");
        assertTrue(SpellBudgets.isAllowed(Element.DARK, SpellRole.UTILITY),
                "summon utility IS owned by DARK; the table allows it");
    }

    @Test
    void noneCannotDealDamage() {
        assertFalse(SpellBudgets.isAllowed(Element.NONE, SpellRole.BURST), "NONE is forbidden from BURST");
        assertFalse(SpellBudgets.isAllowed(Element.NONE, SpellRole.SUSTAIN), "NONE is forbidden from SUSTAIN");
        assertTrue(SpellBudgets.isGrandfatheredBurst("magic_missile", Element.NONE, SpellRole.BURST),
                "magic_missile is the sole grandfathered NONE burst");
        assertFalse(SpellBudgets.isGrandfatheredBurst("warp", Element.NONE, SpellRole.BURST),
                "no other NONE spell is grandfathered");
    }

    @Test
    void everyElementHasAnAllowlistAndNoneForNull() {
        for (Element element : Element.values()) {
            assertFalse(SpellBudgets.allowedRoles(element).isEmpty(),
                    element + " must own at least one role");
        }
        assertTrue(SpellBudgets.allowedRoles(null).isEmpty(),
                "a null element has no allowed roles");
        assertFalse(SpellBudgets.isAllowed(null, SpellRole.BURST),
                "a null element allows nothing");
    }

    @Test
    void roleParsingRejectsTypos() {
        assertEquals(SpellRole.BURST, SpellRole.parse("BURST"), "uppercase parses");
        assertEquals(SpellRole.BURST, SpellRole.parse("  burst "), "whitespace and case are tolerated");
        assertNull(SpellRole.parse("SUSTAN"), "a typo must not coerce to a valid role");
        assertNull(SpellRole.parse(""), "an empty value is not a role");
        assertNull(SpellRole.parse(null), "a null value is not a role");
    }
}
