package me.nagasonic.alkatraz.mobs.ai.task;

import me.nagasonic.alkatraz.api.ai.task.ActivitySpec;
import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.ai.task.Sensor;
import me.nagasonic.alkatraz.api.ai.task.TaskBrain;
import me.nagasonic.alkatraz.api.ai.task.TimedMemory;
import me.nagasonic.alkatraz.api.ai.task.TimeOfDayPredicate;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.mobs.goals.GoalFactory;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the {@code tasks:} YAML section of a brain config into a
 * {@link TaskBrain} plus a per-activity {@link GoalBrain}. Unknown sensor
 * types, schedules and memory references fail loudly — never silently.
 */
public final class TaskBrainFactory {

    private static final Map<String, MemoryKey<?>> KNOWN_KEYS = Map.of(
            "attack_target", MemoryKey.ATTACK_TARGET,
            "walk_target", MemoryKey.WALK_TARGET,
            "nearest_hostiles", MemoryKey.NEAREST_HOSTILES,
            "is_hurt", MemoryKey.IS_HURT);

    public record Result(TaskBrain brain, Map<String, GoalBrain> activityBrains) {}

    private record ParsedActivity(ActivitySpec activity, GoalBrain goals) {}

    private TaskBrainFactory() {}

    public static Result load(ConfigurationSection tasks) {
        if (tasks == null) throw new IllegalStateException("tasks section is missing");

        TaskBrain.Builder builder = TaskBrain.builder();
        for (String id : tasks.getStringList("core-memories")) {
            builder.addCoreMemory(memoryKey(id));
        }
        for (Map<?, ?> sensorMap : tasks.getMapList("core-sensors")) {
            builder.addCoreSensor(sensorFromMap(sensorMap));
        }
        builder.defaultActivity(tasks.getString("default-activity", "IDLE"));

        ConfigurationSection activities = tasks.getConfigurationSection("activities");
        if (activities == null) throw new IllegalStateException("tasks is missing an activities section");

        Map<String, GoalBrain> activityBrains = new HashMap<>();
        for (String activityId : activities.getKeys(false)) {
            ConfigurationSection section = activities.getConfigurationSection(activityId);
            if (section == null) continue;
            ParsedActivity parsed = activityFromSection(activityId, section);
            builder.addActivity(parsed.activity());
            activityBrains.put(activityId, parsed.goals());
        }
        return new Result(builder.build(), activityBrains);
    }

    private static ParsedActivity activityFromSection(String id, ConfigurationSection section) {
        int priority = section.getInt("priority", 0);

        List<Sensor> sensors = new ArrayList<>();
        for (Map<?, ?> sensorMap : section.getMapList("sensors")) {
            sensors.add(sensorFromMap(sensorMap));
        }

        GoalBrain.Builder goals = GoalBrain.builder();
        applySection(goals, section.getConfigurationSection("goals"), false);
        applySection(goals, section.getConfigurationSection("targets"), true);

        List<MemoryKey<?>> activation = keys(section, "activation-memories");
        List<MemoryKey<?>> deactivation = keys(section, "deactivation-memories");
        List<TimedMemory> onEnter = timedMemories(section.getMapList("memories-on-enter"));
        List<MemoryKey<?>> onExit = keys(section, "memories-on-exit");
        TimeOfDayPredicate schedule = scheduleOf(section.getString("schedule"));

        GoalBrain goalsBuilt = goals.build();
        ActivitySpec spec = new ActivitySpec(id, priority, sensors, goalsBuilt.entries(),
                activation, deactivation, onEnter, onExit, schedule);
        return new ParsedActivity(spec, goalsBuilt);
    }

    private static void applySection(GoalBrain.Builder builder, ConfigurationSection section, boolean target) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;
            if (target) entry.set("target", true);
            GoalFactory.applyFromConfig(builder, entry);
        }
    }

    private static Sensor sensorFromMap(Map<?, ?> sensorMap) {
        Object rawType = sensorMap.get("type");
        if (rawType == null) throw new IllegalStateException("a sensor is missing a type");
        String type = String.valueOf(rawType);
        return switch (type) {
            case "nearest_living" -> new Sensor.NearestLivingEntities(intValue(sensorMap, "radius", 16));
            case "nearest_players" -> new Sensor.NearestPlayers(intValue(sensorMap, "radius", 16));
            case "hurt_by" -> new Sensor.HurtBy();
            default -> throw new IllegalStateException("unknown sensor type: " + type);
        };
    }

    private static TimeOfDayPredicate scheduleOf(String value) {
        if (value == null || value.isBlank()) return TimeOfDayPredicate.DAY;
        try {
            return TimeOfDayPredicate.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("unknown schedule: " + value);
        }
    }

    private static List<MemoryKey<?>> keys(ConfigurationSection section, String path) {
        List<MemoryKey<?>> result = new ArrayList<>();
        for (String id : section.getStringList(path)) {
            result.add(memoryKey(id));
        }
        return result;
    }

    private static List<TimedMemory> timedMemories(List<Map<?, ?>> entries) {
        List<TimedMemory> result = new ArrayList<>();
        for (Map<?, ?> entry : entries) {
            Object rawKey = entry.get("key");
            if (rawKey == null) throw new IllegalStateException("memories-on-enter entry is missing a key");
            String keyId = String.valueOf(rawKey);
            Object value = entry.get("value");
            if (value == null) throw new IllegalStateException("memories-on-enter '" + keyId + "' is missing a value");
            long expiry = longValue(entry, "expiry-ticks", -1);
            result.add(new TimedMemory(memoryKey(keyId), value, expiry));
        }
        return result;
    }

    private static MemoryKey<?> memoryKey(String id) {
        MemoryKey<?> known = KNOWN_KEYS.get(id);
        return known != null ? known : new MemoryKey<Object>(id);
    }

    private static int intValue(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) return fallback;
        return number.intValue();
    }

    private static long longValue(Map<?, ?> map, String key, long fallback) {
        Object value = map.get(key);
        if (!(value instanceof Number number)) return fallback;
        return number.longValue();
    }
}