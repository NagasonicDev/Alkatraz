package me.nagasonic.alkatraz.api.magic.definition;

import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerBinding;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines a magic item's complete specification, including its visual appearance,
 * custom components, attributes, trigger bindings, and static configuration.
 * Instances are immutable and can be safely shared across threads.
 */
public final class ItemDefinition implements Keyed {

    private final NamespacedKey key;
    private final ItemVisual visual;
    private final List<NamespacedKey> components;
    private final Map<NamespacedKey, Double> attributes;
    private final Map<Attribute, Double> vanillaAttributes;
    private final List<TriggerBinding> triggers;
    private final Map<String, Object> staticConfig;

    /**
     * Constructs a new item definition.
     *
     * @param key              the unique namespaced key identifying this definition
     * @param visual           the visual representation of the item
     * @param components       the list of custom component keys attached to this item
     * @param attributes       a map of custom attribute keys to their base values
     * @param vanillaAttributes a map of vanilla {@link Attribute} types to their base values
     * @param triggers         the list of trigger bindings that define this item's behavior
     * @param staticConfig     an arbitrary map of static configuration values
     */
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

    /** {@inheritDoc} */
    @Override
    public NamespacedKey getKey() {
        return key;
    }

    /**
     * Returns the visual representation of this item.
     *
     * @return the item visual
     */
    public ItemVisual visual() {
        return visual;
    }

    /**
     * Returns the vanilla attribute mappings for this item.
     *
     * @return an unmodifiable map of vanilla attributes to their values
     */
    public Map<Attribute, Double> vanillaAttributes() {
        return vanillaAttributes;
    }

    /**
     * Returns the custom component keys attached to this item.
     *
     * @return an unmodifiable list of component keys
     */
    public List<NamespacedKey> components() {
        return components;
    }

    /**
     * Returns the custom attribute mappings for this item.
     *
     * @return an unmodifiable map of custom attribute keys to their values
     */
    public Map<NamespacedKey, Double> attributes() {
        return attributes;
    }

    /**
     * Returns the trigger bindings that define this item's behavior.
     *
     * @return an unmodifiable list of trigger bindings
     */
    public List<TriggerBinding> triggers() {
        return triggers;
    }

    /**
     * Returns the static configuration map for this item.
     *
     * @return an unmodifiable map of configuration keys to their values
     */
    public Map<String, Object> staticConfig() {
        return staticConfig;
    }

    /**
     * Checks whether this item has a component of the given type.
     *
     * @param componentType the namespaced key of the component type to check
     * @return {@code true} if this item has the specified component
     */
    public boolean hasComponent(NamespacedKey componentType) {
        return components.contains(componentType);
    }

    /**
     * Returns the value of the given custom attribute, or the fallback if not present.
     *
     * @param attribute the namespaced key of the attribute
     * @param fallback  the default value to return if the attribute is absent
     * @return the attribute value, or the fallback
     */
    public double attributeOrDefault(NamespacedKey attribute, double fallback) {
        return attributes.getOrDefault(attribute, fallback);
    }
}
