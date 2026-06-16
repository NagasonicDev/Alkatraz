package me.nagasonic.alkatraz.items.magic.component;

import me.nagasonic.alkatraz.items.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

import java.util.Map;

/**
 * Declares a capability an item definition can possess (wand, socket holder, etc.).
 */
public final class ComponentType implements Keyed {

    private final NamespacedKey key;
    private final String description;
    private final Map<String, Object> defaultConfig;

    public ComponentType(NamespacedKey key, String description, Map<String, Object> defaultConfig) {
        this.key = key;
        this.description = description;
        this.defaultConfig = Map.copyOf(defaultConfig);
    }

    public ComponentType(NamespacedKey key, String description) {
        this(key, description, Map.of());
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    public String description() {
        return description;
    }

    public Map<String, Object> defaultConfig() {
        return defaultConfig;
    }
}
