package me.nagasonic.alkatraz.items.magic.registry;

import me.nagasonic.alkatraz.items.magic.attribute.AttributeType;
import me.nagasonic.alkatraz.items.magic.component.ComponentType;
import me.nagasonic.alkatraz.items.magic.condition.ConditionType;
import me.nagasonic.alkatraz.items.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.items.magic.effect.EffectType;
import me.nagasonic.alkatraz.items.magic.modifier.ModifierDefinition;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerType;

/**
 * Single access point for all magic-item registries.
 */
public final class MagicItemRegistries {

    public static final Registry<ItemDefinition> ITEM_DEFINITIONS = new Registry<>();
    public static final Registry<ComponentType> COMPONENT_TYPES = new Registry<>();
    public static final Registry<ModifierDefinition> MODIFIER_DEFINITIONS = new Registry<>();
    public static final Registry<TriggerType> TRIGGER_TYPES = new Registry<>();
    public static final Registry<EffectType> EFFECT_TYPES = new Registry<>();
    public static final Registry<ConditionType> CONDITION_TYPES = new Registry<>();
    public static final Registry<AttributeType> ATTRIBUTE_TYPES = new Registry<>();

    private MagicItemRegistries() {}

    public static void clearAll() {
        ITEM_DEFINITIONS.clear();
        COMPONENT_TYPES.clear();
        MODIFIER_DEFINITIONS.clear();
        TRIGGER_TYPES.clear();
        EFFECT_TYPES.clear();
        CONDITION_TYPES.clear();
        ATTRIBUTE_TYPES.clear();
    }
}
