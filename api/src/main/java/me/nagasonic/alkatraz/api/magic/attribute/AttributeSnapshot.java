package me.nagasonic.alkatraz.api.magic.attribute;

import org.bukkit.NamespacedKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * An immutable snapshot of resolved attribute values for an entity at a point in time.
 * Produced by {@link AttributeService#snapshot(LivingEntity, TriggerContext)}.
 */
public final class AttributeSnapshot {

    private final Map<NamespacedKey, Double> values;

    /**
     * Creates a new snapshot with the given attribute values.
     *
     * @param values a map of attribute keys to their resolved values; copied defensively
     */
    public AttributeSnapshot(Map<NamespacedKey, Double> values) {
        this.values = Collections.unmodifiableMap(new HashMap<>(values));
    }

    /**
     * Retrieves the resolved value for the given attribute, returning the fallback
     * if the attribute is not present in this snapshot.
     *
     * @param attribute the {@link NamespacedKey} of the attribute to look up
     * @param fallback  the value to return when the attribute is absent
     * @return the resolved attribute value, or {@code fallback} if not present
     */
    public double get(NamespacedKey attribute, double fallback) {
        return values.getOrDefault(attribute, fallback);
    }

    /**
     * Returns all resolved attribute values as an unmodifiable map.
     *
     * @return an unmodifiable map of attribute keys to their values
     */
    public Map<NamespacedKey, Double> asMap() {
        return values;
    }

    /**
     * Returns an empty snapshot containing no attribute values.
     *
     * @return an empty {@link AttributeSnapshot}
     */
    public static AttributeSnapshot empty() {
        return new AttributeSnapshot(Map.of());
    }
}
