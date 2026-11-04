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
 * Spec sections 5, 5.2, 4.1 and 12.1: stormcaller declares a role allowed for AIR.
 *
 * <p>Zero Bukkit mocks - getResourceAsStream plus YamlConfiguration. The pure
 * {@link StormcallerConfig} record is exercised too, so its defaults and the yml
 * cannot drift apart.
 */
class StormcallerTest {

    private static final String ID = "stormcaller";

    private static YamlConfiguration load() throws IOException {
        try (InputStream in = StormcallerTest.class.getResourceAsStream("/spells/" + ID + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + ID + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void roleIsParseableAndAllowedForAir() throws IOException {
        YamlConfiguration y = load();
        SpellRole role = SpellRole.parse(y.getString("role"));
        assertNotNull(role, ID + " must declare a role: key or SpellBudgetTest cannot judge it");
        assertEquals(Element.AIR, Element.valueOf(y.getString("element")),
                ID + " is an AIR spell and must never declare TERRAIN");
        assertEquals(SpellRole.UTILITY, role,
                "a flight utility spell is a UTILITY");
        assertTrue(SpellBudgets.isAllowed(Element.AIR, role),
                "AIR owns UTILITY");
    }

    @Test
    void costKeysMatchTheCircleEightLadderRow() throws IOException {
        YamlConfiguration y = load();
        assertEquals(450, y.getInt("mana_cost"), "circle-8 mana_cost from the spec section 6 ladder");
        assertEquals(16, y.getInt("cooldown"), "circle-8 cooldown from the spec section 6 ladder");
        assertEquals(153, y.getDouble("required_focus"), 1e-9,
                "circle-8 required_focus from the spec section 6 ladder");
        assertTrue(y.getDouble("required_focus") <= 0.85 * 180,
                "153 sits exactly on SpellBudgetTest's 85% ceiling at circle 8 and must not drift above it");
        assertEquals(1.5, y.getDouble("cast_time"), 1e-9,
                "SpellBudgets.castTimeRange collapses circle 8 to the single point 30 ticks, so 1.5 is "
                        + "the only legal circle-8 cast time");
        assertTrue(SpellBudgets.castTimeRange(8).contains((int) Math.round(y.getDouble("cast_time") * 20.0)),
                "circle 8 allows " + SpellBudgets.castTimeRange(8).minTicks() + ".."
                        + SpellBudgets.castTimeRange(8).maxTicks() + " ticks");
    }

    @Test
    void levelIsEight() throws IOException {
        YamlConfiguration y = load();
        assertEquals(ID, y.getString("id"), "the id key must equal the filename stem");
        assertEquals(8, y.getInt("level"), ID + " is the circle-8 air UTILITY");
        assertEquals("RSSLS", y.getString("code"), "the cast code allocated to stormcaller");
        assertEquals(4152, y.getInt("gui_custom_model_data"),
                "4152 is stormcaller's allocated model slot and is never reused");
    }

    @Test
    void theRecordAgrees() throws IOException {
        YamlConfiguration y = load();

        StormcallerConfig fromYml = StormcallerConfig.from(y);
        assertEquals(2.25, fromYml.channelDuration(), 1e-9);
        assertEquals(45, fromYml.channelTicks(0, 100), "2.25s is 45 ticks at zero mastery");
        assertEquals(34, fromYml.channelTicks(100, 100), "max mastery discounts the channel by 0.75x");

        StormcallerConfig defaults = StormcallerConfig.defaults();
        assertEquals(2.25, defaults.channelDuration(), 1e-9);
        assertEquals(12.0, defaults.flightDuration(), 1e-9);
        assertEquals(0.15, defaults.flightSpeed(), 1e-9);
        assertEquals(0.30, defaults.moveCancelThreshold(), 1e-9);
        assertEquals(defaults, StormcallerConfig.fromConfig(null),
                "a null map falls back to exactly the documented defaults");
    }
}