package me.nagasonic.alkatraz.items.magic.recipe.unlock;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.api.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.events.RecipeUnlockedEvent;
import me.nagasonic.alkatraz.items.magic.recipe.MagicItemRecipeManager;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UnlockManager {
    private static UnlockStore store = new ProfileUnlockStore();
    private static final Map<UUID, Set<String>> CACHE = new ConcurrentHashMap<>();

    private UnlockManager() {}

    public static void setStore(UnlockStore newStore) {
        if (newStore != null) store = newStore;
        CACHE.clear();
    }

    public static UnlockStore getStore() {
        return store;
    }

    /** Removes the player's cached unlock set. */
    public static void invalidate(Player player) {
        if (player != null) CACHE.remove(player.getUniqueId());
    }

    /** Removes the cached unlock set for the given player id. */
    public static void invalidate(UUID uuid) {
        if (uuid != null) CACHE.remove(uuid);
    }

    /** Clears the entire unlock cache. */
    public static void invalidateAll() {
        CACHE.clear();
    }

    /** Invalidates the player's cache entry, then unlocks every eligible recipe. Returns newly-unlocked count. */
    public static int refresh(Player player) {
        if (player == null) return 0;
        invalidate(player);
        return evaluate(player);
    }

    public static boolean isUnlocked(Player player, String recipeKey) {
        if (player == null || recipeKey == null || recipeKey.isBlank()) return false;
        Optional<NamespacedKey> parsed = MagicKeys.parse(recipeKey);
        if (parsed.isPresent() && MagicItemRecipeManager.getRequirements(parsed.get()).isEmpty()) return true;
        String key = recipeKey.toLowerCase();
        return CACHE.computeIfAbsent(player.getUniqueId(), id -> store.loadUnlocked(id)).contains(key);
    }

    public static void unlock(Player player, String recipeKey) {
        if (player == null || recipeKey == null || recipeKey.isBlank()) return;
        String key = recipeKey.toLowerCase();
        Set<String> unlocked = CACHE.computeIfAbsent(player.getUniqueId(), id -> store.loadUnlocked(id));
        if (!unlocked.add(key)) return;
        store.setUnlocked(player.getUniqueId(), key, true);
        Bukkit.getPluginManager().callEvent(new RecipeUnlockedEvent(player, key));
        NotificationManager.notify(player, key);
    }

    public static void lock(Player player, String recipeKey) {
        if (player == null || recipeKey == null || recipeKey.isBlank()) return;
        String key = recipeKey.toLowerCase();
        store.setUnlocked(player.getUniqueId(), key, false);
        Set<String> cached = CACHE.get(player.getUniqueId());
        if (cached != null) cached.remove(key);
    }

    /**
     * Keys of every recipe that can actually be locked, i.e. those with a non-empty
     * requirement list. Recomputing this per evaluation meant calling
     * {@code MagicItemRecipeManager.getRequirements} for every registered recipe on every
     * crafting-matrix change and on every {@code UnlockSweep} player.
     *
     * <p>There is no recipe-reload hook reachable from here, so the set is rebuilt lazily
     * whenever {@link RecipeRegistry#getAllKeys()} reports a different size than the one the
     * cache was built from. That covers add/remove-on-reload, which is the only way the
     * registry changes; a reload that swaps one recipe for another without changing the
     * count would leave a stale set until the next count change or restart.
     * {@link RecipeRegistry#unregisterAll()} calls {@link #invalidateLockableKeys()}, so
     * every reachable reload path drops the set regardless of the count.
     */
    private static volatile Map<String, NamespacedKey> LOCKABLE = Map.of();
    private static volatile long lockableSourceGeneration = -1L;

    private static Map<String, NamespacedKey> lockableKeys() {
        long generation = RecipeRegistry.getRegistryGeneration();
        if (generation == lockableSourceGeneration) {
            return LOCKABLE;
        }
        Map<String, NamespacedKey> lockable = new HashMap<>();
        for (NamespacedKey key : RecipeRegistry.getAllKeys()) {
            if (!MagicItemRecipeManager.getRequirements(key).isEmpty()) {
                lockable.put(key.toString(), key);
            }
        }
        LOCKABLE = Map.copyOf(lockable);
        lockableSourceGeneration = generation;
        return LOCKABLE;
    }

    /** Discards the cached lockable-key map so the next evaluation rebuilds it. */
    public static void invalidateLockableKeys() {
        lockableSourceGeneration = -1L;
        LOCKABLE = Map.of();
    }

    /** Unlocks every recipe whose requirements are all met and that is not yet unlocked. Returns newly-unlocked count. */
    public static int evaluate(Player player) {
        if (player == null) return 0;
        Map<String, NamespacedKey> lockable = lockableKeys();
        if (lockable.isEmpty()) return 0;
        Set<String> alreadyUnlocked = CACHE.computeIfAbsent(player.getUniqueId(), id -> store.loadUnlocked(id));
        int count = 0;
        for (Map.Entry<String, NamespacedKey> entry : lockable.entrySet()) {
            String recipeKey = entry.getKey();
            if (alreadyUnlocked.contains(recipeKey)) continue;
            boolean met = true;
            for (Requirement req : MagicItemRecipeManager.getRequirements(entry.getValue())) {
                if (!req.isMet(player)) { met = false; break; }
            }
            if (met) {
                unlock(player, recipeKey);
                count++;
            }
        }
        return count;
    }
}
