package me.nagasonic.alkatraz.items.magic.modifier;

import me.nagasonic.alkatraz.items.magic.registry.Keyed;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerBinding;
import org.bukkit.NamespacedKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable template for an upgrade that can be attached to item instances.
 */
public final class ModifierDefinition implements Keyed {

    private final NamespacedKey key;
    private final Map<NamespacedKey, Double> attributes;
    private final List<TriggerBinding> triggers;
    private final Map<String, Object> staticConfig;

    public ModifierDefinition(
            NamespacedKey key,
            Map<NamespacedKey, Double> attributes,
            List<TriggerBinding> triggers,
            Map<String, Object> staticConfig
    ) {
        this.key = key;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.triggers = List.copyOf(triggers);
        this.staticConfig = Collections.unmodifiableMap(new LinkedHashMap<>(staticConfig));
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    public Map<NamespacedKey, Double> attributes() {
        return attributes;
    }

    public List<TriggerBinding> triggers() {
        return triggers;
    }

    public Map<String, Object> staticConfig() {
        return staticConfig;
    }
}
