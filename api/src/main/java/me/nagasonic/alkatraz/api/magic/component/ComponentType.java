package me.nagasonic.alkatraz.api.magic.component;

import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

import java.util.Map;

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

    public static class Builder {
        private NamespacedKey key;
        private String description;
        private Map<String, Object> defaultConfig = Map.of();

        public Builder key(NamespacedKey key) { this.key = key; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultConfig(Map<String, Object> config) { this.defaultConfig = config; return this; }

        public ComponentType build() {
            return new ComponentType(key, description, defaultConfig);
        }
    }
}
