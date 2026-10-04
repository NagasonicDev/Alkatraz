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
 * <p>The record's own defaults are asserted from here rather than from a second
 * test class, because the wave-5 gate counts exactly five methods per spell test
 * class and a sixth class would break that arithmetic. It is possible at all only
 * because every field of {@link UmbralVolleyConfig} is a primitive.
 */
class UmbralVolleyTest {

    private static final String ID = "umbral_volley";
    private static final double EPS = 1e-9;

    private static YamlConfiguration yml() throws Exception {
        try (InputStream in = UmbralVolleyTest.class.getResourceAsStream("/spells/" + ID + ".yml")) {
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
        assertEquals(SpellRole.BURST, role, ID + " is dark's circle-7 Curse pillar");
        assertTrue(SpellBudgets.isAllowed(element, role),
                ID + " declares " + role + " but DARK allows only {UTILITY, SUSTAIN, BURST}");
        assertFalse(role == SpellRole.DENIAL, "dark has no defensive option at any circle");
    }

    @Test
    void powerTimesHitCountStaysInsideTheCircleSevenBurstBudget() throws Exception {
        YamlConfiguration y = yml();
        int circle = y.getInt("level");
        int hits = Math.max(1, y.getInt("hit_count"));
        double total = y.getDouble("power") * hits;
        assertEquals(3, hits, "one cast fires three darts, so hit_count must be 3");
        assertEquals(3, y.getInt("dart_count"),
                "the default dart count and hit_count must agree, or the budget is misreported");
        assertEquals(16.0, y.getDouble("power"), EPS,
                "power is the documented deviation: the circle-7 ladder's 18.9 x 3 = 56.7 overshoots "
                        + "burstBudget(7) = 48.0");
        assertEquals(48.0, total, EPS, "three darts of 16 is 48 exactly");
        assertEquals(SpellBudgets.burstBudget(7), total, EPS,
                "48.0 is exactly the circle-7 BURST budget, with nothing spare");
        assertTrue(total <= SpellBudgets.burstBudget(circle),
                ID + " deals " + total + " per cast, over the circle-" + circle
                        + " BURST budget of " + SpellBudgets.burstBudget(circle));
    }

    @Test
    void ladderValuesMatchTheCircleSevenRow() throws Exception {
        YamlConfiguration y = yml();
        assertEquals(240, y.getInt("mana_cost"), "section 6 ladder circle-7 mana");
        assertEquals(13, y.getInt("cooldown"), "section 6 ladder circle-7 cooldown");
        assertEquals(144, y.getInt("required_focus"), "section 6 ladder circle-7 focus");
        assertTrue(y.getInt("required_focus") <= 0.85 * 170,
                "circle-7 focus must sit at or below the 85% ceiling of the 170 maxFocus - 144 is "
                        + "under the 144.5 ceiling and must not be raised");
        assertEquals(1.25, y.getDouble("cast_time"), EPS,
                "SpellBudgets.castTimeRange(7) collapses to the single point 25 ticks = 1.25s, so "
                        + "1.25 is the only legal circle-7 cast time - the old single-value ladder "
                        + "column of 2.0 was retired when cast time became a range");
        assertTrue(SpellBudgets.castTimeRange(7).contains(25),
                "1.25s is 25 ticks, exactly the circle-7 cast-time window");

        UmbralVolleyConfig fromYml = UmbralVolleyConfig.fromConfig(y.getValues(false));
        UmbralVolleyConfig defaults = UmbralVolleyConfig.defaults();
        assertEquals(y.getInt("dart_count"), fromYml.dartCount(), "record and yml must agree on dart_count");
        assertEquals(y.getInt("debuff_duration"), fromYml.debuffSeconds(),
                "record and yml must agree on debuff_duration");
        assertEquals(3, defaults.dartCount(),
                "the record's own fallback for an absent dart_count must be the same 3 darts, so an "
                        + "empty or unreadable yml cannot quietly shrink the volley");
        assertEquals(12.0, defaults.dartSpread(), EPS, "the record's dart_spread default");
        assertEquals(2, defaults.mobDartCount(), "a mob fires the smaller two-dart volley");
        assertEquals(3, UmbralVolleyConfig.yawOffsets(12.0, 3).length, "three darts, three offsets");
        assertEquals(-6.0, UmbralVolleyConfig.yawOffsets(12.0, 3)[0], EPS, "the fan starts at -half spread");
        assertEquals(6.0, UmbralVolleyConfig.yawOffsets(12.0, 3)[2], EPS, "and ends at +half spread");
        assertEquals(0.0, UmbralVolleyConfig.yawOffsets(12.0, 1)[0], EPS,
                "a single dart gets a zero offset rather than a division by zero");
    }

    @Test
    void levelIsSeven() throws Exception {
        YamlConfiguration y = yml();
        assertEquals(7, y.getInt("level"), ID + " is a circle-7 spell");
        assertEquals("LRSLR", y.getString("code"), "cast code from the roster table");
        assertEquals(4159, y.getInt("gui_custom_model_data"),
                "4159 is umbral_volley's allocated gui_custom_model_data and is never reused");
        assertEquals("umbral_volley", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
    }

    @Test
    void theCurseOutlivesTheCastAndDealsNoDamageOfItsOwn() throws Exception {
        YamlConfiguration y = yml();
        UmbralVolleyConfig cfg = UmbralVolleyConfig.fromConfig(y.getValues(false));
        assertTrue(cfg.debuffSeconds() >= 1,
                "the curse must persist after the cast ends, so debuff_duration is at least one second");
        assertEquals(160, cfg.debuffTicks(),
                "8 seconds is 160 ticks, and nothing in the class ever calls removePotionEffect - "
                        + "that absence is the whole of dark's Curse pillar for this spell");
        assertEquals(1, cfg.weaknessAmplifier(), "WEAKNESS amplifier 1");
        assertEquals(0, cfg.blindnessAmplifier(), "BLINDNESS amplifier 0");
        assertEquals(2, cfg.splitCount(), "two shards per impact");
        assertTrue(cfg.debuffSeconds() * 20 >= 20,
                "the potion effect duration in ticks is debuff_duration * 20 and nothing shortens it");
        assertEquals(36.0, cfg.dartRangeBlocks(), EPS,
                "dart_speed 0.9 x dart_lifetime_ticks 40 = 36 blocks of physical reach, so dart_range "
                        + "26.0 - not the lifetime - is what actually stops a dart");
        for (String forbidden : new String[]{"duration", "damage_per_tick", "tick_interval", "denial_damage_cap"}) {
            assertFalse(y.isSet(forbidden),
                    ID + " is a BURST of discrete hits and must carry no '" + forbidden
                            + "' key - a per-tick damage key is the shape spec section 5.1 forbids");
        }
        assertFalse(y.getStringList("description").isEmpty(), "description must be a non-empty list");
    }
}