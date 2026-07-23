package me.nagasonic.alkatraz.api.magic.component;

import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

import java.util.Map;

/**
 * Defines a type of component that can be attached to a magic item, identified by a unique
 * {@link NamespacedKey}.
 * <p>
 * Each component type carries a human-readable description and an optional default configuration
 * map that provides baseline values for items using this component. Use the {@link Builder}
 * for a fluent construction API.
 */
public final class ComponentType implements Keyed {

    private final NamespacedKey key;
    private final String description;
    private final Map<String, Object> defaultConfig;

    /**
     * Creates a new component type with a full configuration.
     *
     * @param key           the unique namespace key identifying this component type
     * @param description   a human-readable description of the component
     * @param defaultConfig default configuration values; copied immutably on construction
     */
    public ComponentType(NamespacedKey key, String description, Map<String, Object> defaultConfig) {
        this.key = key;
        this.description = description;
        this.defaultConfig = Map.copyOf(defaultConfig);
    }

    /**
     * Creates a new component type with no default configuration.
     *
     * @param key         the unique namespace key identifying this component type
     * @param description a human-readable description of the component
     */
    public ComponentType(NamespacedKey key, String description) {
        this(key, description, Map.of());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NamespacedKey getKey() {
        return key;
    }

    /**
     * Returns the human-readable description of this component type.
     *
     * @return the description
     */
    public String description() {
        return description;
    }

    /**
     * Returns the default configuration values for this component type.
     *
     * @return an unmodifiable map of default configuration entries
     */
    public Map<String, Object> defaultConfig() {
        return defaultConfig;
    }

    /**
     * Fluent builder for constructing {@link ComponentType} instances.
     */
    public static class Builder {
        private NamespacedKey key;
        private String description;
        private Map<String, Object> defaultConfig = Map.of();

        /**
         * Sets the unique namespace key for the component type.
         *
         * @param key the namespace key
         * @return this builder
         */
        public Builder key(NamespacedKey key) { this.key = key; return this; }

        /**
         * Sets the human-readable description for the component type.
         *
         * @param description the description
         * @return this builder
         */
        public Builder description(String description) { this.description = description; return this; }

        /**
         * Sets the default configuration values for the component type.
         *
         * @param config the default configuration map
         * @return this builder
         */
        public Builder defaultConfig(Map<String, Object> config) { this.defaultConfig = config; return this; }

        /**
         * Builds and returns a new {@link ComponentType} from the configured values.
         *
         * @return the constructed component type
         */
        public ComponentType build() {
            return new ComponentType(key, description, defaultConfig);
        }
    }
}
