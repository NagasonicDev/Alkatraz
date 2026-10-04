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
 * YML-only assertions, so no Bukkit mock is needed. getResourceAsStream plus
 * YamlConfiguration, following AlkatrazConfigPathsTest.
 */
class WaterSpellConfigTest {

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = WaterSpellConfigTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void everyWaterSpellDeclaresARoleAllowedForItsElement() throws IOException {
        for (String id : new String[]{"water_sphere", "water_pulse", "geyser", "whirlpool", "tsunami"}) {
            YamlConfiguration yml = load(id);
            SpellRole role = SpellRole.parse(yml.getString("role"));
            assertNotNull(role, id + " must declare a role: key");
            assertTrue(SpellBudgets.isAllowed(Element.WATER, role),
                    id + " declares " + role + " but WATER may not use it");
        }
    }

    @Test
    void waterDeclaresNoTerrainSpell() throws IOException {
        assertFalse(SpellBudgets.isAllowed(Element.WATER, SpellRole.TERRAIN),
                "water never reshapes land - TERRAIN is forbidden for the whole element");
        for (String id : new String[]{"water_sphere", "water_pulse", "geyser", "whirlpool", "tsunami"}) {
            assertFalse(SpellRole.TERRAIN == SpellRole.parse(load(id).getString("role")),
                    id + " must not be TERRAIN");
        }
    }

    @Test
    void burstPowersMatchTheTable() throws IOException {
        assertEquals(3.0, load("water_sphere").getDouble("power"), 1e-9, "water_sphere is circle 1");
        assertEquals(4.2, load("water_pulse").getDouble("power"), 1e-9, "water_pulse is circle 2");
        assertEquals(5.5, load("geyser").getDouble("power"), 1e-9, "geyser is circle 3");
        assertEquals(10.8, load("tsunami").getDouble("power"), 1e-9, "tsunami is circle 5");
    }

    @Test
    void burstPowersStayInsideTheirCircleBudgets() throws IOException {
        assertTrue(load("water_sphere").getDouble("power") <= SpellBudgets.burstBudget(1),
                "water_sphere inside the circle-1 BURST budget");
        assertTrue(load("water_pulse").getDouble("power") <= SpellBudgets.burstBudget(2),
                "water_pulse inside the circle-2 BURST budget");
        assertTrue(load("geyser").getDouble("power") <= SpellBudgets.burstBudget(3),
                "geyser inside the circle-3 BURST budget");
        assertTrue(load("tsunami").getDouble("power") <= SpellBudgets.burstBudget(5),
                "tsunami inside the circle-5 BURST budget");
    }

    @Test
    void squaredBasePowerWouldHaveBrokenTheCircleTwoBudget() throws IOException {
        double base = load("water_pulse").getDouble("power");
        assertTrue(base * base > SpellBudgets.burstBudget(2),
                "4.2 squared is 17.64, over the 6.1 circle-2 budget - this is why the "
                        + "getBasePower() * getBasePower() literal had to go");
    }

    @Test
    void whirlpoolSustainTotalLandsExactlyOnTheBudget() throws IOException {
        double perHit = load("whirlpool").getDouble("power");
        double durationSeconds = load("whirlpool").getDouble("duration");
        long ticks = (long) (durationSeconds * 20);
        long hits = ticks / 20L;
        assertEquals(2.75, perHit, 1e-9, "whirlpool is 2.75 per hit");
        assertEquals(8, hits, "8 seconds at one hit per 20 ticks is 8 hits");
        assertEquals(22.0, perHit * hits, 1e-9, "8 x 2.75 = 22");
        assertEquals(SpellBudgets.sustainBudget(4), perHit * hits, 1e-9,
                "the whirlpool total is exactly the circle-4 SUSTAIN budget of "
                        + SpellBudgets.sustainBudget(4));
    }

    @Test
    void whirlpoolNoLongerCarriesADamagePerTickKey() throws IOException {
        assertFalse(load("whirlpool").isSet("damage_per_tick"),
                "damage_per_tick was a second damage key that made `power` dead config; "
                        + "damage now comes from `power` through getPower");
    }

    @Test
    void whirlpoolIsSustainAndTheRestAreBurst() throws IOException {
        assertEquals(SpellRole.SUSTAIN, SpellRole.parse(load("whirlpool").getString("role")),
                "whirlpool keeps acting after the cast resolves, so it is SUSTAIN");
        assertEquals(SpellRole.BURST, SpellRole.parse(load("water_pulse").getString("role")),
                "water_pulse deals damage now, so it is a BURST");
    }

    @Test
    void geyserDefaultOptionNoLongerCarriesAFreeDamageBonus() throws IOException {
        try (InputStream in = WaterSpellConfigTest.class.getResourceAsStream("/spells/geyser_options.yml")) {
            assertNotNull(in, "missing bundled resource /spells/geyser_options.yml");
            YamlConfiguration opts = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            assertEquals(1, opts.getInt("options.height.default_index"),
                    "default_index 0 pointed at 'short', whose damage MULTIPLY of 1.2 was a free "
                            + "x1.20 on every circle-3 cast; index 1 is 'normal'");

            var heightValues = opts.getConfigurationSection("options.height.values");
            assertNotNull(heightValues, "the height option must keep its values - only the index changed");
            assertEquals(5.0, heightValues.getDouble("short.value"), 1e-9,
                    "the short option itself is legitimate and is not deleted");
        }
    }

    @Test
    void ladderCostsMatchTheTable() throws IOException {
        assertEquals(15, load("water_sphere").getInt("mana_cost"), "circle-1 mana");
        assertEquals(4, load("water_sphere").getInt("cooldown"), "circle-1 cooldown");
        assertEquals(24, load("water_pulse").getInt("mana_cost"), "circle-2 mana");
        assertEquals(48, load("geyser").getInt("mana_cost"), "circle-3 mana");
        assertEquals(75, load("whirlpool").getInt("mana_cost"), "circle-4 mana");
        assertEquals(7, load("whirlpool").getInt("cooldown"), "circle-4 cooldown");
        assertEquals(120, load("tsunami").getInt("mana_cost"), "circle-5 mana");
        assertEquals(115, load("tsunami").getInt("required_focus"), "circle-5 focus");
    }
}