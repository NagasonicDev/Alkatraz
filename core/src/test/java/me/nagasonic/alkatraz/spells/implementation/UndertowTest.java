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
 * YML-only assertions, so no Bukkit mock is needed: getResourceAsStream plus
 * YamlConfiguration, following {@code mobs/AlkatrazConfigPathsTest}.
 */
class UndertowTest {

    private static YamlConfiguration yml() throws IOException {
        try (InputStream in = UndertowTest.class.getResourceAsStream("/spells/undertow.yml")) {
            assertNotNull(in, "missing bundled resource /spells/undertow.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsParseableAndAllowedForWater() throws IOException {
        YamlConfiguration y = yml();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "undertow must declare a parseable role: key or SpellBudgetTest cannot judge it");
        assertEquals(SpellRole.BURST, role, "undertow drags one target and hits it once - that is a BURST");
        assertTrue(SpellBudgets.isAllowed(Element.WATER, role),
                "WATER owns SUSTAIN, DENIAL and BURST, so BURST must be allowed");
    }

    @Test
    void scaledWorstCaseStaysInsideTheCircleSixBurstBudget() throws IOException {
        YamlConfiguration y = yml();
        double scaled = y.getDouble("drag_damage_per_block") * y.getDouble("max_drag_distance");
        double worst = Math.min(scaled, y.getDouble("drag_damage_cap"));

        assertEquals(34.1, y.getDouble("drag_damage_cap"), 1e-9,
                "drag_damage_cap is the circle-6 BURST budget");
        assertEquals(SpellBudgets.burstBudget(6), y.getDouble("drag_damage_cap"), 1e-9,
                "and it traces back to the ladder rather than being invented");
        assertEquals(48.0, scaled, 1e-9, "4.0 per block across the full 12.0 block drag");
        assertEquals(34.1, worst, 1e-9, "the 48.0 is clamped by the declared cap");
        assertTrue(worst <= SpellBudgets.burstBudget(6),
                "undertow deals " + worst + " per cast against a budget of " + SpellBudgets.burstBudget(6));
        assertTrue(y.getDouble("power") <= worst,
                "power is the FLOOR of the scaled range, so it must sit under the ceiling");
    }

    @Test
    void ladderValuesMatchTheCircleSixTable() throws IOException {
        YamlConfiguration y = yml();
        assertEquals(14.8, y.getDouble("power"), 1e-9, "spec section 6 ladder, circle 6");
        assertEquals(165, y.getInt("mana_cost"), "spec section 6 ladder, circle 6");
        assertEquals(11, y.getInt("cooldown"), "spec section 6 ladder, circle 6");
        assertEquals(135, y.getInt("required_focus"), "spec section 6 ladder, circle 6");
        // SpellBudgets.castTimeRange collapses circle 6 to the single point 20 ticks, because
        // CAST_MIN_TICKS and CAST_MAX_TICKS both hold 20 at index 6. The withdrawn section 6
        // ladder column said 1.75; 1.0 is the only value the collapsed window still admits.
        assertEquals(1.0, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 6 to the single point 20 ticks, so 1.0 is "
                        + "the only legal circle-6 cast time");
        assertTrue(SpellBudgets.castTimeRange(6).contains((int) Math.round(y.getDouble("cast_time") * 20.0)),
                "circle 6 allows " + SpellBudgets.castTimeRange(6).minTicks() + ".."
                        + SpellBudgets.castTimeRange(6).maxTicks() + " ticks");
    }

    @Test
    void levelIsCircleSix() throws IOException {
        assertEquals(6, yml().getInt("level"),
                "undertow is a circle-6 spell; Spell.loadCommonConfig reads required_circle from level");
    }

    @Test
    void theDragCanActuallyReachItsOwnDamageCeiling() throws IOException {
        YamlConfiguration y = yml();
        double reachable = y.getDouble("drag_step_per_tick") * y.getInt("drag_duration_ticks");
        assertEquals(y.getDouble("max_drag_distance"), reachable, 1e-9,
                "drag_step_per_tick x drag_duration_ticks must equal max_drag_distance, or the declared "
                        + "damage ceiling is unreachable and the yml is lying about the spell");
        assertFalse(y.isSet("hit_count"),
                "the drag deals exactly one damage instance per cast, guarded by hasHit/hit; a hit_count "
                        + "here would understate nothing and misrepresent the mechanic");
    }
}