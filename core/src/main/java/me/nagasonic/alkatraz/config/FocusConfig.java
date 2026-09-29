package me.nagasonic.alkatraz.config;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Centralized static reader for the Focus system settings in config.yml.
 * Follows the {@link SpellbookConfig} pattern.
 *
 * <p>Values are resolved out of the YAML tree at most once per second and then
 * served from {@code volatile} fields, so hot paths (e.g. the per-move-tick
 * movement penalty) do not pay a config path traversal every call.</p>
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

    private static final long REFRESH_INTERVAL_MS = 1000L;

    private static volatile long lastRefresh = Long.MIN_VALUE;
    private static volatile boolean enabled = true;
    private static volatile double maxFocus = 250;
    private static volatile double regenBase = 2.0;
    private static volatile double regenGrowthFactor = 1.5;
    private static volatile double regenMax = 25.0;
    private static volatile double movementPenaltyPerSpeed = 0.02;
    private static volatile double movementMaxPenaltyRatio = 0.55;
    private static volatile double damageFactor = 1.0;
    private static volatile double lowHealthThreshold = 6.0;
    private static volatile double lowHealthMultiplier = 0.5;

    private static boolean stale() {
        return System.currentTimeMillis() - lastRefresh >= REFRESH_INTERVAL_MS;
    }

    /**
     * Re-reads every focus setting from config.yml. Called by the focus regen
     * task so live config edits are picked up within about a second.
     */
    public static void reload() {
        doReload();
    }

    private static synchronized void doReload() {
        enabled = getBoolean("focus.enabled", true);
        maxFocus = getDouble("focus.max_focus", 250);
        regenBase = getDouble("focus.regen_base", 2.0);
        regenGrowthFactor = getDouble("focus.regen_growth_factor", 1.5);
        regenMax = getDouble("focus.regen_max", 25.0);
        movementPenaltyPerSpeed = getDouble("focus.movement.penalty_per_speed", 0.02);
        movementMaxPenaltyRatio = getDouble("focus.movement.max_penalty_ratio", 0.55);
        damageFactor = getDouble("focus.damage.factor", 1.0);
        lowHealthThreshold = getDouble("focus.low_health.threshold", 6.0);
        lowHealthMultiplier = getDouble("focus.low_health.multiplier", 0.5);
        lastRefresh = System.currentTimeMillis();
    }

    private static void refreshIfStale() {
        if (!stale()) return;
        synchronized (FocusConfig.class) {
            if (stale()) doReload();
        }
    }

    public static boolean isEnabled() {
        refreshIfStale();
        return enabled;
    }

    public static double getMaxFocus() {
        refreshIfStale();
        return maxFocus;
    }

    public static double getRegenBase() {
        refreshIfStale();
        return regenBase;
    }

    public static double getRegenGrowthFactor() {
        refreshIfStale();
        return regenGrowthFactor;
    }

    public static double getRegenMax() {
        refreshIfStale();
        return regenMax;
    }

    public static double getMovementPenaltyPerSpeed() {
        refreshIfStale();
        return movementPenaltyPerSpeed;
    }

    public static double getMovementMaxPenaltyRatio() {
        refreshIfStale();
        return movementMaxPenaltyRatio;
    }

    public static double getDamageFactor() {
        refreshIfStale();
        return damageFactor;
    }

    public static double getLowHealthThreshold() {
        refreshIfStale();
        return lowHealthThreshold;
    }

    public static double getLowHealthMultiplier() {
        refreshIfStale();
        return lowHealthMultiplier;
    }
}
