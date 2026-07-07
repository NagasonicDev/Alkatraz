package me.nagasonic.alkatraz.api.magic.registry;

import me.nagasonic.alkatraz.api.magic.attribute.AttributeType;
import me.nagasonic.alkatraz.api.magic.component.ComponentType;
import me.nagasonic.alkatraz.api.magic.condition.ConditionType;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.effect.EffectType;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerType;

public final class MagicItemRegistries {

    public static final Registry<ItemDefinition> ITEM_DEFINITIONS = new Registry<>();
    public static final Registry<ComponentType> COMPONENT_TYPES = new Registry<>();
    public static final Registry<EngravingDefinition> ENGRAVING_DEFINITIONS = new Registry<>();
    public static final Registry<TriggerType> TRIGGER_TYPES = new Registry<>();
    public static final Registry<EffectType> EFFECT_TYPES = new Registry<>();
    public static final Registry<ConditionType> CONDITION_TYPES = new Registry<>();
    public static final Registry<AttributeType> ATTRIBUTE_TYPES = new Registry<>();

    private MagicItemRegistries() {}

    public static void clearAll() {
        ITEM_DEFINITIONS.clear();
        COMPONENT_TYPES.clear();
        ENGRAVING_DEFINITIONS.clear();
        TRIGGER_TYPES.clear();
        EFFECT_TYPES.clear();
        CONDITION_TYPES.clear();
        ATTRIBUTE_TYPES.clear();
    }
}
