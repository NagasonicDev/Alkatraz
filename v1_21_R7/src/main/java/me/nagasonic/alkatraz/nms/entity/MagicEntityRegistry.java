package me.nagasonic.alkatraz.nms.entity;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.Config;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.nms.entity.implementation.MagicZombie;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * MagicEntityRegistry
 *
 * Caches {@link MagicProfile} instances so each mob's yml is only parsed once
 * per server lifetime. Each {@link MagicEntity} subclass calls
 * {@link #getProfile(String)} from its constructor to retrieve its profile.
 *
 * Register a key for every magic mob type in {@link #registerAll()}, then call
 * that once from your plugin's onEnable.
 *
 * Spawning is handled directly by each subclass via a static {@code spawn()}
 * method — see {@link MagicZombie#spawn(org.bukkit.Location)} for the pattern.
 */
public final class MagicEntityRegistry {

    private MagicEntityRegistry() {}

    private static final Map<String, MagicProfile> profileCache = new HashMap<>();

    // -------------------------------------------------------------------------
    // Plugin startup
    // -------------------------------------------------------------------------

    /** Call once from {@code Alkatraz#onEnable} after the config system is ready. */
    public static void registerAll() {
        Alkatraz.logInfo("Registering MagicEntityRegistry");
        register("magic_zombie",   "mobs/zombie.yml");
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static void register(String key, String ymlPath) {
        Config cfgOpt = ConfigManager.getConfig(ymlPath);
        profileCache.put(key, new MagicProfile(cfgOpt.get()));
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Returns the cached profile for the given key, or empty if the key was
     * never registered or its yml failed to load.
     */
    public static Optional<MagicProfile> getProfile(String key) {
        return Optional.ofNullable(profileCache.get(key));
    }
}
