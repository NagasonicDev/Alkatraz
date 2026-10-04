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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YML-only assertions, so no Bukkit mock is needed. getResourceAsStream plus
 * YamlConfiguration, following AlkatrazConfigPathsTest and FireSpellConfigTest.
 */
class SunfireBarrageTest {

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = SunfireBarrageTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static YamlConfiguration yml() throws IOException {
        return load("sunfire_barrage");
    }

    @Test
    void roleIsParseableAndAllowedForFire() throws IOException {
        YamlConfiguration y = yml();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "sunfire_barrage must declare a role: key");
        assertEquals(Element.FIRE, Element.valueOf(y.getString("element")),
                "sunfire_barrage is a fire spell");
        assertEquals(SpellRole.BURST, role,
                "a channeled barrage of discrete impacts is still a BURST - fire never sustains");
        assertTrue(SpellBudgets.isAllowed(Element.FIRE, role),
                "sunfire_barrage declares " + role + " but FIRE may not use it");
    }

    @Test
    void twoStrikesPerBodyStaysInsideTheCircleEightBurstBudget() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(22.7, y.getDouble("power"), 1e-9,
                "power is the section 6 ladder value for circle 8, taken verbatim");
        assertEquals(2, y.getInt("hit_count"),
                "hit_count is max_strikes_per_target - a body may be struck twice and no more");
        double total = y.getDouble("power") * y.getInt("hit_count");
        assertEquals(45.4, total, 1e-9, "2 strikes x 22.7");
        assertEquals(64.1, SpellBudgets.burstBudget(8), 1e-9, "circle-8 BURST budget");
        assertTrue(total <= SpellBudgets.burstBudget(8),
                "2 x 22.7 = 45.4 must sit inside the circle-8 BURST budget of 64.1");
    }

    @Test
    void ladderCostsMatchTheCircleEightLadder() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(450, y.getInt("mana_cost"), "section 6 ladder circle-8 mana");
        assertEquals(16, y.getInt("cooldown"), "section 6 ladder circle-8 cooldown");
        assertEquals(153, y.getInt("required_focus"), "section 6 ladder circle-8 focus");
        assertTrue(y.getInt("required_focus") <= 0.85 * 180,
                "circle-8 focus must be at or below the 85% ceiling of the 180 maxFocus - "
                        + "153 is exactly 0.85 x 180 and must not be raised");
        assertEquals(1.5, y.getDouble("cast_time"), 1e-9,
                "the wind-up IS the cast time at circle 6+, so this is the channel length, and "
                        + "castTimeRange collapses circle 8 to the single point 30 ticks");
        assertTrue(SpellBudgets.castTimeRange(8).contains(30),
                "1.5s is 30 ticks, exactly the circle-8 cast-time window "
                        + SpellBudgets.castTimeRange(8).minTicks() + "-"
                        + SpellBudgets.castTimeRange(8).maxTicks());
    }

    @Test
    void levelIsTheSpellCircle() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(8, y.getInt("level"), "sunfire_barrage is a circle-8 spell");
        assertEquals("RSLSL", y.getString("code"), "cast code from the roster table");
        assertEquals(4139, y.getInt("gui_custom_model_data"),
                "4139 is sunfire_barrage's allocated gui_custom_model_data and is never reused");
        assertEquals("sunfire_barrage", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
    }

    @Test
    void allFiveStrikesLandInsideOneSecondNotOnATick() throws IOException {
        YamlConfiguration y = yml();
        SunfireBarrageConfig cfg = SunfireBarrageConfig.fromConfig(y.getValues(false));
        assertEquals(5, cfg.strikeCount(), "five pieces of the sun");
        assertEquals(5, cfg.maxMarks());
        assertEquals(2, cfg.maxStrikesPerTarget(),
                "the record's per-target cap IS the yml's hit_count - they cannot drift");
        assertEquals(6, cfg.strikeDelayTicks());
        assertEquals(2, cfg.strikeIntervalTicks());
        assertEquals(16, cfg.strikeSpanTicks(), "6 + 5 x 2");
        assertEquals(0.8, cfg.strikeSpanSeconds(), 1e-9,
                "all five impacts land inside one second of each other");
        assertTrue(cfg.strikeSpanSeconds() <= 1.0,
                "spec section 5.1: no fire damage event lands more than a second after the cast");
        assertEquals(2, cfg.strikesDeliveredPerTarget(),
                "with 5 marks and 5 strikes each body takes one; the surplus can only re-strike "
                        + "and the cap still holds at 2");
        assertEquals(40, cfg.windUpTicks(0, 100),
                "wind_up_duration 2 whole seconds = 40 channel ticks; a fractional value here is "
                        + "truncated by the record's intOf and the channel comes out a second short");
        assertEquals(30, cfg.windUpTicks(100, 100),
                "maximum mastery shortens the channel to 75%, exactly as WarpConfig does");
        assertEquals(2, SunfireBarrageConfig.fromConfig(Map.of()).windUpSeconds(),
                "the record's own fallback for an absent wind_up_duration must be the same 2 whole "
                        + "seconds, so an empty or unreadable yml cannot silently shorten the channel");
        assertEquals(0.25, cfg.moveCancelThreshold(), 1e-9,
                "the channel break threshold matches WarpConfig and SkeletalKnightsConfig");
        for (String forbidden : new String[]{"duration", "damage_per_tick", "tick_interval"}) {
            assertFalse(y.isSet(forbidden),
                    "sunfire_barrage is a BURST of discrete impacts and must carry no '"
                            + forbidden + "' key - a per-tick damage key is the shape spec 5.1 forbids");
        }
    }
}