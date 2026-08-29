package me.nagasonic.alkatraz.config;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Centralized static reader for the Focus system settings in config.yml.
 * Follows the {@link SpellbookConfig} pattern.
 */
public class FocusConfig {

    private FocusConfig() {}

    private static YamlConfiguration cfg() {
        return Alkatraz.getPluginConfig();
    }

    private static boolean getBoolean(String path, boolean def) {
        return cfg().getBoolean(path, def);
    }

    private static double getDouble(String path, double def) {
        return cfg().getDouble(path, def);
    }

    public static boolean isEnabled() {
        return getBoolean("focus.enabled", true);
    }

    public static double getMaxFocus() {
        return getDouble("focus.max_focus", 250);
    }

    public static double getRegenBase() {
        return getDouble("focus.regen_base", 2.0);
    }

    public static double getRegenGrowthFactor() {
        return getDouble("focus.regen_growth_factor", 1.5);
    }

    public static double getRegenMax() {
        return getDouble("focus.regen_max", 25.0);
    }

    public static double getMovementPenaltyPerSpeed() {
        return getDouble("focus.movement.penalty_per_speed", 0.02);
    }

    public static double getMovementMaxPenaltyRatio() {
        return getDouble("focus.movement.max_penalty_ratio", 0.55);
    }

    public static double getDamageFactor() {
        return getDouble("focus.damage.factor", 1.0);
    }

    public static double getLowHealthThreshold() {
        return getDouble("focus.low_health.threshold", 6.0);
    }

    public static double getLowHealthMultiplier() {
        return getDouble("focus.low_health.multiplier", 0.5);
    }
}