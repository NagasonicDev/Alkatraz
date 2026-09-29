package me.nagasonic.alkatraz.config;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Centralized static reader for the particle performance settings in config.yml.
 * Follows the {@link FocusConfig} pattern.
 *
 * <p>Values are resolved out of the YAML tree at most once per second and then
 * served from {@code volatile} fields, since they are read several times per
 * particle dispatch.</p>
 */
public final class ParticleConfig {

    private ParticleConfig() {}

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
    private static volatile double denseRadius = 12.0;
    private static volatile double maxRadius = 32.0;
    private static volatile double minScale = 0.25;
    private static volatile boolean casterFullDensity = true;

    private static boolean stale() {
        return System.currentTimeMillis() - lastRefresh >= REFRESH_INTERVAL_MS;
    }

    /**
     * Re-reads every particle setting from config.yml.
     */
    public static void reload() {
        doReload();
    }

    private static synchronized void doReload() {
        enabled = getBoolean("particles.enabled", true);
        denseRadius = getDouble("particles.dense_radius", 12.0);
        maxRadius = getDouble("particles.max_radius", 32.0);
        minScale = getDouble("particles.min_scale", 0.25);
        casterFullDensity = getBoolean("particles.caster_full_density", true);
        lastRefresh = System.currentTimeMillis();
    }

    private static void refreshIfStale() {
        if (!stale()) return;
        synchronized (ParticleConfig.class) {
            if (stale()) doReload();
        }
    }

    public static boolean isEnabled() {
        refreshIfStale();
        return enabled;
    }

    public static double getDenseRadius() {
        refreshIfStale();
        return denseRadius;
    }

    public static double getMaxRadius() {
        refreshIfStale();
        return maxRadius;
    }

    public static double getMinScale() {
        refreshIfStale();
        return minScale;
    }

    public static boolean isCasterFullDensity() {
        refreshIfStale();
        return casterFullDensity;
    }
}
