package me.nagasonic.alkatraz.items.magic.attribute;

import org.bukkit.NamespacedKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Aggregated attribute values for an entity at a point in time.
 */
public final class AttributeSnapshot {

    private final Map<NamespacedKey, Double> values;

    public AttributeSnapshot(Map<NamespacedKey, Double> values) {
        this.values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    public double get(NamespacedKey attribute, double fallback) {
        return values.getOrDefault(attribute, fallback);
    }

    public Map<NamespacedKey, Double> asMap() {
        return values;
    }

    public static AttributeSnapshot empty() {
        return new AttributeSnapshot(Map.of());
    }
}
