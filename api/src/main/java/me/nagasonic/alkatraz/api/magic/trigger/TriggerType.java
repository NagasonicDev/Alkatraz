package me.nagasonic.alkatraz.api.magic.trigger;

import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

import java.util.List;

public final class TriggerType implements Keyed {

    private final NamespacedKey key;
    private final String description;
    private final List<String> allowedItemTypes;

    public TriggerType(NamespacedKey key, String description) {
        this(key, description, List.of());
    }

    public TriggerType(NamespacedKey key, String description, List<String> allowedItemTypes) {
        this.key = key;
        this.description = description;
        this.allowedItemTypes = allowedItemTypes == null ? List.of() : List.copyOf(allowedItemTypes);
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    public String description() {
        return description;
    }

    public List<String> allowedItemTypes() {
        return allowedItemTypes;
    }
}
