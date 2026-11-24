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
     * <p>This is the full 0.9.2 roster: the 33 circle 0-4 survivors plus the 35
     * additions, 68 in all. It is only ever extended, never shrunk, so the guard
     * cannot be loosened by deleting a name.
     */
    private static final String[] SHIPPED_SPELL_FILES = {
            "air_blades", "air_burst", "atlas_unbound", "barrier", "beacon_of_accord",
            "blink", "buff", "chronicle_anchor", "covenant_of_the_first_dawn", "dark_tendrils",
            "dawn_chorus", "debuff", "detect", "disguise", "dispel",
            "dragons_wake", "earth_spike", "earth_throw", "earthen_wall", "emberforge",
            "empyrean_passage", "eye_of_the_firmament", "fire_blast", "fire_wall", "fireball",
            "flaming_volley", "foldstep", "geyser", "heal", "hollow_kings_court",
            "lesser_heal", "leviathans_passage", "leviathans_testament", "light_bolt", "light_buff",
            "magic_missile", "phoenix_reprieve", "quarryheart", "riptide_convoy", "runic_survey",
            "sanctuary_bloom", "skyglass_glide", "skyhook", "soul_lantern", "soul_reclamation",
            "starfall_cataclysm", "starfall_crucible", "stealth", "stone_bastion", "stonebridge",
            "summon_zombies", "swift", "tempest_road", "tidal_reservoir", "titans_causeway",
            "tremor", "undertow_veil", "updraft", "veil_of_the_unseen_court", "water_pulse",
            "water_sphere", "wayfarers_call", "whirlpool", "wind_barrier", "wind_vortex",
            "world_gate", "worldtree_beacon", "wraithbound_retinue"
    };

    @Test
    void burstBudgetMatchesTheSpecLadder() {
        double[] expected = {1.5, 3.5, 6.0, 9.0, 13.9, 22.1, 34.1, 48.0, 64.1, 180.0};
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
        double[] power = {1.5, 3.0, 4.2, 5.5, 7.5, 10.8, 14.8, 18.9, 22.7, 57.2};
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
        int[] cooldown = {2, 4, 5, 6, 7, 30, 45, 60, 90, 165};
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
        int[] minTicks = {5, 5, 5, 5, 5, 25, 30, 45, 50, 70};
        int[] maxTicks = {5, 15, 30, 50, 50, 40, 45, 50, 60, 70};
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
    void castTimeFloorIsFiveTicksThroughCircleFour() {
        for (int circle = 0; circle <= 4; circle++) {
            assertEquals(5, SpellBudgets.castTimeRange(circle).minTicks(),
                    "circle " + circle + " keeps instant casts legal");
        }
        assertTrue(SpellBudgets.castTimeRange(5).minTicks() > 5,
                "from circle 5 the floor climbs with the ceiling, so instant is no longer legal");
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
    void onlyCircleNineIsAFixedCastTime() {
        CastTimeRange nine = SpellBudgets.castTimeRange(9);
        assertEquals(nine.maxTicks(), nine.minTicks(),
                "circle 9 is the fixed 70-tick capstone cast whose duration is its wind-up");
        for (int circle = 6; circle <= 8; circle++) {
            CastTimeRange range = SpellBudgets.castTimeRange(circle);
            assertTrue(range.minTicks() < range.maxTicks(),
                    "circle " + circle + " is a window, not a fixed value");
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
    void windUpTicksIsTheRangeCeilingFromCircleSix() {
        int[] expected = {45, 50, 60, 70};
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
        assertFalse(SpellBudgets.isPaidOverWindUp(90),
                "90 ticks is exactly twice the circle 6 standard of 45, not paid over");
        assertTrue(SpellBudgets.isPaidOverWindUp(91),
                "91 ticks beats every high circle's wind-up contract");
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
        int[] sustain = {0, 0, 0, 14, 22, 36, 54, 76, 102, 280};
        int[] terrain = {0, 0, 0, 120, 220, 380, 620, 950, 1400, 3600};
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
    void waterOwnsTerrainButAirAndLightDoNot() {
        assertTrue(SpellBudgets.isAllowed(Element.WATER, SpellRole.TERRAIN),
                "water reshapes land through its tide spells");
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
        assertFalse(SpellBudgets.isGrandfatheredBurst("blink", Element.NONE, SpellRole.BURST),
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
