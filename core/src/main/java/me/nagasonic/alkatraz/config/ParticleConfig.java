package me.nagasonic.alkatraz.config;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Centralized static reader for the particle performance settings in config.yml.
 * Follows the {@link FocusConfig} pattern.
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

    public static boolean isEnabled() {
        return getBoolean("particles.enabled", true);
    }

    public static double getDenseRadius() {
        return getDouble("particles.dense_radius", 12.0);
    }

    public static double getMaxRadius() {
        return getDouble("particles.max_radius", 32.0);
    }

    public static double getMinScale() {
        return getDouble("particles.min_scale", 0.25);
    }

    public static boolean isCasterFullDensity() {
        return getBoolean("particles.caster_full_density", true);
    }
}