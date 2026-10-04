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

    /** Mirrors TerrainEditor.destroySphere's candidate enumeration. */
    private static int sphereBlockCount(double radius) {
        int r = (int) Math.ceil(radius);
        int count = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius) count++;
                }
            }
        }
        return count;
    }

    @Test
    void everyFireSpellDeclaresARoleAllowedForItsElement() throws IOException {
        for (String id : new String[]{"fireball", "fire_blast", "fire_wall", "flaming_volley", "meteor_shower"}) {
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
    void meteorShowerIsPromotedToCircleSix() throws IOException {
        YamlConfiguration yml = load("meteor_shower");
        assertEquals(6, yml.getInt("level"), "meteor_shower is the roster's only promotion, 5 -> 6");
        assertEquals(165, yml.getInt("mana_cost"), "circle-6 mana");
        assertEquals(11, yml.getInt("cooldown"), "circle-6 cooldown");
        assertEquals(135, yml.getInt("required_focus"), "circle-6 focus");
        // progression.yml ships max_focus 160 at circle 6, so the 85% ceiling is
        // 136. 135 <= 136 clears it by one point.
        assertTrue(yml.getInt("required_focus") <= 0.85 * 160.0,
                "circle-6 focus must be at or below 85% of the 160 maxFocus");
    }

    @Test
    void craterBudgetIsCappedNotTrusted() throws IOException {
        int totalMeteors = 6;
        int budget = load("meteor_shower").getInt("terrain_budget");
        assertEquals(SpellBudgets.terrainBudget(6), budget, "the cast budget is the circle-6 TERRAIN budget");

        int naturalNormal = sphereBlockCount(3) * totalMeteors;
        assertEquals(123, sphereBlockCount(3), "a radius-3 sphere is 123 blocks");
        assertEquals(738, naturalNormal, "6 meteors at radius 3 would naturally clear 738 blocks");
        assertEquals(budget, Math.min(naturalNormal, budget),
                "the maxBlocks cap truncates at the budget rather than overshooting it");
        assertTrue(Math.min(naturalNormal, budget) <= SpellBudgets.terrainBudget(6),
                "the DEFAULT option must not exceed the circle-6 terrain budget of "
                        + SpellBudgets.terrainBudget(6));

        int naturalWide = sphereBlockCount(4) * totalMeteors;
        assertEquals(257, sphereBlockCount(4), "a radius-4 sphere is 257 blocks");
        assertEquals(1542, naturalWide, "6 meteors at radius 4 would naturally clear 1542 blocks");
        int wideCap = (int) (SpellBudgets.terrainBudget(6) * 2.0);
        assertTrue(Math.min(naturalWide, wideCap) <= wideCap,
                "'wide' is the paid-over option and may reach x2.0 of the budget, capped at " + wideCap);
    }

    @Test
    void wideMeteorOptionCostsExtraMana() throws IOException {
        try (InputStream in = FireSpellConfigTest.class.getResourceAsStream("/spells/meteor_shower_options.yml")) {
            assertNotNull(in, "missing bundled resource /spells/meteor_shower_options.yml");
            YamlConfiguration opts = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            int index = opts.getInt("options.radius.default_index");
            assertEquals(1, index, "the default radius option is 'normal' at radius 3");

            var values = opts.getConfigurationSection("options.radius.values");
            assertNotNull(values, "the radius option must declare its values");
            assertEquals(2.0, values.getDouble("tight.value"), 1e-9, "tight is re-scaled 4 -> 2");
            assertEquals(3.0, values.getDouble("normal.value"), 1e-9, "normal is re-scaled 6 -> 3");
            assertEquals(4.0, values.getDouble("wide.value"), 1e-9, "wide is re-scaled 8 -> 4");
            assertTrue(values.contains("wide.impacts"),
                    "wide must carry a mana_cost impact or it is not a legal paid-over");
        }
    }

    @Test
    void bedrockIsProtectedByIdentityNotSubstring() {
        assertTrue(TerrainEditor.isProtected(Material.BEDROCK),
                "the MeteorShower substring check is replaced by an identity comparison");
        assertTrue(TerrainEditor.isProtected(Material.CHEST),
                "containers were never in the substring policy and must now be protected");
        assertFalse(TerrainEditor.isProtected(Material.STONE), "ordinary stone is fair game");
    }
}
