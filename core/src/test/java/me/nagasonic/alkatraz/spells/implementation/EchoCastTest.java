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
 * Spec sections 5, 5.2 and 12.1: echo_cast declares a role allowed for NONE, carries no damage
 * key, its cost keys are the circle-8 ladder row verbatim, and its 60% is a mana model declared as
 * {@code replay_cost_percent} - not a power scaling.
 *
 * <p>Zero Bukkit mocks - getResourceAsStream plus YamlConfiguration, following AlkatrazConfigPathsTest
 * and NoneSpellConfigTest. {@link EchoCastConfig} is Bukkit-free, so its defaults are asserted here
 * rather than in a sixth test method.
 */
class EchoCastTest {

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = EchoCastTest.class.getResourceAsStream("/spells/echo_cast.yml")) {
            assertNotNull(in, "missing bundled resource /spells/echo_cast.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsParseableAndAllowedForNone() throws IOException {
        YamlConfiguration y = load();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "echo_cast must declare a role: key or SpellBudgetTest cannot judge it");
        assertEquals(SpellRole.UTILITY, role,
                "echo_cast re-fires other spells and deals no damage of its own, so UTILITY - the "
                        + "role SpellBudgetTest judges on nothing");
        assertEquals(Element.NONE, Element.valueOf(y.getString("element")),
                "echo_cast manipulates the caster's rotation, so it stays on element NONE");
        assertTrue(SpellBudgets.isAllowed(Element.NONE, role),
                "NONE's allowlist is exactly {UTILITY, DENIAL}");
    }

    @Test
    void aUtilitySpellCarriesNoDamageKey() throws IOException {
        YamlConfiguration y = load();
        assertFalse(y.isSet("power"),
                "echo_cast is UTILITY - judged on nothing - so a power key is dead config and "
                        + "SpellBudgetTest.utilitySpellsCarryNoDamageKey fails the build");
        assertFalse(y.isSet("hit_count"),
                "echo_cast damages no target itself; the replays are other spells' own casts");
        assertFalse(y.isSet("denial_damage_cap"),
                "echo_cast is UTILITY, not DENIAL, and never declares incidental damage");
        assertEquals(0.0, y.getDouble("power") * (y.isSet("hit_count") ? y.getInt("hit_count") : 1), 1e-9,
                "the declared per-cast total is 0.0 against a burstBudget(8) of "
                        + SpellBudgets.burstBudget(8));
    }

    @Test
    void costKeysMatchTheCircleEightLadderRow() throws IOException {
        YamlConfiguration y = load();
        assertEquals(450, y.getInt("mana_cost"), "circle-8 mana_cost from the spec section 6 ladder");
        assertEquals(16, y.getInt("cooldown"), "circle-8 cooldown from the spec section 6 ladder");
        assertEquals(153, y.getDouble("required_focus"), 1e-9,
                "circle-8 required_focus from the spec section 6 ladder. It is EXACTLY "
                        + "0.85 * max_focus(8) = 153.0, which passes "
                        + "SpellBudgetTest.focusCapsStayAtOrBelowEightyFivePercentOfMaxFocus with "
                        + "zero headroom - equality satisfies the <= assertion");
        assertEquals(1.5, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 8 to the single point 30 ticks = 1.5s, "
                        + "so 1.5 is the only legal circle-8 cast time; the spec's original "
                        + "single-value ladder (2.25) was withdrawn when the range replaced it, and "
                        + "sunfire_barrage at circle 8 takes the same 1.5s");
        assertTrue(SpellBudgets.castTimeRange(8).contains(30),
                "SpellBudgetsTest.everyShippedSpellCastTimeFallsInsideItsCircleRange reads the shipped "
                        + "ymls, and 30 is the circle-8 window exactly");
    }

    @Test
    void levelIsEightAndCustomModelDataIs4164() throws IOException {
        YamlConfiguration y = load();
        assertEquals(8, y.getInt("level"),
                "circle 8 is where the chain-ultimate archetype sits, two above the last "
                        + "NONE damage spell");
        assertEquals(4164, y.getInt("gui_custom_model_data"),
                "4164 is echo_cast's slot in the 4137-4165 allocation band");
        assertEquals("LLSLS", y.getString("code"), "the cast code from the wave-5 roster");
        assertEquals("echo_cast", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it, and "
                        + "resolveSequence looks spells up by exactly this string");
        assertFalse(WindUp.requiresWindUp("echo_cast", 8),
                "each replayed spell carries its own wind-up, so a wrapper telegraph on echo_cast "
                        + "itself would be a double-tell");
    }

    @Test
    void theSixtyPercentIsAManaModelAndTheSkipListExcludesSelfCancellingSpells() throws IOException {
        YamlConfiguration y = load();
        assertEquals(60, y.getInt("replay_cost_percent"),
                "the deviations table fixes the echo at 60% cost per re-cast");
        assertEquals(3, y.getInt("history_size"),
                "the echo replays the last three spells, and this is also the deque's bound");

        EchoCastConfig cfg = EchoCastConfig.fromConfig(Map.of());
        assertEquals(3, cfg.historySize());
        assertEquals(60, cfg.replayCostPercent());
        assertEquals(8, cfg.replayGapTicks());

        // The 60% is applied to MANA, never to damage: there is no power to scale.
        assertEquals(144, cfg.replayCost(240), "ceil(240 * 0.60)");
        assertEquals(60, cfg.replayCost(100), "ceil(100 * 0.60)");
        assertEquals(9, cfg.replayCost(15), "ceil(15 * 0.60) - the rounding is upward, never down");
        assertEquals(0, cfg.replayCost(0), "a zero-cost spell replays free");

        // A spell whose castAction self-cancels or opens a GUI cannot be re-entered.
        assertTrue(cfg.isSkipped("echo_cast"), "an echo must never echo itself");
        assertTrue(cfg.isSkipped("arcane_resonance"),
                "its castAction installs pooled StatModifierImpacts and its own listener would "
                        + "amplify the replay");
        assertTrue(cfg.isSkipped("warp"),
                "Warp.castAction calls cancelCast(caster) unconditionally at Warp.java:113, which "
                        + "would silently refund the caster's next genuine warp");
        assertTrue(cfg.isSkipped("blink"),
                "Blink.castAction self-cancels on both failure paths, the same refund leak");
        assertFalse(cfg.isSkipped("fireball"), "an ordinary spell must remain replayable");
        assertEquals(List.of("echo_cast", "arcane_resonance", "warp", "blink"),
                y.getStringList("skip_ids"),
                "the yml skip list and the record's default must agree, or the resource is "
                        + "quietly overriding the code");
    }
}