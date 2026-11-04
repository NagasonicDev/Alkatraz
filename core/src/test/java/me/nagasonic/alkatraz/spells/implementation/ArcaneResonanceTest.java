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
 * damage key of either kind, is a mana-drained sustain rather than a cooldowned action, and the
 * caps that make a 2.5x amplification safe are declared and bounded.
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
    void costKeysAndSustainShapeMatchTheCircleNineLadderRow() throws IOException {
        YamlConfiguration y = load();
        assertEquals(600, y.getInt("mana_cost"), "circle-9 mana_cost from the spec section 6 ladder");
        assertEquals(0, y.getInt("cooldown"),
                "resonance is a sustain: a cooldown would block the second cast that turns it off");
        assertEquals(55, y.getInt("upkeep_mana_per_second"),
                "55/s keeps a 60s run at 3300 gross against the 10,000 pool");
        assertEquals(161, y.getDouble("required_focus"), 1e-9);
        assertEquals(2.0, y.getDouble("cast_time"), 1e-9);
    }

    @Test
    void itStillDeclaresNoDamageKeyOfAnyKind() throws IOException {
        YamlConfiguration y = load();
        assertFalse(y.isSet("power"), "UTILITY spells carry no power key");
        assertFalse(y.isSet("hit_count"),
                "resonance makes ZERO damage applications of its own; the burst budget is spent on "
                        + "someone else's cast");
        assertFalse(y.isSet("denial_damage_cap"));
        assertEquals(0.0, y.getDouble("power") * (y.isSet("hit_count") ? y.getInt("hit_count") : 1), 1e-9);
    }

    @Test
    void theAmplificationCapsAreDeclaredAndBounded() throws IOException {
        YamlConfiguration y = load();
        assertEquals(4, y.getInt("charge_count"));
        assertEquals(1.25, y.getDouble("power_multiplier"), 1e-9);
        assertEquals(10, y.getInt("duplicate_delay_ticks"));
        assertFalse(y.isSet("charge_expiry_seconds"),
                "charges refresh on every cast instead of expiring, so the expiry key is gone");

        ArcaneResonanceConfig cfg = ArcaneResonanceConfig.fromConfig(Map.of());
        assertEquals(4, cfg.chargeCount());
        assertEquals(1.25, cfg.powerMultiplier(), 1e-9);
        assertEquals(10, cfg.duplicateDelayTicks());
        assertTrue(cfg.isSkipped("arcane_resonance"));
        assertTrue(cfg.isSkipped("echo_cast"));
        assertTrue(cfg.isSkipped("warp"));
        assertFalse(cfg.isSkipped("fireball"), "an ordinary damage spell MUST be amplified - that is the spell");

        // The duplicate skip list is a DIFFERENT question from the amplify skip list: a
        // duplicated sustain would toggle itself straight back off, and a duplicated terrain
        // cast would spend the block budget twice in one tick.
        assertTrue(cfg.isDuplicable("fireball"));
        assertFalse(cfg.isDuplicable("arcane_resonance"));
        for (String sustain : List.of("skywrath", "tide_of_the_deep", "crown_of_dawn", "oblivion")) {
            assertFalse(cfg.isDuplicable(sustain), sustain + " is a sustain and must never be duplicated");
        }
        for (String terrain : List.of("worldbreaker", "inferno_rite")) {
            assertFalse(cfg.isDuplicable(terrain), terrain + " would double-spend its budget in one tick");
        }
    }

    @Test
    void itKeepsItsIdentityAndItsParitySentinel() throws IOException {
        YamlConfiguration y = load();
        assertEquals(9, y.getInt("level"));
        assertEquals(4165, y.getInt("gui_custom_model_data"));
        assertEquals("SLSLL", y.getString("code"));
        assertEquals("arcane_resonance", y.getString("id"));
        assertEquals(SpellRole.UTILITY, SpellRole.parse(y.getString("role")));
        assertFalse(WindUp.requiresWindUp("arcane_resonance", 9));
    }
}