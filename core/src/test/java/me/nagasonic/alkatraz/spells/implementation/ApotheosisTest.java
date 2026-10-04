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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * apotheosis is light's circle-8 transformation: {@code role: UTILITY}, and the assertion
 * that matters is the one about what it does <em>not</em> carry. A {@code power} key on a
 * UTILITY spell is dead config and {@code SpellBudgetTest} fails on it.
 */
class ApotheosisTest {

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = ApotheosisTest.class.getResourceAsStream("/spells/apotheosis.yml")) {
            assertNotNull(in, "missing bundled resource /spells/apotheosis.yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsUtilityAndAllowedForLight() throws IOException {
        YamlConfiguration yml = load();
        SpellRole role = SpellRole.parse(yml.getString("role"));
        assertNotNull(role, "apotheosis must declare a role: key");
        assertEquals(SpellRole.UTILITY, role,
                "apotheosis is a transformation, which is why the deviations table "
                        + "lists it as UTILITY rather than a damage role");
        assertEquals("LIGHT", yml.getString("element"), "the transformation is a light spell");
        assertTrue(SpellBudgets.isAllowed(Element.LIGHT, role),
                role + " must be in LIGHT's allowlist");
    }

    @Test
    void utilitySpellCarriesNoDamageKey() throws IOException {
        YamlConfiguration yml = load();
        assertFalse(yml.isSet("power"),
                "SpellBudgetTest.utilitySpellsCarryNoDamageKey fails on a UTILITY spell "
                        + "with a power key - a transformation reaches no damage call");
        assertFalse(yml.isSet("hit_count"),
                "no damage means no discrete damage applications to count");
    }

    @Test
    void ladderCostsMatchTheCircleEightRow() throws IOException {
        YamlConfiguration yml = load();
        assertEquals(450, yml.getInt("mana_cost"), "section 6 circle-8 mana");
        assertEquals(16, yml.getInt("cooldown"), "section 6 circle-8 cooldown");
        assertEquals(153, yml.getInt("required_focus"), "section 6 circle-8 focus");
        // The spec section 6 ladder lists 2.25s for circle 8, but SpellBudgets collapses
        // circles 6-9 to one legal cast time each and circle 8's is 30 ticks = 1.5s.
        assertEquals(1.5, yml.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 8 to the single point 30 ticks, so 1.5 is "
                        + "the only legal circle-8 cast time");
        assertTrue(SpellBudgets.castTimeRange(8).contains((int) Math.round(yml.getDouble("cast_time") * 20.0)),
                "circle 8 allows " + SpellBudgets.castTimeRange(8).minTicks() + ".."
                        + SpellBudgets.castTimeRange(8).maxTicks() + " ticks");
    }

    @Test
    void levelIsCircleEight() throws IOException {
        YamlConfiguration yml = load();
        assertEquals(8, yml.getInt("level"), "apotheosis is light's circle-8 transformation");
        assertEquals("RSSSS", yml.getString("code"), "the allocated cast code");
        assertEquals(4157, yml.getInt("gui_custom_model_data"),
                "the allocated custom model data value");
    }

    @Test
    void transformationKeysAreAllPresentAndPositiveAndMatchTheRecord() throws IOException {
        YamlConfiguration yml = load();
        assertTrue(yml.isSet("channel_duration"), "the wind-up is a spell-specific key");
        assertTrue(yml.isSet("buff_duration"), "the hold is a spell-specific key");
        assertTrue(yml.isSet("aura_radius"), "the visible column needs a radius");
        assertTrue(yml.isSet("move_cancel_threshold"), "the channel needs an interrupt rule");
        double channel = yml.getDouble("channel_duration");
        double buff = yml.getDouble("buff_duration");
        double aura = yml.getDouble("aura_radius");
        double move = yml.getDouble("move_cancel_threshold");
        assertEquals(6.0, channel, 1e-9, "six seconds of standing still");
        assertEquals(30.0, buff, 1e-9, "thirty seconds of holding");
        assertTrue(channel > 0.0 && buff > 0.0,
                "a non-positive duration would resolve the channel or the aura on cast");
        assertTrue(aura > 0.0, "an aura radius of zero would draw nothing");
        assertTrue(move > 0.0 && move < 1.0,
                "the move threshold must ignore a twitch of the mouse but catch a step");

        // The pure config record must carry the SAME literals the yml does, or a missing key
        // would silently fall back to a different number than the file advertises.
        ApotheosisConfig defaults = ApotheosisConfig.defaults();
        assertEquals(defaults, ApotheosisConfig.fromConfig(Map.of()),
                "defaults() and fromConfig(Map.of()) are one set of literals");
        assertEquals(defaults, ApotheosisConfig.fromConfig(null),
                "a null map is the same set of literals");
        assertEquals(channel, defaults.channelSeconds(), 1e-9, "channel_duration");
        assertEquals(buff, defaults.buffDurationSeconds(), 1e-9, "buff_duration");
        assertEquals(aura, defaults.auraRadius(), 1e-9, "aura_radius");
        assertEquals(move, defaults.moveCancelThreshold(), 1e-9, "move_cancel_threshold");
        assertEquals(120, defaults.channelTicks(0, 100), "six seconds at twenty ticks a second");
        assertEquals(90, defaults.channelTicks(100, 100), "maxed mastery cuts the wind-up to 75%");
        assertEquals(600, defaults.buffTicks(), "thirty seconds of holding");
    }
}
