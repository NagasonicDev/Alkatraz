package me.nagasonic.alkatraz.mobs;
import java.util.concurrent.ConcurrentHashMap;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.ai.task.TaskBrain;
import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.api.mobs.GoalBrain;
import me.nagasonic.alkatraz.config.Config;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.mobs.ai.task.TaskBrainFactory;
import me.nagasonic.alkatraz.mobs.goals.GoalFactory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads each magic mob's declarative AI from {@code brains/<id>.yml} into a
 * cached {@link GoalBrain}, plus the display-name / melee-range / wand values
 * the NMS base classes read at spawn time.
 *
 * <pre>
 *   MagicBrains.brain(MagicEntityType.ZOMBIE_MAGE);  // AI goal list
 *   MagicBrains.meleeRange(MagicEntityType.ZOMBIE_MAGE); // 3.0
 * </pre>
 */
public final class MagicBrains {

    private static final Map<String, GoalBrain> brainCache = new ConcurrentHashMap<>();
    private static final Map<String, String> displayNameCache = new HashMap<>();
    private static final Map<String, Double> meleeRangeCache = new HashMap<>();
    private static final Map<String, String> wandCache = new HashMap<>();
    private static final Map<String, TaskBrain> taskBrainCache = new HashMap<>();
    private static final Map<String, Map<String, GoalBrain>> activityBrainCache = new HashMap<>();

    public static final String BRAIN_KEY = "alkatraz_brain";

    /** Extra flat brains preloaded alongside the {@link MagicEntityType} brains. */
    public static final List<String> SUMMON_BRAIN_IDS =
            List.of("summoned_zombie", "summoned_knight", "summoned_horse");

    private MagicBrains() {}

    /** (Re)loads every magic mob brain from disk. Call once at startup and on reload. */
    public static void registerAll() {
        brainCache.clear();
        displayNameCache.clear();
        meleeRangeCache.clear();
        wandCache.clear();
        taskBrainCache.clear();
        activityBrainCache.clear();
        for (MagicEntityType type : MagicEntityType.values()) {
            reload(type.getId());
        }
        for (String id : SUMMON_BRAIN_IDS) {
            reload(id);
        }
        Alkatraz.logInfo("Loaded " + (MagicEntityType.values().length + SUMMON_BRAIN_IDS.size())
                + " magic mob brain(s)");
    }

    /** (Re)loads one brain (and its display-name / melee-range / wand) from disk. */
    public static void reload(String id) {
        Config cfg = ConfigManager.getConfig("brains/" + id + ".yml");
        var root = cfg.get();

        displayNameCache.put(id, root.getString("display-name", "&f" + id));
        meleeRangeCache.put(id, root.getDouble("melee-range", 0));
        String wand = root.getString("wand");
        wandCache.put(id, wand);

        GoalBrain.Builder builder = GoalBrain.builder();
        applySection(builder, root, "goals", false);
        applySection(builder, root, "targets", true);
        brainCache.put(id, builder.build());

        ConfigurationSection tasks = root.getConfigurationSection("tasks");
        if (tasks != null) {
            TaskBrainFactory.Result result = TaskBrainFactory.load(tasks);
            taskBrainCache.put(id, result.brain());
            activityBrainCache.put(id, result.activityBrains());
        } else {
            taskBrainCache.remove(id);
            activityBrainCache.remove(id);
        }

        Alkatraz.logHigh("Registered brain '" + id + "' (wand=" + wand + ", melee-range="
                + meleeRangeCache.get(id) + ")");
    }

    private static void applySection(GoalBrain.Builder builder, ConfigurationSection root, String path, boolean target) {
        Object raw = root.get(path);
        if (raw instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) continue;
                if (target) entry.set("target", true);
                GoalFactory.applyFromConfig(builder, entry);
            }
        } else if (raw instanceof List<?> list) {
            for (Object item : list) {
                ConfigurationSection entry = toEntry(item);
                if (entry == null) continue;
                if (target) entry.set("target", true);
                GoalFactory.applyFromConfig(builder, entry);
            }
        }
    }

    private static ConfigurationSection toEntry(Object item) {
        if (item instanceof ConfigurationSection section) return section;
        if (item instanceof Map<?, ?> map) {
            YamlConfiguration entry = new YamlConfiguration();
            for (Map.Entry<?, ?> pair : map.entrySet()) {
                entry.set(String.valueOf(pair.getKey()), pair.getValue());
            }
            return entry;
        }
        return null;
    }

    public static GoalBrain brain(MagicEntityType type) {
        return brain(type.getId());
    }

    /** Returns the default activity's brain for a task brain, else the flat cached brain, or {@code null} if never loaded. */
    public static GoalBrain brain(String id) {
        GoalBrain defaultActivity = defaultActivityBrain(id);
        if (defaultActivity != null) return defaultActivity;
        return brainCache.get(id);
    }

    /** Whether {@code id} loaded a {@code tasks:} section. */
    public static boolean isTaskBrain(String id) {
        return taskBrainCache.containsKey(id);
    }

    public static TaskBrain taskBrain(String id) {
        return taskBrainCache.get(id);
    }

    public static Map<String, GoalBrain> activityBrains(String id) {
        return activityBrainCache.get(id);
    }

    public static GoalBrain defaultActivityBrain(String id) {
        TaskBrain taskBrain = taskBrainCache.get(id);
        if (taskBrain == null) return null;
        Map<String, GoalBrain> brains = activityBrainCache.get(id);
        if (brains == null) return null;
        GoalBrain goalBrain = brains.get(taskBrain.defaultActivity());
        return goalBrain != null ? goalBrain : GoalBrain.builder().build();
    }

    public static String displayName(MagicEntityType type) {
        return displayNameCache.getOrDefault(type.getId(), "&f" + type.getId());
    }

    public static double meleeRange(MagicEntityType type) {
        return meleeRangeCache.getOrDefault(type.getId(), 0.0);
    }

    /** Returns the wand item key, or {@code null} if the mob should spawn empty-handed. */
    public static String wand(MagicEntityType type) {
        return wandCache.get(type.getId());
    }
}