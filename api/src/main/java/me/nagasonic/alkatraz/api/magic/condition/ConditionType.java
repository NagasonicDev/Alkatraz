package me.nagasonic.alkatraz.api.magic.condition;

import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

import java.util.Map;

public final class ConditionType implements Keyed {

    @FunctionalInterface
    public interface Factory {
        Condition create(Map<String, Object> config);
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

    public Condition create(Map<String, Object> config) {
        return factory.create(config);
    }
}
