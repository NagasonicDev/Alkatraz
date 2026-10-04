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
 * radiant_brand is light's circle-6 burst: one brand, one detonation on expiry. There is no
 * {@code hit_count} because the splash is a second application to a <em>different</em>
 * entity, not a second hit on the branded one.
 */
class RadiantBrandTest {

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = RadiantBrandTest.class.getResourceAsStream("/spells/radiant_brand.yml")) {
            assertNotNull(in, "missing bundled resource /spells/radiant_brand.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsParseableAndAllowedForLight() throws IOException {
        YamlConfiguration yml = load();
        SpellRole role = SpellRole.parse(yml.getString("role"));
        assertNotNull(role, "radiant_brand must declare a role: key");
        assertEquals(SpellRole.BURST, role,
                "the brand's single detonation is a burst, however delayed it is");
        assertEquals("LIGHT", yml.getString("element"), "radiant_brand is a light spell");
        assertTrue(SpellBudgets.isAllowed(Element.LIGHT, role),
                role + " must be in LIGHT's allowlist");
    }

    @Test
    void powerTimesHitCountFitsTheCircleSixBurstBudget() throws IOException {
        YamlConfiguration yml = load();
        double power = yml.getDouble("power");
        assertEquals(14.8, power, 1e-9, "the section 6 circle-6 ladder power");
        assertFalse(yml.isSet("hit_count"),
                "one cast detonates once, so no hit_count key is declared");
        assertEquals(14.8, power * 1, 1e-9, "14.8 to the branded target");
        assertTrue(power <= SpellBudgets.burstBudget(6),
                "14.8 must sit inside the circle-6 BURST budget of " + SpellBudgets.burstBudget(6));
    }

    @Test
    void ladderCostsMatchTheCircleSixRow() throws IOException {
        YamlConfiguration yml = load();
        assertEquals(165, yml.getInt("mana_cost"), "section 6 circle-6 mana");
        assertEquals(11, yml.getInt("cooldown"), "section 6 circle-6 cooldown");
        assertEquals(135, yml.getInt("required_focus"), "section 6 circle-6 focus");
        // The spec section 6 ladder lists 1.75s for circle 6, but SpellBudgets collapses
        // circles 6-9 to one legal cast time each and circle 6's is 20 ticks = 1.0s.
        assertEquals(1.0, yml.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 6 to the single point 20 ticks, so 1.0 is "
                        + "the only legal circle-6 cast time");
        assertTrue(SpellBudgets.castTimeRange(6).contains((int) Math.round(yml.getDouble("cast_time") * 20.0)),
                "circle 6 allows " + SpellBudgets.castTimeRange(6).minTicks() + ".."
                        + SpellBudgets.castTimeRange(6).maxTicks() + " ticks");
    }

    @Test
    void levelIsCircleSix() throws IOException {
        YamlConfiguration yml = load();
        assertEquals(6, yml.getInt("level"), "radiant_brand is light's circle-6 burst");
        assertEquals("SRLLR", yml.getString("code"), "the allocated cast code");
        assertEquals(4155, yml.getInt("gui_custom_model_data"),
                "the allocated custom model data value");
    }

    @Test
    void theBrandIsTimedAndDetonatesOnceOnExpiry() throws IOException {
        YamlConfiguration yml = load();
        assertTrue(yml.isSet("brand_duration"), "the brand's lifetime is a spell-specific key");
        double brandDuration = yml.getDouble("brand_duration");
        assertEquals(4.0, brandDuration, 1e-9, "four seconds of mark");
        assertTrue(brandDuration > 0.0,
                "a non-positive brand_duration would make the mark expire on cast");
        assertTrue(yml.getDouble("detonation_radius") > 0.0,
                "the detonation needs a splash radius");
        double splash = yml.getDouble("splash_fraction");
        assertTrue(splash > 0.0 && splash <= 1.0,
                "splash_fraction is a fraction of power for bystanders only, never more than all of it");
        assertFalse(yml.isSet("undead_multiplier"),
                "the x2.0 against the undead is applied by AttackSpell.getPower, not by config");
    }
}
