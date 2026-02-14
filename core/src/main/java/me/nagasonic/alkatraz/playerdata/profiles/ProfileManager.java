package me.nagasonic.alkatraz.playerdata.profiles;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.logging.Level;

/**
 * Main manager for player profiles
 * Handles caching, loading, and saving of all profile types
 */
public class ProfileManager implements Listener {
    private static ProfileCache cache;
    private static boolean initialized = false;

    /**
     * Initializes the profile system
     * Should be called in onEnable()
     */
    public static void initialize() {
        if (initialized) {
            throw new IllegalStateException("ProfileManager is already initialized");
        }
        
        cache = new ProfileCache();
        initialized = true;
        
        // Register the event listener
        Bukkit.getPluginManager().registerEvents(new ProfileManager(), Alkatraz.getInstance());
        
        Alkatraz.getInstance().getLogger().info("ProfileManager initialized successfully");
    }

    /**
     * Shuts down the profile system
     * Saves all cached profiles
     * Should be called in onDisable()
     */
    public static void shutdown() {
        if (!initialized) return;
        
        saveAllCachedProfiles();
        cache.clear();
        initialized = false;
        
        Alkatraz.getInstance().getLogger().info("ProfileManager shut down - all profiles saved");
    }

    /**
     * Gets a profile for a player, loading from disk if needed
     * @param player The player
     * @param profileClass The profile class type
     * @return The profile (never null)
     */
    public static <T extends Profile> T getProfile(OfflinePlayer player, Class<T> profileClass) {
        return getProfile(player.getUniqueId(), profileClass);
    }

    /**
     * Gets a profile for a player by UUID, loading from disk if needed
     * @param uuid The player UUID
     * @param profileClass The profile class type
     * @return The profile (never null)
     */
    public static <T extends Profile> T getProfile(UUID uuid, Class<T> profileClass) {
        ensureInitialized();
        
        // Check cache first
        T profile = cache.getProfile(uuid, profileClass);
        if (profile != null) {
            return profile;
        }
        
        // Load from disk
        profile = ProfilePersistence.loadProfile(uuid, profileClass);
        cache.cacheProfile(uuid, profile);
        
        return profile;
    }

    /**
     * Gets all profiles for a player, loading from disk if needed
     * @param player The player
     * @return Map of all profiles for the player
     */
    public static Map<Class<? extends Profile>, Profile> getAllProfiles(OfflinePlayer player) {
        return getAllProfiles(player.getUniqueId());
    }

    /**
     * Gets all profiles for a player by UUID, loading from disk if needed
     * @param uuid The player UUID
     * @return Map of all profiles for the player
     */
    public static Map<Class<? extends Profile>, Profile> getAllProfiles(UUID uuid) {
        ensureInitialized();
        
        Map<Class<? extends Profile>, Profile> profiles = new HashMap<>();
        
        for (Class<? extends Profile> profileClass : ProfileRegistry.getRegisteredProfiles()) {
            profiles.put(profileClass, getProfile(uuid, profileClass));
        }
        
        return profiles;
    }

    /**
     * Saves a profile to disk
     * @param profile The profile to save
     */
    public static void saveProfile(Profile profile) {
        ensureInitialized();
        ProfilePersistence.saveProfile(profile);
    }

    /**
     * Saves all profiles for a player
     * @param player The player
     */
    public static void saveAllProfiles(OfflinePlayer player) {
        saveAllProfiles(player.getUniqueId());
    }

    /**
     * Saves all profiles for a player by UUID
     * @param uuid The player UUID
     */
    public static void saveAllProfiles(UUID uuid) {
        ensureInitialized();
        Collection<Profile> profiles = cache.getAllProfiles(uuid);
        
        if (profiles.isEmpty()) {
            // Not cached, load from disk to save (ensures all profiles are saved)
            profiles = ProfilePersistence.loadAllProfiles(uuid).values();
        }
        
        ProfilePersistence.saveAllProfiles(uuid, profiles);
    }

    /**
     * Loads all profiles for a player into cache
     * @param player The player
     */
    public static void loadPlayer(OfflinePlayer player) {
        loadPlayer(player.getUniqueId());
    }

    /**
     * Loads all profiles for a player into cache by UUID
     * @param uuid The player UUID
     */
    public static void loadPlayer(UUID uuid) {
        ensureInitialized();
        
        for (Class<? extends Profile> profileClass : ProfileRegistry.getRegisteredProfiles()) {
            Profile profile = ProfilePersistence.loadProfile(uuid, profileClass);
            cache.cacheProfile(uuid, profile);
        }
    }

    /**
     * Unloads a player's profiles from cache (saves first)
     * @param player The player
     */
    public static void unloadPlayer(OfflinePlayer player) {
        unloadPlayer(player.getUniqueId());
    }

    /**
     * Unloads a player's profiles from cache by UUID (saves first)
     * @param uuid The player UUID
     */
    public static void unloadPlayer(UUID uuid) {
        ensureInitialized();
        
        // Save all cached profiles
        Collection<Profile> profiles = cache.getAllProfiles(uuid);
        if (!profiles.isEmpty()) {
            ProfilePersistence.saveAllProfiles(uuid, profiles);
        }
        
        // Remove from cache
        cache.removePlayer(uuid);
    }

    /**
     * Checks if a player is currently loaded in cache
     * @param player The player
     * @return true if player has cached profiles
     */
    public static boolean isPlayerLoaded(OfflinePlayer player) {
        return isPlayerLoaded(player.getUniqueId());
    }

    /**
     * Checks if a player is currently loaded in cache by UUID
     * @param uuid The player UUID
     * @return true if player has cached profiles
     */
    public static boolean isPlayerLoaded(UUID uuid) {
        ensureInitialized();
        return cache.hasPlayer(uuid);
    }

    /**
     * Forces a reload of a player's profiles from disk
     * @param player The player
     */
    public static void reloadPlayer(OfflinePlayer player) {
        reloadPlayer(player.getUniqueId());
    }

    /**
     * Forces a reload of a player's profiles from disk by UUID
     * @param uuid The player UUID
     */
    public static void reloadPlayer(UUID uuid) {
        ensureInitialized();
        
        // Remove from cache
        cache.removePlayer(uuid);
        
        // Load fresh from disk
        loadPlayer(uuid);
    }

    /**
     * Saves all currently cached profiles
     */
    public static void saveAllCachedProfiles() {
        ensureInitialized();
        
        for (UUID uuid : cache.getCachedPlayers()) {
            Collection<Profile> profiles = cache.getAllProfiles(uuid);
            ProfilePersistence.saveAllProfiles(uuid, profiles);
        }
        
        Alkatraz.getInstance().getLogger().info("Saved " + cache.size() + " player profiles");
    }

    /**
     * Gets the number of players currently loaded in cache
     * @return Number of cached players
     */
    public static int getCachedPlayerCount() {
        ensureInitialized();
        return cache.size();
    }

    /**
     * Clears all cached profiles without saving
     * WARNING: Use with caution - unsaved data will be lost
     */
    public static void clearCache() {
        ensureInitialized();
        cache.clear();
    }

    /**
     * Gets the ProfileCache instance.
     * @return ProfileCache instance
     */
    public static ProfileCache getCache() {
        return cache;
    }

    // Event Handlers

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        try {
            loadPlayer(player);
            Alkatraz.getInstance().getLogger().log(Level.FINE, 
                "Loaded profiles for player: " + player.getName());
        } catch (Exception e) {
            Alkatraz.getInstance().getLogger().log(Level.SEVERE, 
                "Failed to load profiles for player: " + player.getName(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        try {
            unloadPlayer(player);
            Alkatraz.getInstance().getLogger().log(Level.FINE, 
                "Saved and unloaded profiles for player: " + player.getName());
        } catch (Exception e) {
            Alkatraz.getInstance().getLogger().log(Level.SEVERE, 
                "Failed to save profiles for player: " + player.getName(), e);
        }
    }

    // Helper Methods

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("ProfileManager is not initialized. Call initialize() first.");
        }
    }
}
