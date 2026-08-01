package me.nagasonic.alkatraz.items.magic.recipe.unlock;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.events.RecipeUnlockedEvent;
import me.nagasonic.alkatraz.items.magic.recipe.MagicItemRecipeManager;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
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
        if (isUnlocked(player, key)) return;
        store.setUnlocked(player.getUniqueId(), key, true);
        CACHE.computeIfAbsent(player.getUniqueId(), id -> store.loadUnlocked(id)).add(key);
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

    /** Unlocks every recipe whose requirements are all met and that is not yet unlocked. Returns newly-unlocked count. */
    public static int evaluate(Player player) {
        if (player == null) return 0;
        Set<String> alreadyUnlocked = new HashSet<>(CACHE.computeIfAbsent(player.getUniqueId(), id -> store.loadUnlocked(id)));
        int count = 0;
        for (NamespacedKey key : RecipeRegistry.getAllKeys()) {
            List<Requirement> requirements = MagicItemRecipeManager.getRequirements(key);
            if (requirements.isEmpty()) continue; // no requirements = not a lockable recipe
            String recipeKey = key.toString();
            if (alreadyUnlocked.contains(recipeKey)) continue;
            boolean met = true;
            for (Requirement req : requirements) {
                if (!req.isMet(player)) { met = false; break; }
            }
            if (met) {
                unlock(player, recipeKey);
                alreadyUnlocked.add(recipeKey);
                count++;
            }
        }
        return count;
    }
}
