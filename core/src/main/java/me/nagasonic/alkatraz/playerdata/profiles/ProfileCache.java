package me.nagasonic.alkatraz.playerdata.profiles;

import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for player profiles
 * Uses UUID as key for thread-safe storage
 */
public class ProfileCache {
    private final Map<UUID, Map<Class<? extends Profile>, Profile>> cache;
    
    public ProfileCache() {
        this.cache = new ConcurrentHashMap<>();
    }

    /**
     * Gets a profile from cache
     * @param player The player
     * @param profileClass The profile class type
     * @return The profile, or null if not cached
     */
    public <T extends Profile> T getProfile(OfflinePlayer player, Class<T> profileClass) {
        return getProfile(player.getUniqueId(), profileClass);
    }

    /**
     * Gets a profile from cache by UUID
     * @param uuid The player UUID
     * @param profileClass The profile class type
     * @return The profile, or null if not cached
     */
    @SuppressWarnings("unchecked")
    public <T extends Profile> T getProfile(UUID uuid, Class<T> profileClass) {
        Map<Class<? extends Profile>, Profile> playerProfiles = cache.get(uuid);
        if (playerProfiles == null) return null;
        return (T) playerProfiles.get(profileClass);
    }

    /**
     * Caches a profile
     * @param player The player
     * @param profile The profile to cache
     */
    public void cacheProfile(OfflinePlayer player, Profile profile) {
        cacheProfile(player.getUniqueId(), profile);
    }

    /**
     * Caches a profile by UUID
     * @param uuid The player UUID
     * @param profile The profile to cache
     */
    public void cacheProfile(UUID uuid, Profile profile) {
        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
             .put(profile.getClass(), profile);
    }

    /**
     * Removes all profiles for a player from cache
     * @param player The player
     * @return Map of removed profiles, or null if player wasn't cached
     */
    public Map<Class<? extends Profile>, Profile> removePlayer(OfflinePlayer player) {
        return removePlayer(player.getUniqueId());
    }

    /**
     * Removes all profiles for a player from cache by UUID
     * @param uuid The player UUID
     * @return Map of removed profiles, or null if player wasn't cached
     */
    public Map<Class<? extends Profile>, Profile> removePlayer(UUID uuid) {
        return cache.remove(uuid);
    }

    /**
     * Removes a specific profile type for a player
     * @param player The player
     * @param profileClass The profile class to remove
     * @return The removed profile, or null if not cached
     */
    public <T extends Profile> T removeProfile(OfflinePlayer player, Class<T> profileClass) {
        return removeProfile(player.getUniqueId(), profileClass);
    }

    /**
     * Removes a specific profile type for a player by UUID
     * @param uuid The player UUID
     * @param profileClass The profile class to remove
     * @return The removed profile, or null if not cached
     */
    @SuppressWarnings("unchecked")
    public <T extends Profile> T removeProfile(UUID uuid, Class<T> profileClass) {
        Map<Class<? extends Profile>, Profile> playerProfiles = cache.get(uuid);
        if (playerProfiles == null) return null;
        return (T) playerProfiles.remove(profileClass);
    }

    /**
     * Checks if a player has any cached profiles
     * @param player The player
     * @return true if player has cached profiles
     */
    public boolean hasPlayer(OfflinePlayer player) {
        return hasPlayer(player.getUniqueId());
    }

    /**
     * Checks if a player has any cached profiles by UUID
     * @param uuid The player UUID
     * @return true if player has cached profiles
     */
    public boolean hasPlayer(UUID uuid) {
        return cache.containsKey(uuid);
    }

    /**
     * Checks if a specific profile is cached
     * @param player The player
     * @param profileClass The profile class
     * @return true if profile is cached
     */
    public boolean hasProfile(OfflinePlayer player, Class<? extends Profile> profileClass) {
        return hasProfile(player.getUniqueId(), profileClass);
    }

    /**
     * Checks if a specific profile is cached by UUID
     * @param uuid The player UUID
     * @param profileClass The profile class
     * @return true if profile is cached
     */
    public boolean hasProfile(UUID uuid, Class<? extends Profile> profileClass) {
        Map<Class<? extends Profile>, Profile> playerProfiles = cache.get(uuid);
        return playerProfiles != null && playerProfiles.containsKey(profileClass);
    }

    /**
     * Gets all profiles for a player
     * @param player The player
     * @return Collection of all cached profiles for the player
     */
    public Collection<Profile> getAllProfiles(OfflinePlayer player) {
        return getAllProfiles(player.getUniqueId());
    }

    /**
     * Gets all profiles for a player by UUID
     * @param uuid The player UUID
     * @return Collection of all cached profiles for the player
     */
    public Collection<Profile> getAllProfiles(UUID uuid) {
        Map<Class<? extends Profile>, Profile> playerProfiles = cache.get(uuid);
        return playerProfiles != null ? new ArrayList<>(playerProfiles.values()) : Collections.emptyList();
    }

    /**
     * Gets all cached player UUIDs
     * @return Set of all cached player UUIDs
     */
    public Set<UUID> getCachedPlayers() {
        return new HashSet<>(cache.keySet());
    }

    /**
     * Clears the entire cache
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Gets the number of cached players
     * @return Number of players in cache
     */
    public int size() {
        return cache.size();
    }
}
