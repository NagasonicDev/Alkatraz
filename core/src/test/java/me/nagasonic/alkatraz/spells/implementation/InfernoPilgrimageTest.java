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
class InfernoPilgrimageTest {

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = InfernoPilgrimageTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static YamlConfiguration yml() throws IOException {
        return load("inferno_pilgrimage");
    }

    @Test
    void roleIsParseableAndAllowedForFire() throws IOException {
        YamlConfiguration y = yml();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "inferno_pilgrimage must declare a role: key");
        assertEquals(Element.FIRE, Element.valueOf(y.getString("element")),
                "inferno_pilgrimage is a fire spell");
        assertEquals(SpellRole.BURST, role,
                "three discrete bursts is a BURST - fire never sustains (spec section 5.1)");
        assertTrue(SpellBudgets.isAllowed(Element.FIRE, role),
                "inferno_pilgrimage declares " + role + " but FIRE may not use it");
    }

    @Test
    void threePulsesOfSixteenLandExactlyOnTheCircleSevenBurstBudget() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(16.0, y.getDouble("power"), 1e-9,
                "the documented spec section 9 deviation is power 16, not the ladder's 18.9");
        assertEquals(3, y.getInt("hit_count"),
                "inferno_pilgrimage must declare hit_count: 3 so its total is assertable");
        double total = y.getDouble("power") * y.getInt("hit_count");
        assertEquals(48.0, total, 1e-9, "3 pulses x 16");
        assertEquals(48.0, SpellBudgets.burstBudget(7), 1e-9, "circle-7 BURST budget");
        assertTrue(total <= SpellBudgets.burstBudget(7),
                "3 x 16 = 48 must sit inside, not over, the circle-7 BURST budget of 48.0");
    }

    @Test
    void ladderCostsMatchTheCircleSevenLadder() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(240, y.getInt("mana_cost"), "section 6 ladder circle-7 mana");
        assertEquals(13, y.getInt("cooldown"), "section 6 ladder circle-7 cooldown");
        assertEquals(144, y.getInt("required_focus"), "section 6 ladder circle-7 focus");
        assertEquals(1.25, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 7 to the single point 25 ticks, so "
                        + "1.25 is the only legal circle-7 cast time");
        assertTrue(y.getInt("required_focus") <= 0.85 * 170,
                "circle-7 focus must be at or below the 85% ceiling of the 170 maxFocus - "
                        + "144 is under the 144.5 ceiling and must not be raised");
        assertTrue(SpellBudgets.castTimeRange(7).contains(25),
                "1.25s is 25 ticks, exactly the circle-7 cast-time window "
                        + SpellBudgets.castTimeRange(7).minTicks() + "-"
                        + SpellBudgets.castTimeRange(7).maxTicks());
    }

    @Test
    void levelIsTheSpellCircle() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(7, y.getInt("level"), "inferno_pilgrimage is a circle-7 spell");
        assertEquals("SLRLR", y.getString("code"), "cast code from the roster table");
        assertEquals(4138, y.getInt("gui_custom_model_data"),
                "4138 is inferno_pilgrimage's allocated gui_custom_model_data and is never reused");
        assertEquals("inferno_pilgrimage", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
    }

    @Test
    void pulsesAreDiscreteAndFitInsideTheWallsTravel() throws IOException {
        YamlConfiguration y = yml();
        InfernoPilgrimageConfig cfg = InfernoPilgrimageConfig.fromConfig(y.getValues(false));
        assertEquals(3, cfg.pulseCount(), "the record's pulse count IS the yml's hit_count");
        assertEquals(8, cfg.pulseIntervalTicks());
        assertEquals(24, cfg.travelTicks());
        assertEquals(0.75, cfg.wallSpeed(), 1e-9);
        assertEquals(18.0, cfg.travelBlocks(), 1e-9, "travel_ticks 24 x wall_speed 0.75");
        assertEquals(16, cfg.lastPulseTick(), "the third pulse fires at tick 16");
        assertTrue(cfg.pulsesFitTravel(),
                "16 < 24, so the third pulse is inside the wall's travel - a hit_count the "
                        + "wall cannot deliver would make the budget assert an unreachable total");
        double sequenceSeconds = cfg.lastPulseTick() / 20.0;
        assertEquals(0.8, sequenceSeconds, 1e-9,
                "three pulses over 16 ticks is 0.8s, so every damage event lands within a second");
        assertTrue(sequenceSeconds <= 1.0,
                "spec section 5.1: no fire damage event lands more than a second after the cast");

        // The swept windows must overlap, or a stationary target could never take all
        // three pulses and power x hit_count would overstate the real per-cast total.
        assertTrue(cfg.pulseWindowTo(0) + cfg.pulseReach() >= cfg.pulseWindowFrom(1),
                "pulse 0's reach must reach pulse 1's start, or the three pulses never stack");
        assertTrue(cfg.pulseWindowTo(1) + cfg.pulseReach() >= cfg.pulseWindowFrom(2),
                "pulse 1's reach must reach pulse 2's start, or the three pulses never stack");

        assertEquals(5, cfg.wallWidth());
        assertEquals(2, cfg.wallHalfWidth(), "a 5-wide curtain is drawn at ix = -2..2");
        assertEquals(3, cfg.worstCaseHitsPerTarget());
        assertEquals(3, InfernoPilgrimageConfig.fromConfig(Map.of()).pulseCount(),
                "the record's own fallback for an absent hit_count must be the same 3 pulses, so an "
                        + "empty or unreadable yml cannot quietly make the wall fire fewer bursts");

        for (String forbidden : new String[]{"duration", "damage_per_tick", "tick_interval"}) {
            assertFalse(y.isSet(forbidden),
                    "inferno_pilgrimage is a BURST of three discrete pulses and must carry no '"
                            + forbidden + "' key - a per-tick damage key is the shape spec 5.1 forbids");
        }
    }
}