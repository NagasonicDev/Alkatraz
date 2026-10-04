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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec sections 5, 5.2 and 12.1: waystone_recall declares a role allowed for NONE, carries no
 * damage key, its cost keys are the circle-7 ladder row verbatim, and it ships no channel key -
 * the absence of wind_up_duration IS the spell.
 *
 * <p>Zero Bukkit mocks - getResourceAsStream plus YamlConfiguration, following AlkatrazConfigPathsTest
 * and NoneSpellConfigTest. {@link WaystoneRecallConfig} is Bukkit-free, so its defaults are asserted
 * here rather than in a sixth test method.
 */
class WaystoneRecallTest {

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = WaystoneRecallTest.class.getResourceAsStream("/spells/waystone_recall.yml")) {
            assertNotNull(in, "missing bundled resource /spells/waystone_recall.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsParseableAndAllowedForNone() throws IOException {
        YamlConfiguration y = load();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, "waystone_recall must declare a role: key or SpellBudgetTest cannot judge it");
        assertEquals(SpellRole.UTILITY, role,
                "recall moves the caster and touches nobody - UTILITY, which is the role "
                        + "SpellBudgetTest judges on nothing");
        assertEquals(Element.NONE, Element.valueOf(y.getString("element")),
                "waystone_recall manipulates the caster, so it stays on element NONE");
        assertTrue(SpellBudgets.isAllowed(Element.NONE, role),
                "NONE's allowlist is exactly {UTILITY, DENIAL}");
    }

    @Test
    void aUtilitySpellCarriesNoDamageKey() throws IOException {
        YamlConfiguration y = load();
        assertFalse(y.isSet("power"),
                "waystone_recall is UTILITY - judged on nothing - so a power key is dead config "
                        + "and SpellBudgetTest.utilitySpellsCarryNoDamageKey fails the build");
        assertFalse(y.isSet("hit_count"),
                "recall damages no target at all, so a hit_count would under-report nothing");
        assertFalse(y.isSet("denial_damage_cap"),
                "recall is UTILITY, not DENIAL, and never declares incidental damage");
        assertEquals(0.0, y.getDouble("power") * (y.isSet("hit_count") ? y.getInt("hit_count") : 1), 1e-9,
                "the declared per-cast total is 0.0 against a burstBudget(7) of "
                        + SpellBudgets.burstBudget(7));
    }

    @Test
    void costKeysMatchTheCircleSevenLadderRow() throws IOException {
        YamlConfiguration y = load();
        assertEquals(240, y.getInt("mana_cost"), "circle-7 mana_cost from the spec section 6 ladder");
        assertEquals(13, y.getInt("cooldown"), "circle-7 cooldown from the spec section 6 ladder");
        assertEquals(144, y.getDouble("required_focus"), 1e-9,
                "circle-7 required_focus from the spec section 6 ladder, lowered from 145 so that "
                        + "SpellBudgetTest.focusCapsStayAtOrBelowEightyFivePercentOfMaxFocus holds: its "
                        + "ceiling is 0.85 * max_focus(7) = 0.85 * 170 = 144.5, and 144 <= 144.5.");
        assertEquals(1.25, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 7 to the single point 25 ticks = 1.25s, "
                        + "so 1.25 is the only legal circle-7 cast time; the spec's original "
                        + "single-value ladder (2.0) was withdrawn when the range replaced it, and "
                        + "inferno_pilgrimage at circle 7 takes the same 1.25s");
        assertTrue(SpellBudgets.castTimeRange(7).contains(25),
                "SpellBudgetsTest.everyShippedSpellCastTimeFallsInsideItsCircleRange reads the shipped "
                        + "ymls, and 25 is the circle-7 window exactly");
    }

    @Test
    void levelIsSevenAndCustomModelDataIs4163() throws IOException {
        YamlConfiguration y = load();
        assertEquals(7, y.getInt("level"),
                "circle 7 is where the requested longer-distance warp sits, one above warp");
        assertEquals(4163, y.getInt("gui_custom_model_data"),
                "4163 is waystone_recall's slot in the 4137-4165 allocation band");
        assertEquals("SSSLS", y.getString("code"), "the cast code from the wave-5 roster");
        assertEquals("waystone_recall", y.getString("id"),
                "the id key must equal the filename stem - registerIfEnabled keys on it");
        assertFalse(WindUp.requiresWindUp("waystone_recall", 7),
                "instantness is this spell's entire identity, so it is the one circle-7 id "
                        + "WindUp.EXEMPT_FROM_WIND_UP carries");
        assertTrue(WindUp.requiresWindUp("warp", 6),
                "warp keeps its six-second channel on purpose - waystone_recall is only worth "
                        + "having because there is something to skip");
    }

    @Test
    void thereIsNoChannelKeyAndTheConfigRecordDefaultsAreBukkitFree() throws IOException {
        YamlConfiguration y = load();
        assertFalse(y.isSet("wind_up_duration"),
                "the absence of a channel key IS the spell: warp ships wind_up_duration: 6 and is "
                        + "interruptible by movement or damage, waystone_recall resolves in the cast "
                        + "tick and must ship no channel duration at all");

        WaystoneRecallConfig cfg = WaystoneRecallConfig.fromConfig(Map.of());
        assertEquals(2.0, cfg.distanceMultiplier(), 1e-9,
                "recall reaches twice warp's own budgeted distance - the documented deviation");
        assertEquals(6.0, cfg.aimConeDegrees(), 1e-9,
                "the aim cone is small because the destination is chosen by crosshair");
        assertEquals(1, cfg.minRecallSlots());
        assertEquals(5, cfg.maxRecallSlots());
        assertEquals(10, cfg.arrivalExpandTicks());

        // The slot budget can only ever shrink warp's own mastery-gated budget, never exceed it.
        assertEquals(0, cfg.effectiveSlotBudget(0), "no warp mastery unlocks no waystone");
        assertEquals(2, cfg.effectiveSlotBudget(2), "warp's budget applies when it is below the cap");
        assertEquals(5, cfg.effectiveSlotBudget(5), "the cap and the budget agree here");
        assertEquals(5, cfg.effectiveSlotBudget(99),
                "max_recall_slots is a hard ceiling even when warp mastery unlocks more");
        assertEquals(1000.0, cfg.recallDistance(500.0), 1e-9,
                "warp's base_distance of 500.0 doubled is 1000.0");
        assertEquals(2.0, y.getDouble("distance_multiplier"), 1e-9,
                "the yml multiplier and the record's default agree, so the resource is not "
                        + "quietly overriding the code");
    }
}