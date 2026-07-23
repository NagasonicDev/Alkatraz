package me.nagasonic.alkatraz.api.magic.condition;

import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

import java.util.Map;

/**
 * Defines a type of condition that can be applied to a magic item's effect, identified by a
 * unique {@link NamespacedKey}.
 * <p>
 * Each condition type carries a {@link Factory} that creates {@link Condition} instances from
 * a configuration map, allowing condition behavior to be parameterised per-item via data.
 */
public final class ConditionType implements Keyed {

    /**
     * Factory for creating {@link Condition} instances from a configuration map.
     */
    @FunctionalInterface
    public interface Factory {
        /**
         * Creates a new condition from the given configuration.
         *
         * @param config configuration parameters for this condition instance
         * @return a new {@link Condition}
         */
        Condition create(Map<String, Object> config);
    }

    private final NamespacedKey key;
    private final Factory factory;

    /**
     * Creates a new condition type.
     *
     * @param key     the unique namespace key identifying this condition type
     * @param factory the factory used to create condition instances from configuration
     */
    public ConditionType(NamespacedKey key, Factory factory) {
        this.key = key;
        this.factory = factory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NamespacedKey getKey() {
        return key;
    }

    /**
     * Creates a new {@link Condition} instance using this type's factory and the provided
     * configuration.
     *
     * @param config configuration parameters for the condition
     * @return a new {@link Condition} instance
     */
    public Condition create(Map<String, Object> config) {
        return factory.create(config);
    }
}
