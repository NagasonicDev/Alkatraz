package me.nagasonic.alkatraz.api.magic.attribute;

import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

/**
 * Defines a type of attribute in the magic item system, including its registry key,
 * default value, and display name shown to players.
 *
 * <p>Attribute types are registered via {@link me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries#ATTRIBUTE_TYPES}
 * and looked up by {@link org.bukkit.NamespacedKey}.</p>
 */
public final class AttributeType implements Keyed {

    private final NamespacedKey key;
    private final double defaultValue;
    private final String displayName;

    /**
     * Creates a new attribute type.
     *
     * @param key          the unique {@link NamespacedKey} identifying this attribute type
     * @param defaultValue the value used when no contributions are present for this attribute
     * @param displayName  the human-readable name displayed to players
     */
    public AttributeType(NamespacedKey key, double defaultValue, String displayName) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.displayName = displayName;
    }

    /** {@inheritDoc} */
    @Override
    public NamespacedKey getKey() {
        return key;
    }

    /**
     * Returns the default value used when no contributions resolve for this attribute.
     *
     * @return the default attribute value
     */
    public double defaultValue() {
        return defaultValue;
    }

    /**
     * Returns the human-readable display name for this attribute type.
     *
     * @return the display name shown to players
     */
    public String displayName() {
        return displayName;
    }
}
