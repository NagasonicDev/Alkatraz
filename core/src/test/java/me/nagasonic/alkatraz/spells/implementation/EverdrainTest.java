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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YML-only assertions, so no Bukkit mock is needed: getResourceAsStream plus
 * YamlConfiguration, following {@code mobs/AlkatrazConfigPathsTest}.
 */
class EverdrainTest {

    private static YamlConfiguration yml() throws IOException {
        try (InputStream in = EverdrainTest.class.getResourceAsStream("/spells/everdrain.yml")) {
            assertNotNull(in, "missing bundled resource /spells/everdrain.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsParseableAndAllowedForWater() throws IOException {
        YamlConfiguration y = yml();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "everdrain must declare a parseable role: key or SpellBudgetTest cannot judge it");
        assertEquals(SpellRole.SUSTAIN, role,
                "the pool keeps acting after the cast resolves - that is SUSTAIN");
        assertTrue(SpellBudgets.isAllowed(Element.WATER, role),
                "WATER owns SUSTAIN, DENIAL and BURST, so SUSTAIN must be allowed");
    }

    @Test
    void powerTimesHitCountStaysInsideTheCircleSevenSustainBudget() throws IOException {
        YamlConfiguration y = yml();
        double total = y.getDouble("power") * y.getInt("hit_count");

        assertEquals(18.9, y.getDouble("power"), 1e-9,
                "spec section 6 ladder, circle 7, verbatim - everdrain deviates on hit_count, not power");
        assertEquals(4, y.getInt("hit_count"), "hit_count is the number of 20-tick windows in the pool's life");
        assertEquals(75.6, total, 1e-9, "18.9 x 4");
        assertTrue(total <= SpellBudgets.sustainBudget(7),
                "everdrain deals " + total + " over its life, against the circle-7 SUSTAIN budget of "
                        + SpellBudgets.sustainBudget(7));
    }

    @Test
    void ladderValuesMatchTheCircleSevenTable() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(240, y.getInt("mana_cost"), "spec section 6 ladder, circle 7");
        assertEquals(13, y.getInt("cooldown"), "spec section 6 ladder, circle 7");
        assertEquals(144, y.getInt("required_focus"), "spec section 6 ladder, circle 7");
        // SpellBudgets.castTimeRange collapses circle 7 to the single point 25 ticks, because
        // CAST_MIN_TICKS and CAST_MAX_TICKS both hold 25 at index 7. The withdrawn section 6
        // ladder column said 2.0; 1.25 is the only value the collapsed window still admits.
        assertEquals(1.25, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 7 to the single point 25 ticks, so 1.25 is "
                        + "the only legal circle-7 cast time");
        assertTrue(SpellBudgets.castTimeRange(7).contains((int) Math.round(y.getDouble("cast_time") * 20.0)),
                "circle 7 allows " + SpellBudgets.castTimeRange(7).minTicks() + ".."
                        + SpellBudgets.castTimeRange(7).maxTicks() + " ticks");
    }

    @Test
    void levelIsCircleSeven() throws IOException {
        assertEquals(7, yml().getInt("level"),
                "everdrain is a circle-7 spell; Spell.loadCommonConfig reads required_circle from level");
    }

    @Test
    void theLifeTotalIsExactlyTheDamageWindowCountAtTheSharedCadence() throws IOException {
        YamlConfiguration y = yml();
        long interval = SpellAttackContext.DAMAGE_INTERVAL_TICKS;
        assertEquals(20L, interval, "the shared SUSTAIN cadence constant is one second");

        int windows = (int) Math.round(y.getDouble("duration_seconds") * 20.0 / interval);
        assertEquals(4, windows, "4.0 seconds at one hit per 20-tick window is four damage windows");
        assertEquals(y.getInt("hit_count"), windows,
                "hit_count must equal the number of damage windows, or SpellBudgetTest under-reports the spell");
        assertEquals(y.getDouble("duration_seconds"), windows, 1e-9,
                "the SUSTAIN cadence rule is hit_count == duration_seconds, one window per second");
        assertTrue(y.getDouble("power") * windows <= SpellBudgets.sustainBudget(7),
                "the total over the pool's life must sit inside the circle-7 SUSTAIN budget");
    }
}