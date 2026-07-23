package me.nagasonic.alkatraz.api.magic.effect;

import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

import java.util.Map;

/**
 * A keyed registry entry that describes a type of {@link Effect} and provides a factory
 * for creating instances from configuration data.
 */
public final class EffectType implements Keyed {

    /**
     * A functional interface for creating {@link Effect} instances from a configuration map.
     */
    @FunctionalInterface
    public interface Factory {
        /**
         * Creates a new effect instance from the given configuration.
         *
         * @param config the configuration parameters for the effect
         * @return a new {@link Effect} instance
         */
        Effect create(Map<String, Object> config);
    }

    private final NamespacedKey key;
    private final Factory factory;

    /**
     * Constructs a new effect type.
     *
     * @param key     the unique namespaced key identifying this effect type
     * @param factory the factory used to create effect instances
     */
    public EffectType(NamespacedKey key, Factory factory) {
        this.key = key;
        this.factory = factory;
    }

    /** {@inheritDoc} */
    @Override
    public NamespacedKey getKey() {
        return key;
    }

    /**
     * Creates a new {@link Effect} instance using this type's factory and the given configuration.
     *
     * @param config the configuration parameters for the effect
     * @return a new {@link Effect} instance
     */
    public Effect create(Map<String, Object> config) {
        return factory.create(config);
    }
}
