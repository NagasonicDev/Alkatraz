package me.nagasonic.alkatraz.api.magic.definition;

import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerBinding;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ItemDefinition implements Keyed {

    private final NamespacedKey key;
    private final ItemVisual visual;
    private final List<NamespacedKey> components;
    private final Map<NamespacedKey, Double> attributes;
    private final Map<Attribute, Double> vanillaAttributes;
    private final List<TriggerBinding> triggers;
    private final Map<String, Object> staticConfig;

    public ItemDefinition(
            NamespacedKey key,
            ItemVisual visual,
            List<NamespacedKey> components,
            Map<NamespacedKey, Double> attributes,
            Map<Attribute, Double> vanillaAttributes,
            List<TriggerBinding> triggers,
            Map<String, Object> staticConfig
    ) {
        this.key = key;
        this.visual = visual;
        this.components = List.copyOf(components);
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.vanillaAttributes = Collections.unmodifiableMap(new LinkedHashMap<>(vanillaAttributes));
        this.triggers = List.copyOf(triggers);
        this.staticConfig = Collections.unmodifiableMap(new LinkedHashMap<>(staticConfig));
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    public ItemVisual visual() {
        return visual;
    }

    public Map<Attribute, Double> vanillaAttributes() {
        return vanillaAttributes;
    }

    public List<NamespacedKey> components() {
        return components;
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

    public boolean hasComponent(NamespacedKey componentType) {
        return components.contains(componentType);
    }

    public double attributeOrDefault(NamespacedKey attribute, double fallback) {
        return attributes.getOrDefault(attribute, fallback);
    }
}
