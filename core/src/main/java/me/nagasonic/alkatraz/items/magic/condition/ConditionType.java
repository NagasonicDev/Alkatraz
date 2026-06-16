package me.nagasonic.alkatraz.items.magic.condition;

import me.nagasonic.alkatraz.items.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

/**
 * Registry metadata and factory hook for a condition evaluator type.
 */
public final class ConditionType implements Keyed {

    @FunctionalInterface
    public interface Factory {
        Condition create(java.util.Map<String, Object> config);
    }

    private final NamespacedKey key;
    private final Factory factory;

    public ConditionType(NamespacedKey key, Factory factory) {
        this.key = key;
        this.factory = factory;
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    public Condition create(java.util.Map<String, Object> config) {
        return factory.create(config);
    }
}
