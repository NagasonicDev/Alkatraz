package me.nagasonic.alkatraz.items.magic.condition.implementation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for per-actor, per-item cooldowns.
 * <p>
 * Keys are derived from the acting entity's UUID and a stable item-identity key
 * (definition + modifiers + engravings, see {@link CooldownCondition#stableItemKey}),
 * so two separate engravings on different items track their own cooldowns.
 * Exposed as static helpers so the logic is testable without Bukkit entities.
 */
public final class CooldownTracker {

    private static final Map<UUID, Map<String, Long>> COOLDOWN_ENDS = new ConcurrentHashMap<>();

    private CooldownTracker() {}

    public static boolean isOnCooldown(UUID actorId, String itemKey, long nowMillis) {
        Map<String, Long> byItem = COOLDOWN_ENDS.get(actorId);
        if (byItem == null) {
            return false;
        }
        Long endsAt = byItem.get(itemKey);
        if (endsAt == null) {
            return false;
        }
        if (nowMillis < endsAt) {
            return true;
        }
        // Expired entries are dead weight; drop them and the actor map if it empties.
        byItem.remove(itemKey, endsAt);
        if (byItem.isEmpty()) {
            COOLDOWN_ENDS.remove(actorId, byItem);
        }
        return false;
    }

    public static void markCooldown(UUID actorId, String itemKey, long endMillis) {
        COOLDOWN_ENDS.computeIfAbsent(actorId, k -> new ConcurrentHashMap<>()).put(itemKey, endMillis);
    }

    public static void clearCooldown(UUID actorId, String itemKey) {
        Map<String, Long> byItem = COOLDOWN_ENDS.get(actorId);
        if (byItem != null) {
            byItem.remove(itemKey);
        }
    }

    /** Drops every cooldown tracked for a single actor. */
    public static void clear(UUID actorId) {
        if (actorId != null) {
            COOLDOWN_ENDS.remove(actorId);
        }
    }

    public static void clearAll() {
        COOLDOWN_ENDS.clear();
    }
}
