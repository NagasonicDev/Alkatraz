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
import static org.junit.jupiter.api.Assertions.*;

class SkywrathTest {
    private static YamlConfiguration load() throws IOException {
        try (InputStream in = SkywrathTest.class.getResourceAsStream("/spells/skywrath.yml")) {
            assertNotNull(in);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }
    @Test void isCircleNineAirDamageSustainWithoutFlight() throws IOException {
        YamlConfiguration y = load();
        assertEquals("skywrath", y.getString("id"));
        assertEquals(9, y.getInt("level"));
        assertEquals(Element.AIR, Element.valueOf(y.getString("element")));
        assertEquals(SpellRole.SUSTAIN, SpellRole.parse(y.getString("role")));
        assertTrue(SpellBudgets.isAllowed(Element.AIR, SpellRole.SUSTAIN));
        assertEquals(280.0, y.getDouble("power") * y.getInt("hit_count"), 1e-9);
        assertEquals(SpellBudgets.sustainBudget(9), y.getDouble("power") * y.getInt("hit_count"), 1e-9);
        assertFalse(y.isSet("flight_allow"));
        assertFalse(y.isSet("flight_speed"));
        assertEquals("SLRSL", y.getString("code"));
        assertEquals(4153, y.getInt("gui_custom_model_data"));
    }
    @Test void stormCadenceAndRecordMatchTwentyFourSecondDesign() throws IOException {
        YamlConfiguration y = load();
        assertEquals(600, y.getInt("mana_cost"));
        assertEquals(161, y.getInt("required_focus"));
        assertEquals(24.0, y.getDouble("storm_duration"), 1e-9);
        assertEquals(2.4, y.getDouble("strike_interval_seconds"), 1e-9);
        assertEquals(10, y.getInt("hit_count"));
        SkywrathConfig config = SkywrathConfig.from(y);
        assertEquals(480, config.stormTicks());
        assertEquals(48, config.strikeIntervalTicks());
        assertEquals(10, config.hitsPerTarget());
        assertEquals(config, SkywrathConfig.defaults());
    }
}
