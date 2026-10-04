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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YML-only assertions, so no Bukkit mock is needed: getResourceAsStream plus
 * YamlConfiguration, following {@code mobs/AlkatrazConfigPathsTest}.
 */
class TideOfTheDeepTest {

    private static YamlConfiguration yml() throws IOException {
        try (InputStream in = TideOfTheDeepTest.class.getResourceAsStream("/spells/tide_of_the_deep.yml")) {
            assertNotNull(in, "missing bundled resource /spells/tide_of_the_deep.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsParseableAndAllowedForWater() throws IOException {
        YamlConfiguration y = yml();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "tide_of_the_deep must declare a parseable role: key or SpellBudgetTest cannot judge it");
        assertEquals(SpellRole.SUSTAIN, role,
                "the tide keeps acting for ten seconds after the cast resolves - that is SUSTAIN");
        assertTrue(SpellBudgets.isAllowed(Element.WATER, role),
                "WATER owns SUSTAIN, DENIAL and BURST, so SUSTAIN must be allowed");
    }

    @Test
    void powerTimesHitCountLandsExactlyOnTheCircleNineSustainBudget() throws IOException {
        YamlConfiguration y = yml();
        double declared = y.getDouble("power") * y.getInt("hit_count");

        assertEquals(14.4, y.getDouble("power"), 1e-9,
                "documented deviation: 144 / 10 windows, because the ladder's 28.6 gives 286");
        assertEquals(10, y.getInt("hit_count"), "hit_count is the number of 20-tick windows in the tide's life");
        assertEquals(144.0, declared, 1e-9, "14.4 x 10");
        assertEquals(SpellBudgets.sustainBudget(9), declared, 1e-9,
                "the declared total is exactly the circle-9 SUSTAIN budget");
    }

    @Test
    void ladderValuesMatchTheCircleNineTable() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(600, y.getInt("mana_cost"), "spec section 6 ladder, circle 9");
        assertEquals(20, y.getInt("cooldown"), "spec section 6 ladder, circle 9");
        assertEquals(161, y.getInt("required_focus"), "spec section 6 ladder, circle 9");
        // SpellBudgets.castTimeRange collapses circle 9 to the single point 40 ticks, because
        // CAST_MIN_TICKS and CAST_MAX_TICKS both hold 40 at index 9. The withdrawn section 6
        // ladder column said 3.0; 2.0 is the only value the collapsed window still admits.
        assertEquals(2.0, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 9 to the single point 40 ticks, so 2.0 is "
                        + "the only legal circle-9 cast time");
        assertTrue(SpellBudgets.castTimeRange(9).contains((int) Math.round(y.getDouble("cast_time") * 20.0)),
                "circle 9 allows " + SpellBudgets.castTimeRange(9).minTicks() + ".."
                        + SpellBudgets.castTimeRange(9).maxTicks() + " ticks");

        // The tide declares no channel and no wind_up_duration, so the collapsed cast time is
        // not added to anything: hit_count and the ramp are budgeted against
        // duration_seconds, which is why the cast time moving does not move the 10 windows.
        assertFalse(y.isSet("wind_up_duration") || y.isSet("channel_seconds"),
                "nothing is stacked on top of cast_time, so the ten windows of duration_seconds "
                        + "are unaffected by it");
        assertFalse(SpellBudgets.isPaidOverWindUp(9, (int) Math.round(y.getDouble("cast_time") * 20.0)),
                "a 40-tick circle-9 cast is exactly the wind-up, never over it");
    }

    @Test
    void levelIsCircleNine() throws IOException {
        assertEquals(9, yml().getInt("level"),
                "tide_of_the_deep is a circle-9 spell; Spell.loadCommonConfig reads required_circle from level");
    }

    /**
     * The ramp has to climb for the capstone to read, and the real total it produces has to
     * stay under the declared one - otherwise the yml would be promising a budget the spell
     * never spends, and the escalation would be free damage.
     */
    @Test
    void theRampClimbsAndTheRealTotalStaysUnderTheDeclaredOne() throws IOException {
        YamlConfiguration y = yml();
        long interval = SpellAttackContext.DAMAGE_INTERVAL_TICKS;
        assertEquals(20L, interval, "the shared SUSTAIN cadence constant is one second");

        int windows = (int) Math.round(y.getDouble("duration_seconds") * 20.0 / interval);
        assertEquals(10, windows, "10.0 seconds at one hit per 20-tick window is ten damage windows");
        assertEquals(y.getInt("hit_count"), windows,
                "hit_count must equal the number of damage windows, or SpellBudgetTest under-reports the spell");

        List<Double> ramp = y.getDoubleList("tide_ramp");
        assertEquals(windows, ramp.size(), "one ramp multiplier per damage window");
        double sum = 0.0;
        for (double multiplier : ramp) sum += multiplier;
        assertEquals(9.40, sum, 1e-9,
                "0.40 + 0.52 + 0.64 + 0.76 + 0.88 + 1.00 + 1.12 + 1.24 + 1.36 + 1.48");
        assertEquals(135.36, y.getDouble("power") * sum, 1e-9,
                "the real ramped total for one body standing through the whole tide");
        assertTrue(y.getDouble("power") * sum <= SpellBudgets.sustainBudget(9),
                "the ramped total must stay under the circle-9 SUSTAIN budget of "
                        + SpellBudgets.sustainBudget(9));
        assertTrue(ramp.get(ramp.size() - 1) > ramp.get(0),
                "the tide has to climb, or it is not the capstone ramp the identity depends on");
    }
}