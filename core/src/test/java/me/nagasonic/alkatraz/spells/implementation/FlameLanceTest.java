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
 * YML-only assertions, so no Bukkit mock is needed. getResourceAsStream plus
 * YamlConfiguration, following AlkatrazConfigPathsTest and FireSpellConfigTest.
 */
class FlameLanceTest {

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = FlameLanceTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static YamlConfiguration yml() throws IOException {
        return load("flame_lance");
    }

    @Test
    void roleIsParseableAndAllowedForFire() throws IOException {
        YamlConfiguration y = yml();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "flame_lance must declare a role: key");
        assertEquals(Element.FIRE, Element.valueOf(y.getString("element")),
                "flame_lance is a fire spell");
        assertTrue(SpellBudgets.isAllowed(Element.FIRE, role),
                "flame_lance declares " + role + " but FIRE may not use it");
        assertFalse(SpellRole.SUSTAIN == role,
                "fire never sustains - every fire damage event is discrete and front-loaded");
    }

    @Test
    void oneHitPerTargetStaysInsideTheCircleFiveBurstBudget() throws IOException {
        YamlConfiguration y = yml();
        double power = y.getDouble("power");
        int hitCount = y.isSet("hit_count") ? y.getInt("hit_count") : 1;
        assertEquals(1, hitCount,
                "flame_lance damages each target ONCE for the whole beam, so the wave-3 "
                        + "deviations table says it declares no hit_count key at all");
        assertFalse(y.isSet("hit_count"),
                "a hit_count on flame_lance would make SpellBudgetTest read 10.8 x N, which is a lie");
        assertEquals(10.8, power, 1e-9, "power is the section 6 ladder value for circle 5");
        assertEquals(22.1, SpellBudgets.burstBudget(5), 1e-9, "circle-5 BURST budget");
        assertTrue(power * hitCount <= SpellBudgets.burstBudget(5),
                "one 10.8 hit must sit inside the circle-5 BURST budget of 22.1");
    }

    @Test
    void ladderCostsMatchTheCircleFiveLadder() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(120, y.getInt("mana_cost"), "section 6 ladder circle-5 mana");
        assertEquals(9, y.getInt("cooldown"), "section 6 ladder circle-5 cooldown");
        assertEquals(115, y.getInt("required_focus"), "section 6 ladder circle-5 focus");
        assertEquals(1.5, y.getDouble("cast_time"), 1e-9, "section 6 ladder circle-5 cast time");
        assertTrue(SpellBudgets.castTimeRange(5).contains(30),
                "1.5s is 30 ticks, inside the circle-5 cast-time window "
                        + SpellBudgets.castTimeRange(5).minTicks() + "-"
                        + SpellBudgets.castTimeRange(5).maxTicks());
    }

    @Test
    void levelIsTheSpellCircle() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(5, y.getInt("level"), "flame_lance is a circle-5 spell");
        assertEquals("RLSLR", y.getString("code"), "cast code from the roster table");
        assertEquals(4137, y.getInt("gui_custom_model_data"),
                "4137 is flame_lance's allocated gui_custom_model_data and is never reused");
        assertEquals("flame_lance", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
    }

    @Test
    void damageIsOneDiscreteHitInsideOneSecondNotATick() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(24.0, y.getDouble("beam_length"), 1e-9, "the beam reaches 24 blocks");
        assertEquals(1.5, y.getDouble("beam_speed"), 1e-9, "the front advances 1.5 blocks per tick");
        double travelSeconds = y.getDouble("beam_length") / y.getDouble("beam_speed") / 20.0;
        assertEquals(0.8, travelSeconds, 1e-9,
                "24.0 / 1.5 = 16 ticks = 0.8s, so the whole beam lands inside one second");
        assertTrue(travelSeconds <= 1.0,
                "spec section 5.1 requires every fire damage event to land within a second of the cast");
        assertTrue(y.getDouble("hit_radius") > y.getDouble("beam_radius"),
                "the damage volume must not be narrower than the visible dust");
        assertTrue(y.getDouble("hit_radius") >= y.getDouble("beam_radius") + 0.4,
                "hit_radius must cover a 0.4-block entity half-width or standing targets are missed");
        assertEquals(40, y.getInt("ignite_ticks"),
                "one discrete ignition per target; a refreshed ignition would be a DOT");
        for (String forbidden : new String[]{"duration", "damage_per_tick", "tick_interval"}) {
            assertFalse(y.isSet(forbidden),
                    "flame_lance is a BURST and must carry no '" + forbidden
                            + "' key - a per-tick damage key is the shape spec section 5.1 forbids");
        }
    }
}