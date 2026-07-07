package me.nagasonic.alkatraz.api.magic.effect;

import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

import java.util.Map;

public final class EffectType implements Keyed {

    @FunctionalInterface
    public interface Factory {
        Effect create(Map<String, Object> config);
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

    public Effect create(Map<String, Object> config) {
        return factory.create(config);
    }
}
