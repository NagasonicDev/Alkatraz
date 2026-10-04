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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NONE spells are UTILITY and DENIAL only; NONE is forbidden from dealing
 * elemental damage. The single grandfathered exception is magic_missile, which
 * SpellBudgets.isGrandfatheredBurst allowlists by name.
 *
 * <p>YML-only assertions, so no Bukkit mock is needed - getResourceAsStream plus
 * YamlConfiguration, following AlkatrazConfigPathsTest.
 */
class NoneSpellConfigTest {

    private static final String[] NONE_SPELLS =
            {"magic_missile", "detect", "stealth", "barrier", "disguise", "blink", "warp"};

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = NoneSpellConfigTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void everyNoneSpellDeclaresARole() throws IOException {
        for (String id : NONE_SPELLS) {
            SpellRole role = SpellRole.parse(load(id).getString("role"));
            assertNotNull(role, id + " must declare a role: key");
        }
    }

    @Test
    void noneCarriesNoDamageRoleExceptMagicMissile() throws IOException {
        assertFalse(SpellBudgets.isAllowed(Element.NONE, SpellRole.BURST),
                "NONE is forbidden from BURST as a blanket rule");
        assertFalse(SpellBudgets.isAllowed(Element.NONE, SpellRole.SUSTAIN),
                "NONE is forbidden from SUSTAIN - it never deals elemental damage");
        assertFalse(SpellBudgets.isAllowed(Element.NONE, SpellRole.TERRAIN),
                "NONE is forbidden from TERRAIN - it never reshapes land");
        for (String id : NONE_SPELLS) {
            SpellRole role = SpellRole.parse(load(id).getString("role"));
            assertNotNull(role, id + " must declare a role: key");
            if (SpellRole.BURST == role || SpellRole.SUSTAIN == role) {
                assertTrue(SpellBudgets.isGrandfatheredBurst(id, Element.NONE, role),
                        id + " is the sole allowlisted NONE damage role");
            }
        }
    }

    @Test
    void magicMissileIsTheOnlyNoneBurst() throws IOException {
        YamlConfiguration yml = load("magic_missile");
        assertEquals(SpellRole.BURST, SpellRole.parse(yml.getString("role")),
                "magic_missile is the starter every player begins with and the one burst");
        assertEquals(1.5, yml.getDouble("power"), 1e-9, "circle-0 power");
        assertEquals(5, yml.getInt("mana_cost"), "circle-0 mana");
        assertEquals(2, yml.getInt("cooldown"), "circle-0 cooldown");
        assertEquals(0, yml.getInt("required_focus"), "circle-0 focus");
        assertEquals(Element.NONE, Element.valueOf(yml.getString("element")),
                "magic_missile stays on element NONE");
        assertTrue(yml.getDouble("power") <= SpellBudgets.burstBudget(0),
                "a naked circle-0 magic missile must sit inside the 1.5 circle-0 BURST budget");
    }

    @Test
    void barrierIsTheOnlyNoneDenialSpell() throws IOException {
        YamlConfiguration yml = load("barrier");
        assertEquals(SpellRole.DENIAL, SpellRole.parse(yml.getString("role")),
                "barrier blocks incoming attacks, so it is DENIAL");
        assertFalse(yml.isSet("power"),
                "a barrier takes damage rather than dealing it, so it correctly has no power key");
    }

    @Test
    void theOtherFiveNoneSpellsAreUtilityWithNoPowerKey() throws IOException {
        for (String id : new String[]{"detect", "stealth", "disguise", "blink", "warp"}) {
            YamlConfiguration yml = load(id);
            assertEquals(SpellRole.UTILITY, SpellRole.parse(yml.getString("role")),
                    id + " manipulates the caster, not the target");
            assertFalse(yml.isSet("power"),
                    id + " is not a damage spell and correctly carries no power key");
            assertEquals(Element.NONE, Element.valueOf(yml.getString("element")),
                    id + " stays on element NONE");
        }
    }

    /**
     * warp shipped 4118, the same gui_custom_model_data as dark_tendrils. The
     * values of the two neighbouring spells are read at runtime and compared
     * rather than asserted as literals: both belong to DARK and are owned by T13,
     * which is editing them in the same wave, so pinning their numbers here would
     * make this class fail or pass on the other agent's timing rather than on
     * anything it owns. Inequality is the property that actually matters here.
     */
    @Test
    void warpCustomModelDataNoLongerCollidesWithDarkTendrils() throws IOException {
        int warp = load("warp").getInt("gui_custom_model_data");
        int darkTendrils = load("dark_tendrils").getInt("gui_custom_model_data");
        int skeletalKnights = load("skeletal_knights").getInt("gui_custom_model_data");
        assertEquals(4166, warp, "warp was 4118, the same value as dark_tendrils; spec 10.2 assigns 4166");
        assertTrue(warp != darkTendrils, "the warp / dark_tendrils collision is repaired");
        assertTrue(warp != skeletalKnights, "and warp does not collide with skeletal_knights either");
    }

    @Test
    void ladderCostsMatchTheTable() throws IOException {
        assertEquals(24, load("detect").getInt("mana_cost"), "circle-2 mana");
        assertEquals(5, load("detect").getInt("cooldown"), "circle-2 cooldown");
        assertEquals(40, load("detect").getInt("required_focus"), "circle-2 focus");
        assertEquals(24, load("stealth").getInt("mana_cost"), "circle-2 mana");
        assertEquals(40, load("stealth").getInt("required_focus"), "circle-2 focus");
        assertEquals(48, load("barrier").getInt("mana_cost"), "circle-3 mana");
        assertEquals(6, load("barrier").getInt("cooldown"), "circle-3 cooldown");
        assertEquals(65, load("barrier").getInt("required_focus"), "circle-3 focus");
        assertEquals(48, load("disguise").getInt("mana_cost"), "circle-3 mana");
        assertEquals(6, load("disguise").getInt("cooldown"), "circle-3 cooldown");
        assertEquals(65, load("disguise").getInt("required_focus"), "circle-3 focus");
        assertEquals(75, load("blink").getInt("mana_cost"), "circle-4 mana");
        assertEquals(7, load("blink").getInt("cooldown"), "circle-4 cooldown");
        assertEquals(90, load("blink").getInt("required_focus"), "circle-4 focus");
        assertEquals(165, load("warp").getInt("mana_cost"), "circle-6 mana");
        assertEquals(11, load("warp").getInt("cooldown"), "circle-6 cooldown");
        assertEquals(135, load("warp").getInt("required_focus"), "circle-6 focus");
    }

    @Test
    void noneStealthKeepsItsZeroCooldown() throws IOException {
        YamlConfiguration yml = load("stealth");
        assertEquals(0, yml.getInt("cooldown"),
                "stealth is a sustained toggle paid for by cost_over_time, so its cooldown "
                        + "stays at 0 while its circle-2 mana moves to the ladder's 24");
        assertTrue(yml.isSet("cost_over_time"),
                "the per-circle upkeep table survives the rebalance");
    }

    @Test
    void anUnrecognisedRoleFailsRatherThanCoercing() {
        assertNull(SpellRole.parse("BURSTT"),
                "a typo must not coerce to a valid role, or SpellBudgetTest cannot catch it");
    }
}