package me.nagasonic.alkatraz.items.magic;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.attribute.AttributeService;
import me.nagasonic.alkatraz.api.magic.attribute.AttributeType;
import me.nagasonic.alkatraz.items.magic.attribute.EquipmentAttributeSource;
import me.nagasonic.alkatraz.api.magic.component.ComponentHandlerRegistry;
import me.nagasonic.alkatraz.api.magic.component.ComponentType;
import me.nagasonic.alkatraz.items.magic.component.handler.wand.WandComponentHandler;
import me.nagasonic.alkatraz.items.magic.component.handler.grimoire.GrimoireComponentHandler;
import me.nagasonic.alkatraz.items.magic.component.handler.scroll.ScrollComponentHandler;
import me.nagasonic.alkatraz.items.magic.condition.implementation.AlwaysCondition;
import me.nagasonic.alkatraz.items.magic.condition.implementation.ArcaneKnowledgeCondition;
import me.nagasonic.alkatraz.items.magic.condition.implementation.CircleLevelCondition;
import me.nagasonic.alkatraz.items.magic.condition.implementation.CompareAttributeCondition;
import me.nagasonic.alkatraz.items.magic.condition.implementation.EventParameterCondition;
import me.nagasonic.alkatraz.api.magic.condition.ConditionType;
import me.nagasonic.alkatraz.items.magic.condition.implementation.HasDiscoveredSpellCondition;
import me.nagasonic.alkatraz.items.magic.condition.implementation.ManaCondition;
import me.nagasonic.alkatraz.items.magic.condition.implementation.PermissionCondition;
import me.nagasonic.alkatraz.items.magic.condition.implementation.RandomCondition;
import me.nagasonic.alkatraz.items.magic.condition.implementation.SpellElementCondition;
import me.nagasonic.alkatraz.items.magic.condition.implementation.WorldCondition;
import me.nagasonic.alkatraz.items.magic.config.MagicItemConfigLoader;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.items.magic.effect.implementation.ApplyPotionEffect;
import me.nagasonic.alkatraz.items.magic.effect.implementation.CommandEffect;
import me.nagasonic.alkatraz.items.magic.effect.implementation.DamageEffect;
import me.nagasonic.alkatraz.api.magic.effect.EffectType;
import me.nagasonic.alkatraz.items.magic.effect.implementation.ExplosionEffect;
import me.nagasonic.alkatraz.items.magic.effect.implementation.HealEffect;
import me.nagasonic.alkatraz.items.magic.effect.implementation.IgniteEffect;
import me.nagasonic.alkatraz.items.magic.effect.implementation.MessageEffect;
import me.nagasonic.alkatraz.items.magic.effect.implementation.ParticleEffect;
import me.nagasonic.alkatraz.items.magic.effect.implementation.PlaySoundEffect;
import me.nagasonic.alkatraz.items.magic.effect.implementation.TeleportEffect;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentService;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.items.magic.equipment.StorageSlotResolver;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.items.magic.persistence.ItemDataKeys;
import me.nagasonic.alkatraz.items.magic.recipe.MagicItemRecipeManager;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerPipeline;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerType;

import java.util.List;

/**
 * Registers built-in types and loads data-driven definitions at startup.
 */
public final class MagicItemBootstrap {

    private MagicItemBootstrap() {}

    public static void initialize() {
        long start = System.nanoTime();
        Alkatraz.logVeryHigh("MagicItemBootstrap initialization started");
        ItemDataKeys.initialize();
        registerBuiltInTypes();
        loadDefinitions();

        EquipmentService equipmentService = new EquipmentService();
        equipmentService.registerVirtualSlot(EquipmentSlot.RING, new StorageSlotResolver(EquipmentSlot.RING));
        equipmentService.registerVirtualSlot(EquipmentSlot.NECKLACE, new StorageSlotResolver(EquipmentSlot.NECKLACE));
        equipmentService.registerVirtualSlot(EquipmentSlot.BRACELET, new StorageSlotResolver(EquipmentSlot.BRACELET));
        equipmentService.registerVirtualSlot(EquipmentSlot.PENDANT, new StorageSlotResolver(EquipmentSlot.PENDANT));
        AttributeService attributeService = AttributeService.getInstance();
        attributeService.registerSource(new EquipmentAttributeSource(equipmentService));

        TriggerPipeline triggerPipeline = new TriggerPipeline(equipmentService);
        MagicItemService itemService = new MagicItemService(triggerPipeline);

        ComponentHandlerRegistry.register(new WandComponentHandler());
        ComponentHandlerRegistry.register(new ScrollComponentHandler());
        ComponentHandlerRegistry.register(new GrimoireComponentHandler());

        MagicItemServices.initialize(itemService, attributeService, equipmentService);
        int registeredRecipes = MagicItemRecipeManager.registerRecipes();
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        Alkatraz.logInfo("Registered " + registeredRecipes + " recipes.");
        Alkatraz.logVeryHigh("MagicItemBootstrap initialization completed in " + elapsed + "ms");
    }

    public static void reload() {
        MagicItemRegistries.ITEM_DEFINITIONS.clear();
        MagicItemRegistries.ENGRAVING_DEFINITIONS.clear();
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

    private static void saveDefaultArmorAndAccessories() {
        String[] armorItems = {
                "apprentice_hat", "apprentice_robe", "apprentice_leggings", "apprentice_boots",
                "scholar_hat", "scholar_robe", "scholar_leggings", "scholar_boots",
                "enchanter_hat", "enchanter_robe", "enchanter_leggings", "enchanter_boots",
                "archmage_hat", "archmage_robe", "archmage_leggings", "archmage_boots"
        };
        for (String name : armorItems) {
            MagicItemService.saveDefaultResource("magic/items/" + name + ".yml");
        }

        String[][] setTiers = {
                {"ember", "blaze", "inferno"},
                {"frost", "glacier", "abyss"},
                {"zephyr", "storm", "tempest"},
                {"boulder", "mountain", "titan"},
                {"radiant", "luminous", "divine"},
                {"shadow", "void", "nether"},
                {"mystic", "arcane", "ethereal"}
        };
        String[] pieces = {"ring", "necklace", "bracelet", "pendant"};
        for (String[] tiers : setTiers) {
            for (String tier : tiers) {
                for (String piece : pieces) {
                    MagicItemService.saveDefaultResource("magic/items/" + tier + "_" + piece + ".yml");
                }
            }
        }
    }

    private static void saveDefaultRecipes() {
        String[] recipes = {
                "wooden_wand", "reinforced_wand",
                "runic_wand", "blaze_wand", "glacier_wand", "mountain_wand",
                "storm_wand", "luminous_wand", "void_wand",
                "runic_grimoire", "blaze_grimoire", "glacier_grimoire", "mountain_grimoire",
                "storm_grimoire", "luminous_grimoire", "void_grimoire",
                "apprentice_hat", "apprentice_robe", "apprentice_leggings", "apprentice_boots",
                "scholar_hat", "scholar_robe", "scholar_leggings", "scholar_boots",
                "enchanter_hat", "enchanter_robe", "enchanter_leggings", "enchanter_boots",
                "archmage_hat", "archmage_robe", "archmage_leggings", "archmage_boots"
        };
        for (String name : recipes) {
            MagicItemService.saveDefaultResource("magic/recipes/" + name + ".yml");
        }

        String[][] setTiers = {
                {"ember", "blaze", "inferno"},
                {"frost", "glacier", "abyss"},
                {"zephyr", "storm", "tempest"},
                {"boulder", "mountain", "titan"},
                {"radiant", "luminous", "divine"},
                {"shadow", "void", "nether"},
                {"mystic", "arcane", "ethereal"}
        };
        String[] pieces = {"ring", "necklace", "bracelet", "pendant"};
        for (String[] tiers : setTiers) {
            for (String tier : tiers) {
                for (String piece : pieces) {
                    MagicItemService.saveDefaultResource("magic/recipes/" + tier + "_" + piece + ".yml");
                }
            }
        }
    }

    private static void saveDefaultEngravings() {
        String[] engravings = {
            "fire_rune", "frost_rune", "thunder_rune", "boulder_rune",
            "radiance_rune", "void_rune", "arcane_power_rune", "mana_well_rune",
            "ender_rune", "berserker_rune", "vampiric_rune", "crippling_rune",
            "blazing_blade_rune", "fortification_rune", "thorns_rune",
            "vitality_rune", "phoenix_rune", "mana_font_rune", "guardian_rune",
            "sage_rune", "rejuvenation_rune", "marksman_rune", "frost_shot_rune",
            "explosive_rune", "veil_rune", "haste_rune", "feather_fall_rune", "barrier_rune"
        };
        for (String name : engravings) {
            MagicItemService.saveDefaultResource("magic/engravings/" + name + ".yml");
        }
    }

    static void loadDefinitions() {
        MagicItemService.saveDefaultResource("magic/items/wooden_wand.yml");
        MagicItemService.saveDefaultResource("magic/items/reinforced_wand.yml");
        MagicItemService.saveDefaultResource("magic/items/runic_wand.yml");
        MagicItemService.saveDefaultResource("magic/items/blaze_wand.yml");
        MagicItemService.saveDefaultResource("magic/items/glacier_wand.yml");
        MagicItemService.saveDefaultResource("magic/items/mountain_wand.yml");
        MagicItemService.saveDefaultResource("magic/items/storm_wand.yml");
        MagicItemService.saveDefaultResource("magic/items/luminous_wand.yml");
        MagicItemService.saveDefaultResource("magic/items/void_wand.yml");
        MagicItemService.saveDefaultResource("magic/items/magic_stone.yml");
        MagicItemService.saveDefaultResource("magic/items/leather_grimoire.yml");
        MagicItemService.saveDefaultResource("magic/items/runic_grimoire.yml");
        MagicItemService.saveDefaultResource("magic/items/blaze_grimoire.yml");
        MagicItemService.saveDefaultResource("magic/items/glacier_grimoire.yml");
        MagicItemService.saveDefaultResource("magic/items/mountain_grimoire.yml");
        MagicItemService.saveDefaultResource("magic/items/storm_grimoire.yml");
        MagicItemService.saveDefaultResource("magic/items/luminous_grimoire.yml");
        MagicItemService.saveDefaultResource("magic/items/void_grimoire.yml");
        saveDefaultEngravings();
        saveDefaultArmorAndAccessories();
        saveDefaultRecipes();
        MagicItemService.saveDefaultResource("magic/item_types.yml");

        final int[] itemCount = {0};
        MagicItemService.loadYamlDefinitions("magic/items", (path, config) -> {
            ItemDefinition definition = MagicItemConfigLoader.loadItemDefinition(config);
            MagicItemRegistries.ITEM_DEFINITIONS.register(definition);
            itemCount[0]++;
        });

        final int[] recipeCount = {0};
        MagicItemService.loadYamlDefinitions("magic/recipes", (path, config) -> {
            MagicItemRecipeManager.registerItemRecipe(config);
            recipeCount[0]++;
        });

        final int[] engravingCount = {0};
        MagicItemService.loadYamlDefinitions("magic/engravings", (path, config) -> {
            EngravingDefinition engraving = MagicItemConfigLoader.loadEngravingDefinition(config);
            MagicItemRegistries.ENGRAVING_DEFINITIONS.register(engraving);
            if (config.contains("recipe")) {
                MagicItemRecipeManager.registerEngravingRecipe(engraving, config);
            }
            engravingCount[0]++;
        });

        me.nagasonic.alkatraz.items.magic.util.ItemTypeMapper.load();

        Alkatraz.logInfo("Loaded " + itemCount[0] + " item definitions, " + recipeCount[0] + " recipes, " + engravingCount[0] + " engravings.");
    }

    private static void registerBuiltInTypes() {
        registerComponent("wand", "Spell casting focus");
        registerComponent("mana_container", "Stores and supplies mana");
        registerComponent("equipment", "Wearable equipment behavior");
        registerComponent("spell_focus", "Enhances spell casting");
        registerComponent("durability", "Item durability tracking");
        registerComponent("scroll", "Teaches a spell on use");
        registerComponent("grimoire", "A book that stores spells on its pages");

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

        registerTrigger("on_spell_cast", "When the holder casts a spell", "wand");
        registerTrigger("on_spell_hit", "When a spell hits a target", "wand");
        registerTrigger("on_kill", "When the holder kills an entity", "sword", "axe", "wand");
        registerTrigger("on_equip", "When equipped in a slot", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_unequip", "When unequipped from a slot", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_damage_taken", "When the holder takes damage", "helmet", "chestplate", "leggings", "boots");
        registerTrigger("on_interact_entity", "When the holder interacts with an entity", "wand", "sword", "axe");
        registerTrigger("on_item_held", "When the holder changes held item slot", "wand", "sword", "axe", "pickaxe", "shovel", "hoe", "bow", "crossbow", "trident");
        registerTrigger("on_death", "When the holder dies", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_join", "When the holder joins the server", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");

        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("always"), AlwaysCondition::fromConfig));
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("compare_attribute"), CompareAttributeCondition::fromConfig));
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("spell_element"), SpellElementCondition::fromConfig));
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("arcane_knowledge"), ArcaneKnowledgeCondition::fromConfig));
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("mana"), ManaCondition::fromConfig));
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("has_discovered_spell"), HasDiscoveredSpellCondition::fromConfig));
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("circle_level"), CircleLevelCondition::fromConfig));
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("random"), RandomCondition::fromConfig));
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("permission"), PermissionCondition::fromConfig));
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("world"), WorldCondition::fromConfig));
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("event_parameter"), EventParameterCondition::fromConfig));

        MagicItemRegistries.EFFECT_TYPES.register(new EffectType(
                MagicKeys.alkatraz("ignite"), IgniteEffect::fromConfig));
        MagicItemRegistries.EFFECT_TYPES.register(new EffectType(
                MagicKeys.alkatraz("play_sound"), PlaySoundEffect::fromConfig));
        MagicItemRegistries.EFFECT_TYPES.register(new EffectType(
                MagicKeys.alkatraz("potion_effect"), ApplyPotionEffect::fromConfig));
        MagicItemRegistries.EFFECT_TYPES.register(new EffectType(
                MagicKeys.alkatraz("damage"), DamageEffect::fromConfig));
        MagicItemRegistries.EFFECT_TYPES.register(new EffectType(
                MagicKeys.alkatraz("heal"), HealEffect::fromConfig));
        MagicItemRegistries.EFFECT_TYPES.register(new EffectType(
                MagicKeys.alkatraz("particle"), ParticleEffect::fromConfig));
        MagicItemRegistries.EFFECT_TYPES.register(new EffectType(
                MagicKeys.alkatraz("teleport"), TeleportEffect::fromConfig));
        MagicItemRegistries.EFFECT_TYPES.register(new EffectType(
                MagicKeys.alkatraz("command"), CommandEffect::fromConfig));
        MagicItemRegistries.EFFECT_TYPES.register(new EffectType(
                MagicKeys.alkatraz("message"), MessageEffect::fromConfig));
        MagicItemRegistries.EFFECT_TYPES.register(new EffectType(
                MagicKeys.alkatraz("explosion"), ExplosionEffect::fromConfig));
    }

    private static void registerComponent(String key, String description) {
        MagicItemRegistries.COMPONENT_TYPES.register(new ComponentType(MagicKeys.alkatraz(key), description));
    }

    private static void registerAttribute(String key, double defaultValue, String displayName) {
        MagicItemRegistries.ATTRIBUTE_TYPES.register(new AttributeType(MagicKeys.alkatraz(key), defaultValue, displayName));
    }

    private static void registerTrigger(String key, String description, String... allowedTypes) {
        MagicItemRegistries.TRIGGER_TYPES.register(new TriggerType(MagicKeys.alkatraz(key), description, List.of(allowedTypes)));
    }
}
