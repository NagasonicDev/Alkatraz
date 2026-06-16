package me.nagasonic.alkatraz.items.magic.trigger;

import me.nagasonic.alkatraz.items.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

/**
 * Registry metadata for an internal trigger event type.
 */
public final class TriggerType implements Keyed {

    private final NamespacedKey key;
    private final String description;

    public TriggerType(NamespacedKey key, String description) {
        this.key = key;
        this.description = description;
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    public String description() {
        return description;
    }
}
