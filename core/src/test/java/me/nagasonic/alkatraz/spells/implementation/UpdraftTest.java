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
 * Spec sections 5, 5.2 and 12.1: updraft declares a role allowed for AIR, its
 * single cast stays inside the circle-3 BURST budget, and its cost keys are the
 * circle-3 ladder row verbatim.
 *
 * <p>Zero Bukkit mocks - getResourceAsStream plus YamlConfiguration, following
 * AlkatrazConfigPathsTest.
 */
class UpdraftTest {

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = UpdraftTest.class.getResourceAsStream("/spells/updraft.yml")) {
            assertNotNull(in, "missing bundled resource /spells/updraft.yml");
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
        assertNotNull(role, "updraft must declare a role: key or SpellBudgetTest cannot judge it");
        assertEquals(SpellRole.BURST, role, "a knock-up burst is a BURST, not a SUSTAIN");
        assertTrue(SpellBudgets.isAllowed(Element.valueOf(y.getString("element")), role),
                "AIR may use BURST, SUSTAIN, UTILITY and DENIAL - but never TERRAIN");
    }

    @Test
    void declaredDamageTotalStaysInsideTheCircleThreeBurstBudget() throws IOException {
        YamlConfiguration y = load();
        double total = y.getDouble("power") * hitCount(y);
        assertEquals(5.5, total, 1e-9, "one application of the circle-3 ladder power");
        assertTrue(total <= SpellBudgets.burstBudget(3),
                "updraft deals " + total + " per cast against the circle-3 BURST budget of "
                        + SpellBudgets.burstBudget(3));
    }

    @Test
    void costKeysMatchTheCircleThreeLadderRow() throws IOException {
        YamlConfiguration y = load();
        assertEquals(48, y.getInt("mana_cost"), "circle-3 mana_cost from the spec section 6 ladder");
        assertEquals(6, y.getInt("cooldown"), "circle-3 cooldown from the spec section 6 ladder");
        assertEquals(65, y.getDouble("required_focus"), 1e-9,
                "circle-3 required_focus from the spec section 6 ladder");
        assertEquals(1.0, y.getDouble("cast_time"), 1e-9,
                "circle-3 cast_time; SpellBudgets.castTimeRange(3) is 5-50 ticks so 20 is legal");
        assertTrue(SpellBudgets.castTimeRange(3).contains((int) Math.round(y.getDouble("cast_time") * 20.0)),
                "circle 3 allows " + SpellBudgets.castTimeRange(3).minTicks() + ".."
                        + SpellBudgets.castTimeRange(3).maxTicks() + " ticks");
    }

    @Test
    void levelIsThreeAndPowerIsTheUnroundedLadderValue() throws IOException {
        YamlConfiguration y = load();
        assertEquals("updraft", y.getString("id"), "the id key must equal the filename stem");
        assertEquals(3, y.getInt("level"),
                "updraft fills AIR's circle-3 gap between wind_vortex and wind_barrier");
        assertEquals("LLSSL", y.getString("code"), "the cast code allocated to updraft");
        assertEquals(4149, y.getInt("gui_custom_model_data"),
                "4149 is updraft's allocated model slot and is never reused");
        assertEquals(5.5, y.getDouble("power"), 1e-9,
                "circle-3 power from the spec section 6 ladder - this is the cheapest ladder row in "
                        + "wave 5 and must not be inflated");
    }

    @Test
    void theColumnDamagesEachTargetOnceAndLiftsVertically() throws IOException {
        YamlConfiguration y = load();
        assertFalse(y.isSet("hit_count"),
                "updraft damages a target exactly once per cast, so declaring hit_count would "
                        + "under-report nothing and mislead SpellBudgetTest");
        assertEquals(1, hitCount(y), "an absent hit_count key means one damage application per cast");
        assertEquals(10.0, y.getDouble("lift_duration"), 1e-9,
                "10 seconds is 200 ticks of runnable; the code divides by ticks - 1 to reach the far end");
        assertEquals(6.0, y.getDouble("lift_range"), 1e-9,
                "the column's reach, read from this key rather than hardcoded, so the two cannot drift");
        assertEquals(2.0, y.getDouble("lift_radius"), 1e-9, "the column's horizontal hit radius");
        assertTrue(y.getDouble("lift_velocity") > 0.0,
                "the knock-up is updraft's whole identity, so lift_velocity must be positive");
        assertTrue(y.getDouble("lift_velocity") != y.getDouble("power"),
                "the lift and the damage are deliberately unrelated constants - air_burst writes its "
                        + "damage number into velocity, which ties mobility scaling to damage scaling");
        // AIR is forbidden from TERRAIN (spec 5.2), so no cap key may ever appear here.
        assertFalse(y.isSet("terrain_budget"), "AIR is forbidden from TERRAIN, so updraft caps no blocks");
    }
}