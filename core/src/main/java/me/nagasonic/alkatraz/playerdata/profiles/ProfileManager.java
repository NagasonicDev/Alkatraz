package me.nagasonic.alkatraz.playerdata.profiles;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.playerdata.Profile;
import me.nagasonic.alkatraz.config.VerbosityLevel;
import me.nagasonic.alkatraz.events.SpellDiscoveredEvent;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentStorage;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * Main manager for player profiles
 * Handles caching, loading, and saving of all profile types
 */
public class ProfileManager implements Listener {
    private static ProfileCache cache;
    private static boolean initialized = false;
    private static BukkitTask manaRegenTask;
    private static BukkitTask autosaveTask;
    private static final java.util.List<BukkitTask> autosaveSteps = new java.util.ArrayList<>();
    private static final Map<UUID, Long> flushGenerations = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> flushBlocked = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Object> loadLocks = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong flushGenCounter = new java.util.concurrent.atomic.AtomicLong();
    // Serializes periodic/synchronous saves so shutdown cannot overlap an in-flight autosave.
    private static final ReentrantLock saveLock = new ReentrantLock();
    private static volatile boolean shuttingDown = false;
    private static volatile boolean reloading = false;

    /** One shared regeneration pass per second for every online player. */
    private static final long MANA_REGEN_PERIOD_TICKS = 20L;
    /** Offset from the focus regen task so both do not fire on the same tick. */
    private static final long MANA_REGEN_INITIAL_DELAY_TICKS = 10L;
    /** Persist cached profiles periodically so crashes do not lose long sessions. */
    private static final long AUTOSAVE_PERIOD_TICKS = 2400L;

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
        
        startManaRegenTask();
        startAutosaveTask();

        Alkatraz.getInstance().getLogger().info("ProfileManager initialized successfully");
    }

    private static void startManaRegenTask() {
        manaRegenTask = Bukkit.getScheduler().runTaskTimer(Alkatraz.getInstance(), () -> {
            // Periodic sweep of expired timed modifiers/effects so empty buckets do not linger.
            me.nagasonic.alkatraz.spells.modifier.ModifierManager.pruneAll();
            for (Player player : Bukkit.getOnlinePlayers()) {
                MagicProfile profile = getProfile(player.getUniqueId(), MagicProfile.class);
                if (profile == null) continue;
                profile.regenMana();
            }
        }, MANA_REGEN_INITIAL_DELAY_TICKS, MANA_REGEN_PERIOD_TICKS);
    }

    /** Number of players snapshotted per main-thread tick during an autosave pass. */
    private static final int AUTOSAVE_CHUNK_SIZE = 8;

    private static void startAutosaveTask() {
        autosaveTask = Bukkit.getScheduler().runTaskTimer(Alkatraz.getInstance(), () -> {
            if (!initialized || shuttingDown || reloading || cache == null) return;
            // Spread the main-thread snapshot work across ticks: only AUTOSAVE_CHUNK_SIZE
            // players are copied per tick, preserving copy-before-write semantics while
            // keeping each sweep off the hot path.
            final java.util.List<UUID> players = new java.util.ArrayList<>(cache.getCachedPlayers());
            if (players.isEmpty()) return;
            final java.util.Map<UUID, java.util.List<Profile>> snapshots = new java.util.HashMap<>();
            final int[] cursor = { 0 };
            Runnable[] step = new Runnable[1];
            step[0] = () -> {
                if (!initialized || shuttingDown || reloading) return;
                int end = Math.min(cursor[0] + AUTOSAVE_CHUNK_SIZE, players.size());
                for (; cursor[0] < end; cursor[0]++) {
                    UUID uuid = players.get(cursor[0]);
                    snapshots.put(uuid, snapshotProfiles(uuid, cache.getAllProfiles(uuid)));
                }
                if (cursor[0] < players.size()) {
                    synchronized (autosaveSteps) {
                        autosaveSteps.add(Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), step[0], 1L));
                    }
                    return;
                }
                // All snapshots taken on the main thread; hand them to a writer.
                Bukkit.getScheduler().runTaskAsynchronously(Alkatraz.getInstance(), () -> {
                    if (!initialized || shuttingDown || reloading) return;
                    try {
                        saveLock.lock();
                        if (shuttingDown || reloading) return;
                        int saved = 0;
                        for (java.util.Map.Entry<UUID, java.util.List<Profile>> entry : snapshots.entrySet()) {
                            try {
                                ProfilePersistence.saveAllProfiles(entry.getKey(), entry.getValue());
                                saved++;
                            } catch (Exception e) {
                                Alkatraz.getInstance().getLogger().log(Level.WARNING,
                                        "Periodic profile save failed for " + entry.getKey(), e);
                            }
                        }
                        Alkatraz.getInstance().getLogger().info("Saved " + saved + " player profiles");
                    } catch (Exception e) {
                        Alkatraz.getInstance().getLogger().log(Level.SEVERE, "Periodic profile save failed", e);
                    } finally {
                        saveLock.unlock();
                    }
                });
            };
            step[0].run();
        }, AUTOSAVE_PERIOD_TICKS, AUTOSAVE_PERIOD_TICKS);
    }

    /**
     * Shuts down the profile system
     * Saves all cached profiles
     * Should be called in onDisable()
     */
    public static void shutdown() {
        if (!initialized) return;

        shuttingDown = true;
        if (manaRegenTask != null) {
            manaRegenTask.cancel();
            manaRegenTask = null;
        }
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        synchronized (autosaveSteps) {
            for (BukkitTask t : autosaveSteps) {
                try { if (t != null) t.cancel(); } catch (Exception ignored) { }
            }
            autosaveSteps.clear();
        }

        // Wait for any in-flight periodic save to finish before writing synchronously.
        saveLock.lock();
        try {
            saveAllCachedProfiles();
        } finally {
            saveLock.unlock();
        }
        cache.clear();
        initialized = false;
        shuttingDown = false;

        Alkatraz.getInstance().getLogger().info("ProfileManager shut down - all profiles saved");
    }

    public static void beginReload() {
        reloading = true;
        if (autosaveTask != null) {
            try { autosaveTask.cancel(); } catch (Exception ignored) { }
            autosaveTask = null;
        }
        synchronized (autosaveSteps) {
            for (BukkitTask t : autosaveSteps) {
                try { if (t != null) t.cancel(); } catch (Exception ignored) { }
            }
            autosaveSteps.clear();
        }
    }

    public static void endReload() {
        reloading = false;
        if (initialized && !shuttingDown) {
            startAutosaveTask();
        }
    }

    public static boolean isReloading() { return reloading; }

    public static boolean isShuttingDown() { return shuttingDown; }

    /**
     * Gets a profile for a player, loading from disk if needed
     * @param player The player
     * @param profileClass The profile class type
     * @return The cached profile, or null if the player is not online
     */
    public static <T extends Profile> T getProfile(OfflinePlayer player, Class<T> profileClass) {
        return getProfile(player.getUniqueId(), profileClass);
    }

    /**
     * Gets a profile for a player by UUID, loading from disk if needed.
     *
     * <p>Never performs disk I/O for a player that is not online. Hot call
     * sites (movement, combat, spell casts) can query entities that have no
     * loaded profile, and a cache miss there must not trigger a blocking YAML
     * parse. Use {@link #getOrLoadProfile(UUID, Class)} on admin/command paths
     * that legitimately target offline players.
     *
     * @param uuid The player UUID
     * @param profileClass The profile class type
     * @return The cached profile, or null if the player is not online
     */
    public static <T extends Profile> T getProfile(UUID uuid, Class<T> profileClass) {
        ensureInitialized();

        // Check cache first (fast path, no locking needed)
        T profile = cache.getProfile(uuid, profileClass);
        if (profile != null) {
            return profile;
        }

        if (Bukkit.getPlayer(uuid) == null) {
            if (Alkatraz.isLoggable(VerbosityLevel.DEBUG)) {
                Alkatraz.logDebug("Profile cache miss for non-online player " + uuid
                        + " (" + profileClass.getSimpleName() + ") - skipped disk load");
            }
            return null;
        }

        // Serialise the miss path per UUID so two async callers cannot each
        // build a different profile object and have one overwrite the other.
        Object lock = loadLocks.computeIfAbsent(uuid, k -> new Object());
        try {
            synchronized (lock) {
                profile = cache.getProfile(uuid, profileClass);
                if (profile != null) {
                    return profile;
                }
                if (Bukkit.getPlayer(uuid) == null) {
                    if (Alkatraz.isLoggable(VerbosityLevel.DEBUG)) {
                        Alkatraz.logDebug("Profile cache miss for non-online player " + uuid
                                + " (" + profileClass.getSimpleName() + ") - skipped disk load");
                    }
                    return null;
                }

                // Load from disk
                if (Alkatraz.isLoggable(VerbosityLevel.HIGH)) {
                    Alkatraz.logHigh("Loading profile " + profileClass.getSimpleName() + " for " + uuid);
                }
                profile = ProfilePersistence.loadProfile(uuid, profileClass);
                cache.cacheProfile(uuid, profile);

                return profile;
            }
        } finally {
            // Evict the per-UUID lock after the load completes so admin inspection
            // of offline players (and transient lookups) do not leak lock objects.
            loadLocks.remove(uuid, lock);
        }
    }

    /**
     * Gets a profile for a player by UUID, falling back to a blocking disk load
     * even when the player is offline.
     *
     * <p>Intended for admin/command paths that operate on offline players. Do
     * not call this from per-tick or per-event hot paths.
     *
     * @param uuid The player UUID
     * @param profileClass The profile class type
     * @return The profile (never null)
     */
    public static <T extends Profile> T getOrLoadProfile(UUID uuid, Class<T> profileClass) {
        ensureInitialized();

        T profile = cache.getProfile(uuid, profileClass);
        if (profile != null) {
            return profile;
        }

        Object lock = loadLocks.computeIfAbsent(uuid, k -> new Object());
        try {
            synchronized (lock) {
                profile = cache.getProfile(uuid, profileClass);
                if (profile != null) {
                    return profile;
                }

                if (Alkatraz.isLoggable(VerbosityLevel.HIGH)) {
                    Alkatraz.logHigh("Loading profile " + profileClass.getSimpleName() + " for " + uuid);
                }
                profile = ProfilePersistence.loadProfile(uuid, profileClass);
                cache.cacheProfile(uuid, profile);

                return profile;
            }
        } finally {
            // Evict the per-UUID lock after the load completes so admin inspection
            // of offline players does not leak lock objects for the process lifetime.
            loadLocks.remove(uuid, lock);
        }
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
            Profile profile = getProfile(uuid, profileClass);
            if (profile != null) {
                profiles.put(profileClass, profile);
            }
        }
        
        return profiles;
    }

    /**
     * Saves a profile to disk
     * @param profile The profile to save
     */
    public static void saveProfile(Profile profile) {
        ensureInitialized();
        saveLock.lock();
        try {
            ProfilePersistence.saveProfile(profile);
        } finally {
            saveLock.unlock();
        }
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
        saveLock.lock();
        try {
            Collection<Profile> profiles = cache.getAllProfiles(uuid);

            if (profiles.isEmpty()) {
                // Not cached, load from disk to save (ensures all profiles are saved)
                profiles = ProfilePersistence.loadAllProfiles(uuid).values();
            }

            ProfilePersistence.saveAllProfiles(uuid, profiles);
        } finally {
            saveLock.unlock();
        }
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
        saveLock.lock();
        try {
            // Save all cached profiles
            Collection<Profile> profiles = cache.getAllProfiles(uuid);
            if (!profiles.isEmpty()) {
                ProfilePersistence.saveAllProfiles(uuid, profiles);
            }

            // Drop mastery boss bars and their delayed-cancel tasks before discarding
            // cached profiles, so quit under load does not leak bars or tasks.
            for (Profile p : profiles) {
                if (!(p instanceof me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile mp)) continue;
                for (org.bukkit.boss.BossBar bar : new java.util.ArrayList<>(mp.getMasteryBars().values())) {
                    try { bar.removeAll(); } catch (Exception ignored) { }
                }
                for (int taskId : mp.getMasteryBarTaskIds().values()) {
                    try { org.bukkit.Bukkit.getScheduler().cancelTask(taskId); } catch (Exception ignored) { }
                }
                mp.getMasteryBars().clear();
                mp.getMasteryBarTaskIds().clear();
            }

        } finally {
            // Always evict from cache even if save/cleanup threw, so a failed quit
            // cannot leave a stale row cached.
            try { cache.removePlayer(uuid); } catch (Exception ignored) { }
            loadLocks.remove(uuid);
            saveLock.unlock();
        }
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
        saveLock.lock();
        try {
            // Save any cached profiles before discarding them
            Collection<Profile> cached = cache.getAllProfiles(uuid);
            if (!cached.isEmpty()) {
                ProfilePersistence.saveAllProfiles(uuid, cached);
            }
            // Remove from cache before loading fresh from disk
            cache.removePlayer(uuid);

            // Load fresh from disk
            loadPlayer(uuid);
        } finally {
            saveLock.unlock();
        }
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
        UUID joinUuid = player.getUniqueId();
        flushBlocked.remove(joinUuid);

        try {
            loadPlayer(player);
            Alkatraz.getInstance().getLogger().log(Level.FINE, 
                "Loaded profiles for player: " + player.getName());
        } catch (Exception e) {
            Alkatraz.getInstance().getLogger().log(Level.SEVERE, 
                "Failed to load profiles for player: " + player.getName(), e);
            return;
        }

        // Grant starter spells to new players
        MagicProfile magicProfile = getProfile(player.getUniqueId(), MagicProfile.class);
        if (magicProfile != null && !magicProfile.isStarterSpellGranted()) {
            magicProfile.setStarterSpellGranted(true);
            if (magicProfile.getAllDiscoveredSpellTypes().isEmpty()) {
                Spell magicMissile = SpellRegistry.getSpell("magic_missile");
                if (magicMissile != null) {
                    magicProfile.setDiscoveredSpell(magicMissile, true);
                    Bukkit.getPluginManager().callEvent(new SpellDiscoveredEvent(player, magicMissile));
                    Alkatraz.getInstance().getLogger().log(Level.FINE,
                        "Granted starter spell 'magic_missile' to new player: " + player.getName());
                }
            }
            // Persist immediately so the discovery survives a crash in the first autosave window.
            saveProfile(magicProfile);
        }
    }

@EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!initialized) return;
        flushPlayer(event.getEntity());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!initialized) return;
        flushPlayer(event.getPlayer());
    }

    private static void flushPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        // Snapshot cached profiles on the main thread so the async save never has
        // to touch the live (main-thread-mutated) Profile objects.
        Collection<Profile> profiles =
                cache != null ? new ArrayList<>(cache.getAllProfiles(uuid)) : java.util.List.of();
        final java.util.List<Profile> snapshots = snapshotProfiles(uuid, profiles);
        // Snapshot equipment on the main thread too, so the async write does not
        // read the live cache map.
        final java.util.Map<String, org.bukkit.inventory.ItemStack> equipmentSnapshot =
                me.nagasonic.alkatraz.items.magic.equipment.EquipmentStorage.snapshot(uuid);
        long gen = flushGenCounter.incrementAndGet();
        flushGenerations.put(uuid, gen);
        Bukkit.getScheduler().runTaskAsynchronously(Alkatraz.getInstance(), () -> {
            try {
                saveLock.lock();
                // Re-check generation and cache membership after obtaining the lock,
                // so a synchronous quit/unload cannot race this save.
if (flushBlocked.contains(uuid)) return;
                Long currentGen = flushGenerations.get(uuid);
                if (currentGen == null || currentGen.longValue() != gen) return;
                if (cache == null || !cache.hasPlayer(uuid)) return;
                try {
                    for (Profile profile : snapshots) {
                        ProfilePersistence.saveProfile(uuid, profile);
                    }
                    if (equipmentSnapshot != null) {
                        me.nagasonic.alkatraz.items.magic.equipment.EquipmentStorage.save(uuid, equipmentSnapshot);
                    } else {
                        me.nagasonic.alkatraz.items.magic.equipment.EquipmentStorage.save(uuid);
                    }
                } catch (Exception e) {
                    Alkatraz.getInstance().getLogger().log(Level.WARNING,
                            "Failed to flush player data for " + name, e);
                }
            } finally {
                saveLock.unlock();
                flushGenerations.remove(uuid, gen);
            }
        });
    }

    /**
     * Builds detached copies of the given profiles so they can be serialised on a
     * background thread without racing the main-thread mutations of the originals.
     */
    private static java.util.List<Profile> snapshotProfiles(UUID uuid, Collection<Profile> profiles) {
        java.util.List<Profile> copies = new java.util.ArrayList<>();
        for (Profile p : profiles) {
            try {
                Profile copy = ProfileRegistry.createBlankProfile(uuid, p.getClass());
                copy.copyStats(p);
                copies.add(copy);
            } catch (Exception e) {
                // Do not fall back to the live object: that would hand a
                // main-thread-mutated profile to an async writer. Skip this
                // profile's save and log the failure instead.
                Alkatraz.getInstance().getLogger().log(Level.WARNING,
                        "Failed to snapshot profile for " + uuid + " (" + p.getClass().getSimpleName() + "); skipping save", e);
            }
        }
        return copies;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        try {
            EquipmentStorage.unload(player);
        } catch (Exception e) {
            Alkatraz.getInstance().getLogger().log(Level.SEVERE, 
                "Failed to unload equipment for player: " + player.getName(), e);
        }

        UUID quitUuid = player.getUniqueId();
        // Mark this UUID as no longer flushable before unloading so any flush already
        // in flight cannot overwrite the synchronous save below with a stale snapshot.
        flushBlocked.add(quitUuid);
        try {
            unloadPlayer(player);
            Alkatraz.getInstance().getLogger().log(Level.FINE,
                "Saved and unloaded profiles for player: " + player.getName());
        } catch (Exception e) {
            Alkatraz.getInstance().getLogger().log(Level.SEVERE,
                "Failed to save profiles for player: " + player.getName(), e);
        } finally {
            // Always clear these, even if unloadPlayer threw, so a failed quit save
            // cannot permanently disable flushing for this UUID.
            flushGenerations.remove(quitUuid);
            flushBlocked.remove(quitUuid);
            // Regeneration is shared, but the memoised player folder would otherwise
            // keep an entry for this UUID for the lifetime of the process. Dropped
            // last, because the save above re-memoises it.
            loadLocks.remove(quitUuid);
            ProfilePersistence.forgetPlayer(quitUuid);
            // Release any per-actor coordinator cooldown bucket tracked for this player.
            me.nagasonic.alkatraz.items.magic.condition.implementation.CooldownTracker.clear(quitUuid);
        }
    }

    // Helper Methods

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("ProfileManager is not initialized. Call initialize() first.");
        }
    }
}
