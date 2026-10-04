package me.nagasonic.alkatraz.items.magic.attribute;

import me.nagasonic.alkatraz.api.magic.attribute.AttributeContribution;
import org.bukkit.NamespacedKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Determines how a magic item attribute key is resolved when multiple
 * contributions target the same attribute.
 */
public final class AttributeOps {

    private static final int MAX_CACHED_KEYS = 512;

    private static final Map<NamespacedKey, AttributeContribution.AttributeOperation> CACHE =
            new ConcurrentHashMap<>();

    private AttributeOps() {}

    public static AttributeContribution.AttributeOperation operationFor(NamespacedKey key) {
        AttributeContribution.AttributeOperation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        AttributeContribution.AttributeOperation computed = computeOperation(key);
        if (CACHE.size() < MAX_CACHED_KEYS) {
            CACHE.putIfAbsent(key, computed);
        }
        return computed;
    }

    private static AttributeContribution.AttributeOperation computeOperation(NamespacedKey key) {
        String path = key.getKey();
        if (path.equals("cast_time_multiplier")) {
            return AttributeContribution.AttributeOperation.MULTIPLY;
        }
        if (path.contains("_set_")) {
            return AttributeContribution.AttributeOperation.SET;
        }
        if (path.contains("_multiply_")) {
            return AttributeContribution.AttributeOperation.MULTIPLY;
        }
        return AttributeContribution.AttributeOperation.ADD;
    }
}
