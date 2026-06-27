package me.nagasonic.alkatraz.items.magic;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.magic.attribute.AttributeService;
import me.nagasonic.alkatraz.items.magic.attribute.AttributeType;
import me.nagasonic.alkatraz.items.magic.attribute.EquipmentAttributeSource;
import me.nagasonic.alkatraz.items.magic.component.ComponentType;
import me.nagasonic.alkatraz.items.magic.condition.AlwaysCondition;
import me.nagasonic.alkatraz.items.magic.condition.ArcaneKnowledgeCondition;
import me.nagasonic.alkatraz.items.magic.condition.CompareAttributeCondition;
import me.nagasonic.alkatraz.items.magic.condition.ConditionType;
import me.nagasonic.alkatraz.items.magic.condition.SpellElementCondition;
import me.nagasonic.alkatraz.items.magic.config.MagicItemConfigLoader;
import me.nagasonic.alkatraz.items.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.items.magic.effect.EffectType;
import me.nagasonic.alkatraz.items.magic.effect.IgniteEffect;
import me.nagasonic.alkatraz.items.magic.effect.PlaySoundEffect;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentService;
import me.nagasonic.alkatraz.items.magic.modifier.ModifierDefinition;
import me.nagasonic.alkatraz.items.magic.persistence.ItemDataKeys;
import me.nagasonic.alkatraz.items.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.items.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerPipeline;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerType;

/**
 * Registers built-in types and loads data-driven definitions at startup.
 */
public final class MagicItemBootstrap {

    private MagicItemBootstrap() {}

    public static void initialize() {
        ItemDataKeys.initialize();
        registerBuiltInTypes();
        loadDefinitions();

        EquipmentService equipmentService = new EquipmentService();
        AttributeService attributeService = new AttributeService();
        attributeService.registerSource(new EquipmentAttributeSource(equipmentService));

        TriggerPipeline triggerPipeline = new TriggerPipeline(equipmentService);
        MagicItemService itemService = new MagicItemService(triggerPipeline);

        MagicItemServices.initialize(itemService, attributeService, equipmentService);
        Alkatraz.logInfo("Magic item system initialized with "
                + MagicItemRegistries.ITEM_DEFINITIONS.values().size() + " item definitions.");
    }

    public static void reload() {
        MagicItemRegistries.ITEM_DEFINITIONS.clear();
        MagicItemRegistries.MODIFIER_DEFINITIONS.clear();
        loadDefinitions();
    }

    /** Addon entry point: register a new component type at runtime. */
    public static void registerComponentType(ComponentType type) {
        MagicItemRegistries.COMPONENT_TYPES.register(type);
    }

    /** Addon entry point: register a new attribute type at runtime. */
    public static void registerAttributeType(AttributeType type) {
        MagicItemRegistries.ATTRIBUTE_TYPES.register(type);
    }

    /** Addon entry point: register a new trigger type at runtime. */
    public static void registerTriggerType(TriggerType type) {
        MagicItemRegistries.TRIGGER_TYPES.register(type);
    }

    /** Addon entry point: register a new condition type at runtime. */
    public static void registerConditionType(ConditionType type) {
        MagicItemRegistries.CONDITION_TYPES.register(type);
    }

    /** Addon entry point: register a new effect type at runtime. */
    public static void registerEffectType(EffectType type) {
        MagicItemRegistries.EFFECT_TYPES.register(type);
    }

    static void reloadDefinitions() {
        reload();
    }

    static void loadDefinitions() {
        MagicItemService.saveDefaultResource("magic/items/wooden_wand.yml");
        MagicItemService.saveDefaultResource("magic/items/reinforced_wand.yml");
        MagicItemService.saveDefaultResource("magic/modifiers/fire_rune.yml");

        MagicItemService.loadYamlDefinitions("magic/items", (path, config) -> {
            ItemDefinition definition = MagicItemConfigLoader.loadItemDefinition(config);
            MagicItemRegistries.ITEM_DEFINITIONS.register(definition);
            Alkatraz.logInfo("Loaded magic item definition: " + MagicKeys.format(definition.getKey()) + " (" + path + ")");
        });

        MagicItemService.loadYamlDefinitions("magic/modifiers", (path, config) -> {
            ModifierDefinition modifier = MagicItemConfigLoader.loadModifierDefinition(config);
            MagicItemRegistries.MODIFIER_DEFINITIONS.register(modifier);
            Alkatraz.logInfo("Loaded modifier definition: " + MagicKeys.format(modifier.getKey()) + " (" + path + ")");
        });
    }

    private static void registerBuiltInTypes() {
        registerComponent("wand", "Spell casting focus");
        registerComponent("mana_container", "Stores and supplies mana");
        registerComponent("equipment", "Wearable equipment behavior");
        registerComponent("spell_focus", "Enhances spell casting");
        registerComponent("durability", "Item durability tracking");
        registerComponent("socket_holder", "Accepts socketed upgrades");

        registerAttribute("spell_power", 0, "Spell Power");
        registerAttribute("max_circle", 1, "Maximum Circle");
        registerAttribute("cast_time_multiplier", 1, "Cast Time Multiplier");
        registerAttribute("fire_affinity", 0, "Fire Affinity");
        registerAttribute("water_affinity", 0, "Water Affinity");
        registerAttribute("earth_affinity", 0, "Earth Affinity");
        registerAttribute("air_affinity", 0, "Air Affinity");
        registerAttribute("light_affinity", 0, "Light Affinity");
        registerAttribute("dark_affinity", 0, "Dark Affinity");
        registerAttribute("max_mana", 0, "Maximum Mana");
        registerAttribute("mana_regeneration", 0, "Mana Regeneration");

        registerTrigger("on_spell_cast", "When the holder casts a spell");
        registerTrigger("on_spell_hit", "When a spell hits a target");
        registerTrigger("on_kill", "When the holder kills an entity");
        registerTrigger("on_equip", "When equipped in a slot");

        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("always"), AlwaysCondition::fromConfig));
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("compare_attribute"), CompareAttributeCondition::fromConfig));
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("spell_element"), SpellElementCondition::fromConfig));
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("arcane_knowledge"), ArcaneKnowledgeCondition::fromConfig));

        MagicItemRegistries.EFFECT_TYPES.register(new EffectType(
                MagicKeys.alkatraz("ignite"), IgniteEffect::fromConfig));
        MagicItemRegistries.EFFECT_TYPES.register(new EffectType(
                MagicKeys.alkatraz("play_sound"), PlaySoundEffect::fromConfig));
    }

    private static void registerComponent(String key, String description) {
        MagicItemRegistries.COMPONENT_TYPES.register(new ComponentType(MagicKeys.alkatraz(key), description));
    }

    private static void registerAttribute(String key, double defaultValue, String displayName) {
        MagicItemRegistries.ATTRIBUTE_TYPES.register(new AttributeType(MagicKeys.alkatraz(key), defaultValue, displayName));
    }

    private static void registerTrigger(String key, String description) {
        MagicItemRegistries.TRIGGER_TYPES.register(new TriggerType(MagicKeys.alkatraz(key), description));
    }
}
