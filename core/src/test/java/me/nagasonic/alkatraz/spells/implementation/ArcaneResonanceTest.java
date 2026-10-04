package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.spells.vfx.WindUp;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec sections 5, 5.2 and 12.1: arcane_resonance declares a role allowed for NONE, carries no
 * damage key of either kind, its cost keys are the circle-9 ladder row verbatim, and the caps that
 * make a 2.4x amplification safe are declared and bounded.
 *
 * <p>Zero Bukkit mocks - getResourceAsStream plus YamlConfiguration, following AlkatrazConfigPathsTest
 * and NoneSpellConfigTest. {@link ArcaneResonanceConfig} is Bukkit-free, so its defaults are asserted
 * here rather than in a sixth test method.
 */
class ArcaneResonanceTest {

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = ArcaneResonanceTest.class.getResourceAsStream("/spells/arcane_resonance.yml")) {
            assertNotNull(in, "missing bundled resource /spells/arcane_resonance.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsParseableAndAllowedForNone() throws IOException {
        YamlConfiguration y = load();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "arcane_resonance must declare a role: key or SpellBudgetTest cannot judge it");
        assertEquals(SpellRole.UTILITY, role,
                "resonance amplifies other spells and deals no damage of its own, so UTILITY");
        assertEquals(Element.NONE, Element.valueOf(y.getString("element")),
                "arcane_resonance manipulates the caster's own damage stat, so it stays on NONE");
        assertTrue(SpellBudgets.isAllowed(Element.NONE, role),
                "NONE's allowlist is exactly {UTILITY, DENIAL}");
    }

    @Test
    void noneIsStillForbiddenFromEveryDamageRole() throws IOException {
        YamlConfiguration y = load();
        assertFalse(SpellBudgets.isAllowed(Element.NONE, SpellRole.BURST),
                "NONE is forbidden from BURST by spec 5.2 - the budget test's own allowlist, asserted "
                        + "here so this file's element claim is checked and not merely declared");
        assertFalse(SpellBudgets.isAllowed(Element.NONE, SpellRole.SUSTAIN),
                "NONE is forbidden from SUSTAIN by spec 5.2");
        assertFalse(SpellBudgets.isAllowed(Element.NONE, SpellRole.TERRAIN),
                "NONE is forbidden from TERRAIN - it never reshapes land");
        assertFalse(SpellBudgets.isGrandfatheredBurst("arcane_resonance", Element.NONE, SpellRole.BURST),
                "magic_missile at circle 0 is the ONLY grandfathered NONE damage spell; "
                        + "arcane_resonance must never be granted that exception");
        assertFalse(y.isSet("power"),
                "arcane_resonance is UTILITY - judged on nothing - so a power key is dead config and "
                        + "SpellBudgetTest.utilitySpellsCarryNoDamageKey fails the build");
    }

    @Test
    void costKeysMatchTheCircleNineLadderRow() throws IOException {
        YamlConfiguration y = load();
        assertEquals(600, y.getInt("mana_cost"), "circle-9 mana_cost from the spec section 6 ladder");
        assertEquals(20, y.getInt("cooldown"), "circle-9 cooldown from the spec section 6 ladder");
        assertEquals(161, y.getDouble("required_focus"), 1e-9,
                "circle-9 required_focus from the spec section 6 ladder, lowered from 162 so that "
                        + "SpellBudgetTest.focusCapsStayAtOrBelowEightyFivePercentOfMaxFocus holds: its "
                        + "ceiling is 0.85 * max_focus(9) = 0.85 * 190 = 161.5, and 161 <= 161.5.");
        assertEquals(2.0, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 9 to the single point 40 ticks = 2.0s, "
                        + "so 2.0 is the only legal circle-9 cast time; the spec's original "
                        + "single-value ladder (3.0) was withdrawn when the range replaced it, and "
                        + "inferno_rite at circle 9 takes the same 2.0s");
        assertTrue(SpellBudgets.castTimeRange(9).contains(40),
                "SpellBudgetsTest.everyShippedSpellCastTimeFallsInsideItsCircleRange reads the shipped "
                        + "ymls, and 40 is the circle-9 window exactly");
    }

    @Test
    void levelIsNineAndCustomModelDataIs4165AndItIsTheParitySentinel() throws IOException {
        YamlConfiguration y = load();
        assertEquals(9, y.getInt("level"),
                "arcane_resonance is the cap row, and it is also the last id in the 4137-4165 "
                        + "allocation band - see below");
        assertEquals(4165, y.getInt("gui_custom_model_data"),
                "4165 is arcane_resonance's slot in the 4137-4165 allocation band, the last of the 29");
        assertEquals("SLSLL", y.getString("code"), "the cast code from the wave-5 roster");
        assertEquals("arcane_resonance", y.getString("id"),
                "the id must equal the filename stem. THREE SpellRegistryParityTest methods "
                        + "Assumptions.assumeTrue on 'arcane_resonance' being REGISTERED, and "
                        + "ElementArchetypeTest's circle-contiguity clause is gated on this yml "
                        + "being BUNDLED - so a mismatch here leaves those tests skipping forever");
        assertFalse(WindUp.requiresWindUp("arcane_resonance", 9),
                "resonance amplifies other casts rather than acting, so the amplified spells are "
                        + "the ones that wind up");
    }

    @Test
    void theCapsThatMakeTwoPointFourTimesAmplificationSafeAreDeclaredAndBounded() throws IOException {
        YamlConfiguration y = load();

        // No hit_count, even though an amplified cast lands twice: the two landings are two
        // casts of ANOTHER spell, each with its own AttackProperties and its own getPower.
        assertFalse(y.isSet("hit_count"),
                "arcane_resonance makes ZERO damage applications to any target; declaring hit_count: 2 "
                        + "would attribute another spell's damage to this one and, with a power key, "
                        + "would be charged against circle 9's BURST budget of 90.0");
        assertFalse(y.isSet("denial_damage_cap"),
                "resonance is UTILITY, not DENIAL, and never declares incidental damage");
        assertEquals(0.0, y.getDouble("power") * (y.isSet("hit_count") ? y.getInt("hit_count") : 1), 1e-9,
                "the declared per-cast damage total is exactly 0.0 against a burstBudget(9) of "
                        + SpellBudgets.burstBudget(9));

        // The three caps, and their yml values.
        assertEquals(3, y.getInt("charge_count"),
                "the counter must be exactly three casts - the deviations table says 'your next 3 casts'");
        assertEquals(1.2, y.getDouble("power_multiplier"), 1e-9,
                "the 120% is a MULTIPLY modifier on the damage stat, applied inside getPower's own "
                        + "getModifiedStat call - never a multiplier on getPower's output");
        assertEquals(20.0, y.getDouble("charge_expiry_seconds"), 1e-9,
                "an armed resonance must expire unused so a charge cannot be banked across a fight");

        ArcaneResonanceConfig cfg = ArcaneResonanceConfig.fromConfig(Map.of());
        assertEquals(3, cfg.chargeCount(), "the default counter size is the ladder brief's three");
        assertEquals(1.2, cfg.powerMultiplier(), 1e-9);
        assertEquals(20.0, cfg.chargeExpirySeconds(), 1e-9);
        assertEquals(400, cfg.expiryTicks(), "20.0 seconds at 20 ticks per second");
        assertEquals(1, ArcaneResonanceConfig.fromConfig(Map.of("charge_expiry_seconds", 0.0)).expiryTicks(),
                "a zero expiry still schedules one tick, never zero - runTaskLater with 0 would fire this "
                        + "tick and clear the charge before it could ever be spent");

        // Self-amplification is the one thing that could compound without bound.
        assertTrue(cfg.isSkipped("arcane_resonance"),
                "resonance must never amplify itself; its own listener would amplify the amplified casting");
        assertTrue(cfg.isSkipped("echo_cast"),
                "echo_cast re-fires other spells, so amplifying it would double an already-multiplied rotation");
        assertTrue(cfg.isSkipped("warp"),
                "Warp.castAction calls cancelCast(caster) unconditionally at Warp.java:113 and opens a "
                        + "GUI; firing it twice opens two menus and poisons the next genuine warp");
        assertFalse(cfg.isSkipped("fireball"), "an ordinary damage spell MUST be amplified - that is the spell");
        assertEquals(List.of("arcane_resonance", "echo_cast", "warp"), y.getStringList("skip_ids"),
                "the yml skip list and the record's default must agree, or the resource is quietly "
                        + "overriding the code");
    }
}