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
 * YML-only assertions so no Bukkit mock is needed. getResourceAsStream plus
 * YamlConfiguration, following AlkatrazConfigPathsTest.
 */
class LightSpellConfigTest {

    private static YamlConfiguration load(String id) throws IOException {
        try (InputStream in = LightSpellConfigTest.class.getResourceAsStream("/spells/" + id + ".yml")) {
            assertNotNull(in, "missing bundled resource /spells/" + id + ".yml");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void everyLightSpellDeclaresARoleAllowedForItsElement() throws IOException {
        for (String id : new String[]{"buff", "lesser_heal", "heal", "light_buff"}) {
            YamlConfiguration yml = load(id);
            SpellRole role = SpellRole.parse(yml.getString("role"));
            assertNotNull(role, id + " must declare a role: key");
            assertTrue(SpellBudgets.isAllowed(Element.LIGHT, role),
                    id + " declares " + role + " but LIGHT may not use it");
        }
    }

    @Test
    void lightDeclaresNoTerrainSpell() throws IOException {
        assertFalse(SpellBudgets.isAllowed(Element.LIGHT, SpellRole.TERRAIN),
                "LIGHT is forbidden from TERRAIN - light strengthens the caster, it never reshapes land");
        for (String id : new String[]{"buff", "lesser_heal", "heal", "light_buff"}) {
            assertFalse(SpellRole.TERRAIN == SpellRole.parse(load(id).getString("role")),
                    id + " must not be TERRAIN");
        }
    }

    @Test
    void healAndBuffAreUtilityWithNoPowerKey() throws IOException {
        for (String id : new String[]{"buff", "lesser_heal", "heal", "light_buff"}) {
            assertEquals(SpellRole.UTILITY, SpellRole.parse(load(id).getString("role")),
                    id + " is a support spell");
            assertFalse(load(id).isSet("power"),
                    id + " is not a damage spell and correctly carries no power key");
        }
    }

    @Test
    void ladderCostsMatchTheTable() throws IOException {
        assertEquals(24, load("buff").getInt("mana_cost"), "circle-2 mana");
        assertEquals(24, load("lesser_heal").getInt("mana_cost"), "circle-2 mana");
        assertEquals(5, load("buff").getInt("cooldown"), "circle-2 cooldown");
        assertEquals(48, load("heal").getInt("mana_cost"), "circle-3 mana");
        assertEquals(6, load("heal").getInt("cooldown"), "circle-3 cooldown");
        assertEquals(65, load("heal").getInt("required_focus"), "circle-3 focus");
        assertEquals(75, load("light_buff").getInt("mana_cost"), "circle-4 mana");
    }

    @Test
    void buffAndLightBuffKeepTheirOwnDurationKeys() throws IOException {
        assertTrue(load("buff").isSet("base_duration"),
                "buff's duration key survives the rebalance - only mana, cooldown and role move");
        assertTrue(load("buff").isSet("target_range"),
                "buff's target_range key survives");
        assertTrue(load("light_buff").isSet("buff_radius"),
                "light_buff's buff_radius key survives");
        assertTrue(load("light_buff").isSet("base_duration"),
                "light_buff's base_duration key survives");
    }
}