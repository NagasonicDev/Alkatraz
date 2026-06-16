package me.nagasonic.alkatraz.items.magic.effect;

import me.nagasonic.alkatraz.items.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

/**
 * Registry metadata and factory hook for an effect type.
 */
public final class EffectType implements Keyed {

    @FunctionalInterface
    public interface Factory {
        Effect create(java.util.Map<String, Object> config);
    }

    private final NamespacedKey key;
    private final Factory factory;

    public EffectType(NamespacedKey key, Factory factory) {
        this.key = key;
        this.factory = factory;
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    public Effect create(java.util.Map<String, Object> config) {
        return factory.create(config);
    }
}
