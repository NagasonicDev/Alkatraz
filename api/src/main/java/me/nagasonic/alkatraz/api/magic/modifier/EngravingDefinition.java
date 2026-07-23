package me.nagasonic.alkatraz.api.magic.modifier;

import me.nagasonic.alkatraz.api.magic.condition.Condition;
import me.nagasonic.alkatraz.api.magic.definition.ItemVisual;
import me.nagasonic.alkatraz.api.magic.effect.Effect;
import me.nagasonic.alkatraz.api.magic.registry.Keyed;
import org.bukkit.NamespacedKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines a type of engraving that can be applied to a {@link me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance}.
 * An engraving definition specifies the visual representation, stat-modifying attributes,
 * activation conditions, triggered effects, item-type restrictions, and static configuration
 * for a particular engraving.
 */
public final class EngravingDefinition implements Keyed {

    private final NamespacedKey key;
    private final ItemVisual visual;
    private final Map<NamespacedKey, Double> attributes;
    private final List<Condition> conditions;
    private final List<Effect> effects;
    private final List<String> allowedItemTypes;
    private final Map<String, Object> staticConfig;

    /**
     * Constructs a new engraving definition.
     *
     * @param key             unique key for this engraving definition
     * @param visual          the visual representation of the engraving
     * @param attributes      map of attribute keys to their bonus values, must not be {@code null}
     * @param conditions      conditions that must be met for the engraving to activate, or {@code null}
     * @param effects         effects triggered when the engraving activates, or {@code null}
     * @param allowedItemTypes list of item type identifiers this engraving can be applied to, or {@code null}
     * @param staticConfig    arbitrary static configuration data, must not be {@code null}
     */
    public EngravingDefinition(
            NamespacedKey key,
            ItemVisual visual,
            Map<NamespacedKey, Double> attributes,
            List<Condition> conditions,
            List<Effect> effects,
            List<String> allowedItemTypes,
            Map<String, Object> staticConfig
    ) {
        this.key = key;
        this.visual = visual;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        this.conditions = conditions == null ? List.of() : List.copyOf(conditions);
        this.effects = effects == null ? List.of() : List.copyOf(effects);
        this.allowedItemTypes = allowedItemTypes == null ? List.of() : List.copyOf(allowedItemTypes);
        this.staticConfig = Collections.unmodifiableMap(new LinkedHashMap<>(staticConfig));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NamespacedKey getKey() {
        return key;
    }

    /**
     * Returns an unmodifiable map of attribute keys to bonus values.
     *
     * @return the attributes map
     */
    public Map<NamespacedKey, Double> attributes() {
        return attributes;
    }

    /**
     * Returns the list of conditions that must be met for the engraving to activate.
     *
     * @return the conditions, never {@code null}
     */
    public List<Condition> conditions() {
        return conditions;
    }

    /**
     * Returns the list of effects triggered when the engraving activates.
     *
     * @return the effects, never {@code null}
     */
    public List<Effect> effects() {
        return effects;
    }

    /**
     * Returns the list of item type identifiers this engraving can be applied to.
     *
     * @return the allowed item types, never {@code null}
     */
    public List<String> allowedItemTypes() {
        return allowedItemTypes;
    }

    /**
     * Returns the visual representation of this engraving.
     *
     * @return the {@link ItemVisual}
     */
    public ItemVisual visual() {
        return visual;
    }

    /**
     * Returns an unmodifiable map of static configuration data.
     *
     * @return the static config map
     */
    public Map<String, Object> staticConfig() {
        return staticConfig;
    }
}
