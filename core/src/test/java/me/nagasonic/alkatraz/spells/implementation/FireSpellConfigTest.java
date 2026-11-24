package me.nagasonic.alkatraz.spells.implementation;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import me.nagasonic.alkatraz.spells.util.TerrainEditor;
import org.bukkit.Material;
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
class FireSpellConfigTest {

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = FireSpellConfigTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void everyFireSpellDeclaresARoleAllowedForItsElement() throws IOException {
        for (String id : new String[]{"fireball", "fire_blast", "fire_wall", "flaming_volley"}) {
            YamlConfiguration yml = load(id);
            SpellRole role = SpellRole.parse(yml.getString("role"));
            assertNotNull(role, id + " must declare a role: key");
            assertTrue(SpellBudgets.isAllowed(Element.FIRE, role),
                    id + " declares " + role + " but FIRE may not");
        }
    }

    @Test
    void fireDeclaresNoSustainSpell() throws IOException {
        assertFalse(SpellBudgets.isAllowed(Element.FIRE, SpellRole.SUSTAIN),
                "FIRE is forbidden from SUSTAIN - fire has no ticking damage");
        assertEquals(SpellRole.DENIAL, SpellRole.parse(load("fire_wall").getString("role")),
                "fire_wall is a DENIAL wall, not a DoT field");
    }

    @Test
    void burstSpellPowersMatchTheTable() throws IOException {
        assertEquals(3.0, load("fireball").getDouble("power"), 1e-9, "fireball is a circle-1 BURST at power 3.0");
        assertEquals(5.5, load("fire_blast").getDouble("power"), 1e-9, "fire_blast is circle 2 at 5.5");
        assertEquals(4.0, load("flaming_volley").getDouble("power"), 1e-9, "flaming_volley is 4.0 per arrow");
    }

    @Test
    void ladderCostsMatchTheTable() throws IOException {
        assertEquals(15, load("fireball").getInt("mana_cost"), "fireball circle-1 mana");
        assertEquals(4, load("fireball").getInt("cooldown"), "fireball circle-1 cooldown");
        assertEquals(24, load("fire_blast").getInt("mana_cost"), "fire_blast circle-2 mana");
        assertEquals(48, load("fire_wall").getInt("mana_cost"), "fire_wall circle-3 mana");
        assertEquals(65, load("fire_wall").getInt("required_focus"), "fire_wall circle-3 focus");
        assertEquals(75, load("flaming_volley").getInt("mana_cost"), "flaming_volley circle-4 mana");
    }

    @Test
    void burstTotalsStayInsideTheCircleBudget() throws IOException {
        double circle1 = SpellBudgets.burstBudget(1);
        double circle2 = SpellBudgets.burstBudget(2);
        double circle4 = SpellBudgets.burstBudget(4);
        assertTrue(load("fireball").getDouble("power") <= circle1,
                "fireball's power must sit inside the circle-1 BURST budget of " + circle1);
        assertTrue(load("fire_blast").getDouble("power") <= circle2,
                "fire_blast's power must sit inside the circle-2 BURST budget of " + circle2);
        // flaming_volley: 4.0 per arrow x 3 arrows at the default option.
        assertTrue(load("flaming_volley").getDouble("power") * 3.0 <= circle4,
                "3 arrows must total inside the circle-4 BURST budget of " + circle4);
    }

    @Test
    void fireWallBurstIsInsideTheDenialDamageCap() throws IOException {
        double wallPower = load("fire_wall").getDouble("power");
        assertEquals(5.5, wallPower, 1e-9, "fire_wall power is the standard circle-3 value");
        assertEquals(9.0, SpellBudgets.burstBudget(3), 1e-9, "circle-3 BURST budget");
        assertTrue(wallPower <= SpellBudgets.burstBudget(3),
                "a DENIAL spell's incidental damage is capped at the same-circle BURST budget, and "
                        + wallPower + " <= " + SpellBudgets.burstBudget(3));
    }

    @Test
    void fireWallGeometryKeysHaveDefaults() throws IOException {
        YamlConfiguration yml = load("fire_wall");
        assertTrue(yml.isSet("duration"), "duration must be present so getDouble(key, 12.5) has a value");
        assertTrue(yml.isSet("length"), "length must be present so getDouble(key, 25.0) has a value");
        assertTrue(yml.isSet("height"), "height must be present so getDouble(key, 3.0) has a value");
        assertTrue(yml.isSet("barrier_hitpoints"), "the DEFENSE-side barrier needs hitpoints");
    }

    @Test
    void bedrockIsProtectedByIdentityNotSubstring() {
        assertTrue(TerrainEditor.isProtected(Material.BEDROCK),
                "bedrock is protected by an identity comparison, not a name substring");
        assertTrue(TerrainEditor.isProtected(Material.CHEST),
                "containers were never in the substring policy and must now be protected");
        assertFalse(TerrainEditor.isProtected(Material.STONE), "ordinary stone is fair game");
    }
}
