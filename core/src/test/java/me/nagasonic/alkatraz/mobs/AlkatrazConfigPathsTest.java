package me.nagasonic.alkatraz.mobs;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AlkatrazConfigPathsTest {

    @Test
    void mobConfigPathsCoversEveryMagicEntityType() {
        Set<String> paths = Alkatraz.mobConfigPaths();
        for (MagicEntityType type : MagicEntityType.values()) {
            assertTrue(paths.contains(type.getConfigPath()), "missing " + type.getConfigPath());
        }
    }

    @Test
    void brainConfigPathsCoversEveryMagicEntityTypeAndSummon() {
        Set<String> paths = Alkatraz.brainConfigPaths();
        for (MagicEntityType type : MagicEntityType.values()) {
            assertTrue(paths.contains("brains/" + type.getId() + ".yml"), "missing brains/" + type.getId() + ".yml");
        }
        for (String id : MagicBrains.SUMMON_BRAIN_IDS) {
            assertTrue(paths.contains("brains/" + id + ".yml"), "missing brains/" + id + ".yml");
        }
    }

    @Test
    void everyConfiguredResourceExistsInJar() throws IOException {
        Set<String> paths = Alkatraz.mobConfigPaths();
        paths.addAll(Alkatraz.brainConfigPaths());
        for (String path : paths) {
            try (InputStream in = getClass().getResourceAsStream("/" + path)) {
                assertNotNull(in, "missing resource " + path);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                assertFalse(yaml.getKeys(false).isEmpty(), "empty config " + path);
            }
        }
    }
}