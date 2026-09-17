package me.nagasonic.alkatraz.mobs;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.ai.task.TaskBrain;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.config.Config;
import me.nagasonic.alkatraz.config.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MagicBrainsTaskBrainTest {

    private MockedStatic<ConfigManager> cmMock;
    private MockedStatic<Alkatraz> alkatrazMock;

    @BeforeEach
    void setUp() {
        cmMock = mockStatic(ConfigManager.class);
        alkatrazMock = mockStatic(Alkatraz.class);
        alkatrazMock.when(Alkatraz::getInstance).thenReturn(mock(Alkatraz.class));
    }

    @AfterEach
    void tearDown() {
        cmMock.close();
        alkatrazMock.close();
    }

    private YamlConfiguration taskYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tasks.default-activity", "IDLE");
        yaml.set("tasks.activities.IDLE.priority", 1);
        yaml.set("tasks.activities.IDLE.goals.wander.type", "water_avoiding_stroll");
        yaml.set("tasks.activities.FIGHT.priority", 10);
        return yaml;
    }

    @Test
    void registerAllClearsTaskBrainCaches() {
        Config cfg = mock(Config.class);
        when(cfg.get()).thenReturn(new YamlConfiguration());
        cmMock.when(() -> ConfigManager.getConfig(anyString())).thenReturn(cfg);

        MagicBrains.registerAll();
        assertFalse(MagicBrains.isTaskBrain("zombie_mage"));
        assertNull(MagicBrains.taskBrain("zombie_mage"));
        assertNull(MagicBrains.activityBrains("zombie_mage"));
    }

    @Test
    void reloadPopulatesTaskBrainCaches() {
        Config cfg = mock(Config.class);
        when(cfg.get()).thenReturn(taskYaml());
        cmMock.when(() -> ConfigManager.getConfig(anyString())).thenReturn(cfg);

        MagicBrains.reload("zombie_mage");

        assertTrue(MagicBrains.isTaskBrain("zombie_mage"));
        TaskBrain taskBrain = MagicBrains.taskBrain("zombie_mage");
        assertNotNull(taskBrain);
        assertEquals(2, taskBrain.activities().size());
        assertNotNull(MagicBrains.activityBrains("zombie_mage"));
    }

    @Test
    void brainReturnsDefaultActivityGoalsForTaskBrain() {
        Config cfg = mock(Config.class);
        when(cfg.get()).thenReturn(taskYaml());
        cmMock.when(() -> ConfigManager.getConfig(anyString())).thenReturn(cfg);

        MagicBrains.reload("zombie_mage");

        GoalBrain brain = MagicBrains.brain("zombie_mage");
        assertNotNull(brain);
        assertEquals(1, brain.entries().size());
        assertTrue(brain.entries().get(0).goalOrSpec()
                instanceof me.nagasonic.alkatraz.api.mobs.NativeGoalSpec.WaterAvoidingRandomStroll);
    }

    @Test
    void brainFallsBackToFlatCacheWithoutTasks() {
        Config cfg = mock(Config.class);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("goals.move.type", "water_avoiding_stroll");
        when(cfg.get()).thenReturn(yaml);
        cmMock.when(() -> ConfigManager.getConfig(anyString())).thenReturn(cfg);

        MagicBrains.reload("zombie_fighter");

        assertFalse(MagicBrains.isTaskBrain("zombie_fighter"));
        assertNotNull(MagicBrains.brain("zombie_fighter"));
        assertNull(MagicBrains.activityBrains("zombie_fighter"));
        assertNull(MagicBrains.defaultActivityBrain("zombie_fighter"));
    }

    @Test
    void defaultActivityBrainReturnsEmptyWhenDefaultHasNoGoals() {
        Config cfg = mock(Config.class);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tasks.activities.IDLE.priority", 1);
        when(cfg.get()).thenReturn(yaml);
        cmMock.when(() -> ConfigManager.getConfig(anyString())).thenReturn(cfg);

        MagicBrains.reload("zombie_mage");

        assertNotNull(MagicBrains.brain("zombie_mage"));
        assertTrue(MagicBrains.brain("zombie_mage").entries().isEmpty());
    }
}