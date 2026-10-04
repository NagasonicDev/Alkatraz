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
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YML-only assertions, so no Bukkit mock is needed. getResourceAsStream plus
 * YamlConfiguration, following AlkatrazConfigPathsTest and FireSpellConfigTest.
 */
class InfernoRiteTest {

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = InfernoRiteTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static YamlConfiguration yml() throws IOException {
        return load("inferno_rite");
    }

    @Test
    void roleIsParseableAndAllowedForFire() throws IOException {
        YamlConfiguration y = yml();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "inferno_rite must declare a role: key");
        assertEquals(Element.FIRE, Element.valueOf(y.getString("element")),
                "inferno_rite is a fire spell");
        assertEquals(SpellRole.BURST, role,
                "an eight-second window does not make this a SUSTAIN - the rite has no damage "
                        + "timer, it amplifies damage events the caster already caused");
        assertTrue(SpellBudgets.isAllowed(Element.FIRE, role),
                "inferno_rite declares " + role + " but FIRE may not use it");
    }

    @Test
    void threeChargesOfTwentyEightPointSixStayInsideTheCircleNineBurstBudget() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(28.6, y.getDouble("power"), 1e-9,
                "power is the section 6 ladder value for circle 9, taken verbatim");
        assertEquals(3, y.getInt("hit_count"),
                "hit_count is max_chain_targets - the hard per-cast cap on secondary detonations");
        double total = y.getDouble("power") * y.getInt("hit_count");
        assertEquals(85.8, total, 1e-9, "3 detonations x 28.6");
        assertEquals(90.0, SpellBudgets.burstBudget(9), 1e-9, "circle-9 BURST budget");
        assertTrue(total <= SpellBudgets.burstBudget(9),
                "3 x 28.6 = 85.8 must sit inside the circle-9 BURST budget of 90.0");
        assertTrue(y.getDouble("power") <= SpellBudgets.burstBudget(9),
                "the single power key alone must also sit inside the budget");
    }

    @Test
    void ladderCostsMatchTheCircleNineLadder() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(600, y.getInt("mana_cost"), "section 6 ladder circle-9 mana");
        assertEquals(20, y.getInt("cooldown"), "section 6 ladder circle-9 cooldown");
        assertEquals(161, y.getInt("required_focus"), "section 6 ladder circle-9 focus");
        assertTrue(y.getInt("required_focus") <= 0.85 * 190,
                "circle-9 focus must be at or below the 85% ceiling of the 190 maxFocus - "
                        + "161 is under the 161.5 ceiling and must not be raised");
        assertEquals(2.0, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange(9) is the single point 40 ticks = 2.0s, so 2.0 is "
                        + "the only legal circle-9 cast time");
        assertTrue(SpellBudgets.castTimeRange(9).contains(40),
                "2.0s is 40 ticks, exactly the circle-9 cast-time window");
    }

    @Test
    void levelIsTheSpellCircle() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(9, y.getInt("level"), "inferno_rite is a circle-9 spell");
        assertEquals("SLSRL", y.getString("code"), "cast code from the roster table");
        assertEquals(4140, y.getInt("gui_custom_model_data"),
                "4140 is inferno_rite's allocated gui_custom_model_data and is never reused");
        assertEquals("inferno_rite", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
    }

    @Test
    void theEightSecondWindowIsEligibilityNotACadence() throws IOException {
        YamlConfiguration y = yml();
        InfernoRiteConfig cfg = InfernoRiteConfig.fromConfig(y.getValues(false));
        assertEquals(3, cfg.maxChainTargets(), "the record's charge cap IS the yml's hit_count");
        assertEquals(2, cfg.chainTargetsPerTrigger());
        assertEquals(2, cfg.detonationsPerTrigger(),
                "a single hit may detonate two bodies, never above the per-cast cap of 3");
        assertEquals(8.0, cfg.duration(), 1e-9, "the rite is armed for eight seconds");
        assertEquals(160, cfg.durationTicks(), "8.0 seconds x 20");
        assertEquals(85.8, cfg.worstCaseTotal(y.getDouble("power")), 1e-9,
                "power x hit_count is the per-cast ceiling regardless of how much fire is thrown");

        // A configuration asking for more detonations per trigger than the cap must not raise
        // the ceiling. This is the guarantee that makes the budget assertion meaningful.
        Map<String, Object> greedyValues = new HashMap<>(y.getValues(false));
        greedyValues.put("chain_targets_per_trigger", 12);
        InfernoRiteConfig greedy = InfernoRiteConfig.fromConfig(greedyValues);
        assertEquals(3, greedy.worstCaseDetonations(), "the per-cast charge counter is unchanged");
        assertEquals(3, greedy.detonationsPerTrigger(),
                "detonationsPerTrigger is clamped to the per-cast cap, so no option or yml edit "
                        + "can produce a fourth detonation");
        assertEquals(85.8, greedy.worstCaseTotal(y.getDouble("power")), 1e-9,
                "and the clamped ceiling is still 3 x 28.6");
        assertEquals(3, InfernoRiteConfig.fromConfig(Map.of()).maxChainTargets(),
                "the record's own fallback for an absent hit_count must be the same 3 charges, so an "
                        + "empty or unreadable yml cannot quietly raise the per-cast ceiling");

        for (String forbidden : new String[]{"damage_per_tick", "tick_interval"}) {
            assertFalse(y.isSet(forbidden),
                    "inferno_rite must carry no '" + forbidden + "' key - the rite has no damage "
                            + "timer at all, and a per-tick damage key is the shape spec 5.1 forbids");
        }
        assertTrue(y.isSet("duration"),
                "duration is legitimate here: it is an eligibility window, and the config "
                        + "comment says so. The rite deals nothing on any schedule.");
        assertTrue(cfg.openingRingTicks() > 0, "the opening ring is declared and defaults to 12");
    }
}