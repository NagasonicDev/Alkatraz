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
 * Spec sections 5, 5.2 and 12.1: arcane_ward declares a role allowed for NONE, deals no damage
 * at all, and its cost keys are the circle-5 ladder row verbatim.
 *
 * <p>Zero Bukkit mocks - getResourceAsStream plus YamlConfiguration, following
 * AlkatrazConfigPathsTest and NoneSpellConfigTest.
 */
class ArcaneWardTest {

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = ArcaneWardTest.class.getResourceAsStream("/spells/arcane_ward.yml")) {
            assertNotNull(in, "missing bundled resource /spells/arcane_ward.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsParseableAndAllowedForNone() throws IOException {
        YamlConfiguration y = load();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "arcane_ward must declare a role: key or SpellBudgetTest cannot judge it");
        assertEquals(SpellRole.DENIAL, role,
                "a ward that absorbs and projects is DENIAL; UTILITY would let SpellBudgetTest "
                        + "judge it on nothing");
        assertEquals(Element.NONE, Element.valueOf(y.getString("element")),
                "arcane_ward manipulates the caster's field, so it stays on element NONE");
        assertTrue(SpellBudgets.isAllowed(Element.NONE, role),
                "NONE's allowlist is exactly {UTILITY, DENIAL}");
    }

    @Test
    void aDenialSpellThatDealsNoDamageCarriesNeitherPowerNorDamageCap() throws IOException {
        YamlConfiguration y = load();
        assertFalse(y.isSet("power"),
                "arcane_ward absorbs damage through BarrierProperties.damage and deals none, so a "
                        + "power key is dead config and trips "
                        + "SpellBudgetTest.denialSpellsCapIncidentalDamageAtTheCircleBurstBudget");
        assertFalse(y.isSet("denial_damage_cap"),
                "without a denial_damage_cap the test asserts the power key is absent; declaring a "
                        + "cap would claim incidental damage the spell never deals");
        assertFalse(y.isSet("hit_count"),
                "arcane_ward damages no target at all, so a hit_count would under-report nothing");
        assertEquals(0.0, y.getDouble("power") * (y.isSet("hit_count") ? y.getInt("hit_count") : 1), 1e-9,
                "the declared per-cast total is 0.0 against a burstBudget(5) of "
                        + SpellBudgets.burstBudget(5));
        assertEquals(SpellRole.DENIAL, SpellRole.parse(y.getString("role")),
                "the cap test only reaches a DENIAL spell through its role key, so a role change "
                        + "here silently disarms both assertions above");
    }

    @Test
    void costKeysMatchTheCircleFiveLadderRow() throws IOException {
        YamlConfiguration y = load();
        assertEquals(120, y.getInt("mana_cost"), "circle-5 mana_cost from the spec section 6 ladder");
        assertEquals(9, y.getInt("cooldown"), "circle-5 cooldown from the spec section 6 ladder");
        assertEquals(115, y.getDouble("required_focus"), 1e-9,
                "circle-5 required_focus from the spec section 6 ladder; SpellBudgetTest's 85% "
                        + "ceiling for circle 5 is 0.85 * 150 = 127.5, so 115 clears it");
        assertEquals(1.5, y.getDouble("cast_time"), 1e-9,
                "circle-5 cast_time, 30 ticks, which sits inside the circle-5 cast-time window "
                        + SpellBudgets.castTimeRange(5).minTicks() + "-"
                        + SpellBudgets.castTimeRange(5).maxTicks() + "; flame_lance at circle 5 "
                        + "takes the same 1.5s");
        assertTrue(SpellBudgets.castTimeRange(5).contains(30),
                "SpellBudgetsTest.everyShippedSpellCastTimeFallsInsideItsCircleRange reads the "
                        + "shipped ymls, and 30 is inside the circle-5 window");
    }

    @Test
    void levelIsFiveAndCustomModelDataIs4162() throws IOException {
        YamlConfiguration y = load();
        assertEquals(5, y.getInt("level"),
                "arcane_ward is the DENIAL above circle 3's barrier");
        assertEquals(4162, y.getInt("gui_custom_model_data"),
                "4162 is arcane_ward's slot in the 4137-4165 allocation band");
        assertEquals("arcane_ward", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
        assertEquals("LLRRL", y.getString("code"),
                "the cast code from the wave-5 roster; SpellCodeRegistryTest requires five "
                        + "characters from {R, L, S} and uniqueness across the whole roster");
    }

    @Test
    void theWardIsPlaceableOnAnAllyAndIsNeverStationary() throws IOException {
        YamlConfiguration y = load();
        assertTrue(y.isSet("max_hitpoints"),
                "BarrierSpell.loadCommonConfig reads max_hitpoints with getDouble and no default, "
                        + "so an absent key silently yields 0.0 and the ward is born broken");
        assertEquals(40.0, y.getDouble("max_hitpoints"), 1e-9,
                "twice barrier's circle-3 20 - this is a circle-5 spell on a circle-3 chassis");
        assertEquals(2.5, y.getDouble("radius"), 1e-9, "the ward shell radius, in blocks");
        assertEquals(45.0, y.getDouble("duration"), 1e-9,
                "45.0 seconds is 225 ticks at the 4-tick period");
        assertEquals(24, y.getInt("target_range"),
                "target_range is what makes the ward placeable on someone other than the caster");
        assertFalse(y.isSet("follow_player"),
                "arcane_ward exists to persist after the caster moves; a follow_player option "
                        + "would turn it back into barrier");
    }
}