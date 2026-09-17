package me.nagasonic.alkatraz.mobs.ai.task;

import me.nagasonic.alkatraz.api.ai.task.ActivitySpec;
import me.nagasonic.alkatraz.api.ai.task.Sensor;
import me.nagasonic.alkatraz.api.ai.task.TaskBrain;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.mobs.goals.CastSpellGoal;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskBrainFactoryTest {

    private static YamlConfiguration fixture() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tasks.core-memories", java.util.List.of("nearest_hostiles", "is_hurt"));
        yaml.set("tasks.core-sensors",
                java.util.List.of(java.util.Map.of("type", "nearest_living", "radius", 16)));
        yaml.set("tasks.default-activity", "IDLE");
        yaml.set("tasks.activities.IDLE.priority", 1);
        yaml.set("tasks.activities.IDLE.goals.wander.type", "water_avoiding_stroll");
        yaml.set("tasks.activities.IDLE.goals.wander.priority", 4);
        yaml.set("tasks.activities.FIGHT.priority", 10);
        yaml.set("tasks.activities.FIGHT.activation-memories",
                java.util.List.of("nearest_hostiles"));
        yaml.set("tasks.activities.FIGHT.memories-on-enter",
                java.util.List.of(java.util.Map.of("key", "is_hurt", "value", true, "expiry-ticks", 200)));
        yaml.set("tasks.activities.FIGHT.goals.cast.type", "cast_spell");
        yaml.set("tasks.activities.FIGHT.goals.cast.priority", 3);
        yaml.set("tasks.activities.FIGHT.targets.attack.type", "nearest_attackable_target");
        yaml.set("tasks.activities.FIGHT.targets.attack.priority", 2);
        return yaml;
    }

    @Test
    void loadParsesBrainAndActivityGoals() {
        TaskBrainFactory.Result result = TaskBrainFactory.load(fixture().getConfigurationSection("tasks"));

        TaskBrain brain = result.brain();
        assertEquals(java.util.List.of("IDLE", "FIGHT"), brain.activities().stream().map(a -> a.id()).toList());
        assertEquals("IDLE", brain.defaultActivity());
        assertEquals(1, brain.coreSensors().size());
        assertTrue(brain.coreSensors().get(0) instanceof Sensor.NearestLivingEntities);

        GoalBrain fight = result.activityBrains().get("FIGHT");
        assertNotNull(fight);
        assertTrue(fight.entries().stream()
                .filter(e -> !e.isTargetGoal())
                .anyMatch(e -> e.goalOrSpec() instanceof CastSpellGoal));
        assertTrue(fight.entries().stream()
                .filter(GoalBrain.Entry::isTargetGoal)
                .anyMatch(e -> e.goalOrSpec() instanceof me.nagasonic.alkatraz.api.mobs.NativeGoalSpec.NearestAttackableTarget));

        GoalBrain idle = result.activityBrains().get("IDLE");
        assertEquals(1, idle.entries().size());
        assertTrue(idle.entries().get(0).goalOrSpec()
                instanceof me.nagasonic.alkatraz.api.mobs.NativeGoalSpec.WaterAvoidingRandomStroll);
    }

    @Test
    void unknownSensorTypeFailsLoudly() {
        YamlConfiguration yaml = fixture();
        yaml.set("tasks.core-sensors", java.util.List.of(java.util.Map.of("type", "mind_reader")));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TaskBrainFactory.load(yaml.getConfigurationSection("tasks")));
        assertTrue(ex.getMessage().contains("mind_reader"));
    }

    @Test
    void unknownScheduleFailsLoudly() {
        YamlConfiguration yaml = fixture();
        yaml.set("tasks.activities.IDLE.schedule", "brunch");
        assertThrows(IllegalStateException.class,
                () -> TaskBrainFactory.load(yaml.getConfigurationSection("tasks")));
    }

    @Test
    void missingActivitiesFailsLoudly() {
        YamlConfiguration yaml = fixture();
        yaml.set("tasks.activities", null);
        assertThrows(IllegalStateException.class,
                () -> TaskBrainFactory.load(yaml.getConfigurationSection("tasks")));
    }

    @Test
    void undeclaredMemoryReferenceFailsLoudly() {
        YamlConfiguration yaml = fixture();
        yaml.set("tasks.activities.FIGHT.activation-memories", java.util.List.of("ghost_target"));
        assertThrows(IllegalArgumentException.class,
                () -> TaskBrainFactory.load(yaml.getConfigurationSection("tasks")));
    }

    @Test
    void omittedExpiryTicksDefaultsToNeverExpire() {
        YamlConfiguration yaml = fixture();
        yaml.set("tasks.activities.FIGHT.memories-on-enter",
                java.util.List.of(java.util.Map.of("key", "is_hurt", "value", true)));
        TaskBrainFactory.Result result = TaskBrainFactory.load(yaml.getConfigurationSection("tasks"));

        TaskBrain brain = result.brain();
        assertNotNull(brain);
        ActivitySpec fight = brain.activities().stream()
                .filter(a -> a.id().equals("FIGHT"))
                .findFirst().orElseThrow();
        assertEquals(1, fight.memoriesOnEnter().size());
        assertEquals(-1, fight.memoriesOnEnter().get(0).expireTicks());
    }
}