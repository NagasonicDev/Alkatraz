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
 * Spec sections 5, 5.2 and 12.1: thunderclap declares a role allowed for AIR, its
 * single application stays inside the circle-6 BURST budget, and its cost keys are
 * the circle-6 ladder row.
 *
 * <p>Zero Bukkit mocks - getResourceAsStream plus YamlConfiguration.
 */
class ThunderclapTest {

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = ThunderclapTest.class.getResourceAsStream("/spells/thunderclap.yml")) {
            assertNotNull(in, "missing bundled resource /spells/thunderclap.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** Mirrors SpellBudgetTest.hitCount: absent means one damage application per cast. */
    private static int hitCount(YamlConfiguration y) {
        return y.isSet("hit_count") ? Math.max(1, y.getInt("hit_count")) : 1;
    }

    @Test
    void roleIsParseableAndAllowedForAir() throws IOException {
        YamlConfiguration y = load();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "thunderclap must declare a role: key or SpellBudgetTest cannot judge it");
        assertEquals(SpellRole.BURST, role, "one concussive blast is a BURST, not a SUSTAIN");
        assertTrue(SpellBudgets.isAllowed(Element.valueOf(y.getString("element")), role),
                "AIR may use BURST, SUSTAIN, UTILITY and DENIAL - but never TERRAIN");
    }

    @Test
    void declaredDamageTotalStaysInsideTheCircleSixBurstBudget() throws IOException {
        YamlConfiguration y = load();
        double total = y.getDouble("power") * hitCount(y);
        assertEquals(14.8, total, 1e-9, "one application of the full circle-6 ladder power");
        assertTrue(total <= SpellBudgets.burstBudget(6),
                "thunderclap deals " + total + " per cast against the circle-6 BURST budget of "
                        + SpellBudgets.burstBudget(6));
    }

    @Test
    void costKeysMatchTheCircleSixLadderRow() throws IOException {
        YamlConfiguration y = load();
        assertEquals(165, y.getInt("mana_cost"), "circle-6 mana_cost from the spec section 6 ladder");
        assertEquals(11, y.getInt("cooldown"), "circle-6 cooldown from the spec section 6 ladder");
        assertEquals(135, y.getDouble("required_focus"), 1e-9,
                "circle-6 required_focus from the spec section 6 ladder, and it must stay at or below "
                        + "SpellBudgetTest's 85% ceiling of 0.85 * maxFocus(6) = 136");
        // The spec section 6 ladder lists 1.75s for circle 6, but SpellBudgets collapses
        // circles 6-9 to a single legal cast time each and circle 6's is 20 ticks = 1.0s.
        // Every other shipped circle-6 spell (warp, skeletal_knights, meteor_shower) already
        // declares 1.0, and SpellBudgetsTest scans those files. 1.75 would be 35 ticks,
        // which is not in the circle-6 window at all.
        assertEquals(1.0, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 6 to the single point 20 ticks, so 1.0 is "
                        + "the only legal circle-6 cast time");
        assertTrue(SpellBudgets.castTimeRange(6).contains((int) Math.round(y.getDouble("cast_time") * 20.0)),
                "circle 6 allows " + SpellBudgets.castTimeRange(6).minTicks() + ".."
                        + SpellBudgets.castTimeRange(6).maxTicks() + " ticks");
    }

    @Test
    void levelIsSixAndPowerIsTheLadderValue() throws IOException {
        YamlConfiguration y = load();
        assertEquals("thunderclap", y.getString("id"), "the id key must equal the filename stem");
        assertEquals(6, y.getInt("level"), "thunderclap is the circle-6 air burst");
        assertEquals("LRSLS", y.getString("code"), "the cast code allocated to thunderclap");
        assertEquals(4150, y.getInt("gui_custom_model_data"),
                "4150 is thunderclap's allocated model slot and is never reused");
        assertEquals(14.8, y.getDouble("power"), 1e-9,
                "circle-6 power from the spec section 6 ladder - the loudest single hit at circle 6");
    }

    @Test
    void theBlastIsASingleApplicationSoNoHitCountKeyIsDeclared() throws IOException {
        YamlConfiguration y = load();
        assertFalse(y.isSet("hit_count"),
                "thunderclap damages a target on exactly one tick of its runnable, so declaring a "
                        + "hit_count would over-report the per-cast total");
        assertEquals(1, hitCount(y), "an absent hit_count key means one damage application per cast");
        assertTrue(y.getDouble("charge_ticks") < y.getDouble("clap_ticks"),
                "the charge phase must finish before the runnable expires, or the blast never fires");
        assertTrue(y.getDouble("clap_push") > 0.0,
                "air's signature is that something changes position, so the radial shove must be positive");
        assertTrue(y.getDouble("clap_push") != y.getDouble("power"),
                "the shove is its own constant and not the damage number - tying them together is what "
                        + "lets damage scaling silently become mobility scaling");
        assertFalse(y.isSet("terrain_budget"), "AIR is forbidden from TERRAIN, so thunderclap caps no blocks");
    }
}