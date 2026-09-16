package me.nagasonic.alkatraz.mobs;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.mobs.MagicEntityType;
import me.nagasonic.alkatraz.api.mobs.MobBrain;
import me.nagasonic.alkatraz.config.Config;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.mobs.goals.GoalFactory;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads each magic mob's declarative AI from {@code brains/<id>.yml} into a
 * cached {@link MobBrain}, plus the display-name / melee-range / wand values
 * the NMS base classes read at spawn time.
 *
 * <pre>
 *   MagicBrains.brain(MagicEntityType.ZOMBIE_MAGE);  // AI goal list
 *   MagicBrains.meleeRange(MagicEntityType.ZOMBIE_MAGE); // 3.0
 * </pre>
 */
public final class MagicBrains {

    private static final Map<String, MobBrain> brainCache = new HashMap<>();
    private static final Map<String, String> displayNameCache = new HashMap<>();
    private static final Map<String, Double> meleeRangeCache = new HashMap<>();
    private static final Map<String, String> wandCache = new HashMap<>();

    private MagicBrains() {}

    /** (Re)loads every magic mob brain from disk. Call once at startup and on reload. */
    public static void registerAll() {
        brainCache.clear();
        displayNameCache.clear();
        meleeRangeCache.clear();
        wandCache.clear();
        for (MagicEntityType type : MagicEntityType.values()) {
            register(type);
        }
        Alkatraz.logInfo("Loaded " + MagicEntityType.values().length + " magic mob brain(s)");
    }

    private static void register(MagicEntityType type) {
        Config cfg = ConfigManager.getConfig("brains/" + type.getId() + ".yml");
        var root = cfg.get();

        displayNameCache.put(type.getId(), root.getString("display-name", "&f" + type.getId()));
        meleeRangeCache.put(type.getId(), root.getDouble("melee-range", 0));
        String wand = root.getString("wand");
        wandCache.put(type.getId(), wand);

        MobBrain.Builder builder = MobBrain.builder();
        applySection(builder, root.getConfigurationSection("goals"), false);
        applySection(builder, root.getConfigurationSection("targets"), true);
        brainCache.put(type.getId(), builder.build());

        Alkatraz.logHigh("Registered brain '" + type.getId() + "' (wand=" + wand + ", melee-range="
                + meleeRangeCache.get(type.getId()) + ")");
    }

    private static void applySection(MobBrain.Builder builder, ConfigurationSection section, boolean target) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;
            if (target) entry.set("target", true);
            GoalFactory.applyFromConfig(builder, entry);
        }
    }

    public static MobBrain brain(MagicEntityType type) {
        return brainCache.get(type.getId());
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