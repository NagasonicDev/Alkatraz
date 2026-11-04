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

    private static final String ID = "inferno_rite";

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = InfernoRiteTest.class.getResourceAsStream("/spells/" + ID + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + ID + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** Mirrors SpellBudgetTest.hitCount: absent means one damage application per cast. */
    private static int hitCount(YamlConfiguration y) {
        return y.isSet("hit_count") ? Math.max(1, y.getInt("hit_count")) : 1;
    }

    @Test
    void roleIsParseableAndAllowedForFire() throws IOException {
        YamlConfiguration y = load();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, ID + " must declare a role: key");
        assertEquals(Element.FIRE, Element.valueOf(y.getString("element")),
                ID + " is a fire spell");
        assertEquals(SpellRole.BURST, role,
                "a self-spreading twelve second window is still three capped charges, not a "
                        + "repeating damage cadence, so fire never sustains");
        assertTrue(SpellBudgets.isAllowed(Element.FIRE, role),
                ID + " declares " + role + " but FIRE may not use it");
    }

    @Test
    void declaredDamageLandsInsideTheCircleNineBurstBudget() throws IOException {
        YamlConfiguration y = load();
        double total = y.getDouble("power") * hitCount(y);
        assertEquals(57.2, y.getDouble("power"), 1e-9, "the doubled ladder power");
        assertEquals(3, hitCount(y), "three detonations per burning body");
        assertEquals(171.6, total, 1e-9);
        assertEquals(180.0, SpellBudgets.burstBudget(9), 1e-9);
        assertTrue(total <= SpellBudgets.burstBudget(9),
                ID + " deals " + total + " against the circle-9 BURST budget of "
                        + SpellBudgets.burstBudget(9));
    }

    @Test
    void itIgnitesItsOwnTargetsNow() throws IOException {
        YamlConfiguration y = load();
        assertEquals(240, y.getInt("ignite_ticks"), "burn persists through the 12s chain window");
        assertEquals(12.0, y.getDouble("duration"), 1e-9, "the armed window, up from 8s");
        assertEquals(8.0, y.getDouble("chain_radius"), 1e-9, "now the ignition arc radius");
        assertEquals(20, y.getLong("cooldown"), ID + " is one of the two one-shot capshots");
        assertEquals(9, y.getInt("level"));
        assertEquals("SLSRL", y.getString("code"));
        assertEquals(4140, y.getInt("gui_custom_model_data"));
        assertEquals(600, y.getInt("mana_cost"));
        assertEquals(161, y.getInt("required_focus"));
        assertEquals(2.0, y.getDouble("cast_time"), 1e-9);
    }

    @Test
    void ladderCostsMatchTheCircleNineLadder() throws IOException {
        YamlConfiguration y = load();
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
        YamlConfiguration y = load();
        assertEquals(9, y.getInt("level"), ID + " is a circle-9 spell");
        assertEquals("SLSRL", y.getString("code"), "cast code from the roster table");
        assertEquals(4140, y.getInt("gui_custom_model_data"),
                "4140 is " + ID + "'s allocated gui_custom_model_data and is never reused");
        assertEquals(ID, y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
    }

    @Test
    void theWindowLengthIsNotTheDamageCeiling() throws IOException {
        YamlConfiguration y = load();
        InfernoRiteConfig cfg = InfernoRiteConfig.fromConfig(y.getValues(false));
        assertEquals(3, cfg.maxChainTargets(), "the record's charge cap IS the yml's hit_count");
        assertEquals(2, cfg.chainTargetsPerTrigger());
        assertEquals(2, cfg.detonationsPerTrigger(),
                "a single detonation may reach two bodies, never above the per-cast cap of 3");
        assertEquals(12.0, cfg.duration(), 1e-9, "the rite is armed for twelve seconds");
        assertEquals(240, cfg.durationTicks(), "12.0 seconds x 20");
        assertEquals(171.6, cfg.worstCaseTotal(y.getDouble("power")), 1e-9,
                "power x hit_count is the per-cast ceiling regardless of how long the window is");

        // A configuration asking for more bodies per detonation than the cap must not raise
        // the ceiling. This is the guarantee that makes the budget assertion meaningful.
        Map<String, Object> greedyValues = new HashMap<>(y.getValues(false));
        greedyValues.put("chain_targets_per_trigger", 12);
        InfernoRiteConfig greedy = InfernoRiteConfig.fromConfig(greedyValues);
        assertEquals(3, greedy.worstCaseDetonations(), "the per-cast charge counter is unchanged");
        assertEquals(3, greedy.detonationsPerTrigger(),
                "detonationsPerTrigger is clamped to the per-cast cap, so no option or yml edit "
                        + "can produce a fourth detonation");
        assertEquals(171.6, greedy.worstCaseTotal(y.getDouble("power")), 1e-9,
                "and the clamped ceiling is still 3 x 57.2");
        assertEquals(3, InfernoRiteConfig.fromConfig(Map.of()).maxChainTargets(),
                "the record's own fallback for an absent hit_count must be the same 3 charges, so an "
                        + "empty or unreadable yml cannot quietly raise the per-cast ceiling");

        for (String forbidden : new String[]{"damage_per_tick", "tick_interval"}) {
            assertFalse(y.isSet(forbidden),
                    ID + " must carry no '" + forbidden + "' key - the chain is paced by the window "
                            + "task walking burning bodies, and the damage it may ever deal is fixed "
                            + "by hit_count, so a per-tick damage key is the shape spec 5.1 forbids");
        }
        assertTrue(y.isSet("duration"),
                "duration is legitimate here: it is the armed window the chain spreads inside, and "
                        + "the config comment says so.");
        assertTrue(cfg.openingRingTicks() > 0, "the opening ring is declared and defaults to 12");
    }
}