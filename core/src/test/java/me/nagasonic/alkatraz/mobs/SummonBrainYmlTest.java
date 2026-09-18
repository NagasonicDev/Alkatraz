package me.nagasonic.alkatraz.mobs;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SummonBrainYmlTest {

    @Test
    void summonBrainsAreFlatWithoutPlayerTargetGoal() throws Exception {
        for (String id : MagicBrains.SUMMON_BRAIN_IDS) {
            try (InputStreamReader reader = new InputStreamReader(
                    getClass().getResourceAsStream("/brains/" + id + ".yml"), StandardCharsets.UTF_8)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
                assertNull(yaml.get("tasks"), id + ": flat brain must not define a tasks section");
                assertNotNull(yaml.getList("goals"), id + ": missing goals list");
                List<?> targets = yaml.getList("targets");
                assertNotNull(targets, id + ": missing targets list");
                boolean hasPlayerTarget = targets.stream().anyMatch(entry -> entry instanceof Map<?, ?> map
                        && map.toString().contains("target-class"));
                assertFalse(hasPlayerTarget, id + ": summon brain must not auto-target players");
            }
        }
    }
}