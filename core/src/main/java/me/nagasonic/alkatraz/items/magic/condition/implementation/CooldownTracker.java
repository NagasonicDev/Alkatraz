package me.nagasonic.alkatraz.items.magic.condition.implementation;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for per-actor, per-item cooldowns.
 * <p>
 * Keys are derived from the acting entity's UUID and the triggering item's
 * instance id, so two separate engravings on different items track their own
 * cooldowns. Exposed as static helpers so the logic is testable without
 * Bukkit entities.
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
        return endsAt != null && nowMillis < endsAt;
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

    public static void clearAll() {
        COOLDOWN_ENDS.clear();
    }
}
