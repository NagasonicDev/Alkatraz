package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YML-only assertions, so no Bukkit mock is needed. getResourceAsStream plus
 * YamlConfiguration, following AlkatrazConfigPathsTest and FlameLanceTest.
 *
 * <p>The record's own defaults and the whole scaling model are asserted from here
 * rather than from a second test class, because the wave-5 gate counts exactly five
 * methods per spell test class. It is possible at all only because every field of
 * {@link OblivionConfig} is a primitive.
 */
class OblivionTest {

    private static final String ID = "oblivion";
    private static final double EPS = 1e-9;

    private static YamlConfiguration yml() throws Exception {
        try (InputStream in = OblivionTest.class.getResourceAsStream("/spells/" + ID + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + ID + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsParseableAndAllowedForDark() throws Exception {
        YamlConfiguration y = yml();
        Element element = Element.valueOf(y.getString("element"));
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, ID + " must declare a parseable role: key");
        assertEquals(Element.DARK, element, ID + " is a dark spell");
        assertEquals(SpellRole.BURST, role, ID + " is the circle-9 burst capstone");
        assertTrue(SpellBudgets.isAllowed(element, role),
                ID + " declares " + role + " but DARK allows only {UTILITY, SUSTAIN, BURST}");
        assertFalse(role == SpellRole.DENIAL, "dark has no defensive option at any circle");
    }

    @Test
    void theScaledWorstCaseStaysInsideTheCircleNineBurstBudget() throws Exception {
        YamlConfiguration y = yml();
        int circle = y.getInt("level");
        assertTrue(y.isSet("missing_health_multiplier") && y.isSet("burst_damage_cap"),
                ID + " scales with missing health, so both keys must exist");
        assertEquals(90.0, y.getDouble("burst_damage_cap"), EPS,
                "the cap is the circle-9 BURST budget");
        assertEquals(SpellBudgets.burstBudget(9), y.getDouble("burst_damage_cap"), EPS,
                "and it traces back to the ladder rather than being invented");
        double worst = Math.min(y.getDouble("power") * y.getDouble("missing_health_multiplier"),
                y.getDouble("burst_damage_cap"));
        assertEquals(90.0, worst, EPS, "28.6 x 3.15 = 90.09, truncated to the 90.0 cap");
        assertTrue(worst <= SpellBudgets.burstBudget(circle),
                ID + " deals " + worst + " per cast, over the circle-" + circle
                        + " BURST budget of " + SpellBudgets.burstBudget(circle));
        assertFalse(y.isSet("hit_count"),
                "oblivion's damage is scaled, not counted - a hit_count key would be dead config");
    }

    @Test
    void ladderValuesMatchTheCircleNineRow() throws Exception {
        YamlConfiguration y = yml();
        assertEquals(600, y.getInt("mana_cost"), "section 6 ladder circle-9 mana");
        assertEquals(20, y.getInt("cooldown"), "section 6 ladder circle-9 cooldown");
        assertEquals(161, y.getInt("required_focus"), "section 6 ladder circle-9 focus");
        assertTrue(y.getInt("required_focus") <= 0.85 * 190,
                "circle-9 focus must sit at or below the 85% ceiling of the 190 maxFocus - 161 is "
                        + "under the 161.5 ceiling and must not be raised");
        assertEquals(28.6, y.getDouble("power"), EPS, "section 6 ladder circle-9 power, verbatim");
        assertEquals(3.15, y.getDouble("missing_health_multiplier"), EPS,
                "the multiplier comes from the section 9 deviations table, not from the ladder");
        assertEquals(2.0, y.getDouble("cast_time"), EPS,
                "SpellBudgets.castTimeRange(9) collapses to the single point 40 ticks = 2.0s, so 2.0 "
                        + "is the only legal circle-9 cast time");
        assertTrue(SpellBudgets.castTimeRange(9).contains(40),
                "2.0s is 40 ticks, exactly the circle-9 cast-time window");
    }

    @Test
    void levelIsNine() throws Exception {
        YamlConfiguration y = yml();
        assertEquals(9, y.getInt("level"), ID + " is the circle-9 capstone");
        assertEquals("RSLRL", y.getString("code"), "cast code from the roster table");
        assertEquals(4161, y.getInt("gui_custom_model_data"),
                "4161 is oblivion's allocated gui_custom_model_data and is never reused");
        assertEquals("oblivion", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
    }

    @Test
    void theScalingModelIsMonotonicAndCappedAtBothEnds() throws Exception {
        YamlConfiguration y = yml();
        OblivionConfig cfg = OblivionConfig.fromConfig(y.getValues(false));
        OblivionConfig defaults = OblivionConfig.defaults();

        assertEquals(28.6, cfg.scaledDamage(28.6, 0.0), EPS, "a full-health target takes base power");
        assertEquals(59.345, cfg.scaledDamage(28.6, 0.5), 1e-6,
                "1 + 0.5 x 2.15 = 2.075, and 28.6 x 2.075 = 59.345");
        assertEquals(90.0, cfg.scaledDamage(28.6, 1.0), EPS, "a 1-health target takes exactly the cap");
        assertEquals(90.0, cfg.worstCaseDamage(28.6), EPS, "worst case is the cap");
        assertEquals(28.6, cfg.minimumDamage(28.6), EPS, "minimum case is base power");
        assertTrue(cfg.scaledDamage(28.6, 0.25) < cfg.scaledDamage(28.6, 0.75),
                "more missing health must always be worth more damage");
        assertTrue(cfg.scaledDamage(28.6, -1.0) == cfg.scaledDamage(28.6, 0.0),
                "a negative missing fraction clamps to zero rather than healing the target");
        assertEquals(cfg.scaledDamage(28.6, 0.6), defaults.scaledDamage(28.6, 0.6), EPS,
                "the record's own defaults must not drift from the yml");
        assertEquals(90.0, defaults.burstDamageCap(), EPS, "the default cap is the circle-9 budget");
        assertEquals(3.15, defaults.missingHealthMultiplier(), EPS, "the default multiplier");
        assertEquals(240, cfg.debuffTicks(),
                "12 seconds is 240 ticks, and nothing in the class calls removePotionEffect - that "
                        + "absence is the whole of dark's Curse pillar for this spell");
        assertEquals(0.1, cfg.erasureRadiusAt(0), EPS,
                "the wave opens at the 0.1 floor rather than at zero radius, so tick 0 draws something");
        assertEquals(1.5, cfg.erasureRadiusAt(15), 1e-6,
                "halfway through a 30-tick wave the shell is halfway to its 3.0 radius");
        assertFalse(y.isSet("damage_per_tick"),
                ID + " makes exactly one damage application, so a per-tick damage key would be a lie");
        assertFalse(y.getStringList("description").isEmpty(), "description must be a non-empty list");
    }
}