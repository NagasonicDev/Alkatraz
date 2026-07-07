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

public final class EngravingDefinition implements Keyed {

    private final NamespacedKey key;
    private final ItemVisual visual;
    private final Map<NamespacedKey, Double> attributes;
    private final List<Condition> conditions;
    private final List<Effect> effects;
    private final List<String> allowedItemTypes;
    private final Map<String, Object> staticConfig;

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

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    public Map<NamespacedKey, Double> attributes() {
        return attributes;
    }

    public List<Condition> conditions() {
        return conditions;
    }

    public List<Effect> effects() {
        return effects;
    }

    public List<String> allowedItemTypes() {
        return allowedItemTypes;
    }

    public ItemVisual visual() {
        return visual;
    }

    public Map<String, Object> staticConfig() {
        return staticConfig;
    }
}
