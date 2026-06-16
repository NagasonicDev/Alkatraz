package me.nagasonic.alkatraz.items.magic.attribute;

import me.nagasonic.alkatraz.items.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

/**
 * Registry metadata for a gameplay attribute (e.g. spell power, fire affinity).
 */
public final class AttributeType implements Keyed {

    private final NamespacedKey key;
    private final double defaultValue;
    private final String displayName;

    public AttributeType(NamespacedKey key, double defaultValue, String displayName) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.displayName = displayName;
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    public double defaultValue() {
        return defaultValue;
    }

    public String displayName() {
        return displayName;
    }
}
