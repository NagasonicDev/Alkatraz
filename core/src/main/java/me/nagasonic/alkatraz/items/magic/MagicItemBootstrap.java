package me.nagasonic.alkatraz.items.magic;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.attribute.AttributeService;
import me.nagasonic.alkatraz.api.magic.attribute.AttributeType;
import me.nagasonic.alkatraz.items.magic.attribute.EquipmentAttributeSource;
import me.nagasonic.alkatraz.items.magic.attribute.StatPointsAttributeSource;
import me.nagasonic.alkatraz.api.magic.component.ComponentHandlerRegistry;
import me.nagasonic.alkatraz.items.magic.imbue.ImbueManager;
import me.nagasonic.alkatraz.api.magic.component.ComponentType;
import me.nagasonic.alkatraz.items.magic.component.handler.wand.WandComponentHandler;
import me.nagasonic.alkatraz.items.magic.component.handler.grimoire.GrimoireComponentHandler;
import me.nagasonic.alkatraz.items.magic.component.handler.scroll.ScrollComponentHandler;
import me.nagasonic.alkatraz.items.magic.condition.implementation.AlwaysCondition;
import me.nagasonic.alkatraz.items.magic.condition.implementation.ArcaneKnowledgeCondition;
import me.nagasonic.alkatraz.items.magic.condition.implementation.CircleLevelCondition;
import me.nagasonic.alkatraz.items.magic.condition.implementation.CompareAttributeCondition;
import me.nagasonic.alkatraz.items.magic.condition.implementation.CooldownCondition;
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
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentChangeListener;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentReconcileTask;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentService;
import me.nagasonic.alkatraz.api.magic.equipment.EquipmentSlot;
import me.nagasonic.alkatraz.items.magic.equipment.StorageSlotResolver;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.items.magic.persistence.ItemDataKeys;
import me.nagasonic.alkatraz.items.magic.recipe.MagicItemRecipeManager;
import me.nagasonic.alkatraz.items.magic.recipe.adapter.CraftingEventRouter;
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
        CraftingEventRouter.registerDefaultAdapters();

        EquipmentService equipmentService = new EquipmentService();
        equipmentService.registerVirtualSlot(EquipmentSlot.RING, new StorageSlotResolver(EquipmentSlot.RING));
        equipmentService.registerVirtualSlot(EquipmentSlot.NECKLACE, new StorageSlotResolver(EquipmentSlot.NECKLACE));
        equipmentService.registerVirtualSlot(EquipmentSlot.BRACELET, new StorageSlotResolver(EquipmentSlot.BRACELET));
        equipmentService.registerVirtualSlot(EquipmentSlot.PENDANT, new StorageSlotResolver(EquipmentSlot.PENDANT));
        AttributeService attributeService = AttributeService.getInstance();
        attributeService.registerSource(new EquipmentAttributeSource(equipmentService));
        attributeService.registerSource(StatPointsAttributeSource.getInstance());

        TriggerPipeline triggerPipeline = new TriggerPipeline(equipmentService);
        MagicItemService itemService = new MagicItemService(triggerPipeline);

        ComponentHandlerRegistry.register(new WandComponentHandler());
        ComponentHandlerRegistry.register(new ScrollComponentHandler());
        ComponentHandlerRegistry.register(new GrimoireComponentHandler());

        MagicItemServices.initialize(itemService, attributeService, equipmentService);
        EquipmentChangeListener.tryRegister(Alkatraz.getInstance());
        EquipmentReconcileTask.start();
        int registeredRecipes = MagicItemRecipeManager.registerRecipes();
        ImbueManager.initialize();
        int imbuingRecipes = MagicItemRecipeManager.registerImbuingRecipes();
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        Alkatraz.logInfo("Registered " + registeredRecipes + " recipes and " + imbuingRecipes + " imbuing recipes.");
        Alkatraz.logVeryHigh("MagicItemBootstrap initialization completed in " + elapsed + "ms");
    }

    public static void reload() {
        MagicItemRecipeManager.unregisterAll();
        MagicItemRegistries.ITEM_DEFINITIONS.clear();
        MagicItemRegistries.ENGRAVING_DEFINITIONS.clear();
        loadDefinitions();
        CraftingEventRouter.registerDefaultAdapters();
        MagicItemRecipeManager.registerRecipes();
        ImbueManager.initialize();
        MagicItemRecipeManager.registerImbuingRecipes();
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
        MagicItemService.saveDefaultResource("magic/items/imbued_tier1.yml");
        MagicItemService.saveDefaultResource("magic/items/imbued_tier2.yml");
        MagicItemService.saveDefaultResource("magic/items/imbued_tier3.yml");
        MagicItemService.saveDefaultResource("magic/items/imbued_tier4.yml");
        MagicItemService.saveDefaultResource("magic/items/imbued_tier5.yml");
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
        registerTrigger("on_damage_dealt", "When the holder deals damage to an entity", "sword", "axe", "wand", "bow", "crossbow", "trident");
        registerTrigger("on_projectile_hit", "When a projectile fired by the holder hits an entity", "bow", "crossbow", "trident");
        registerTrigger("on_equip", "When equipped in a slot", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_unequip", "When unequipped from a slot", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_damage_taken", "When the holder takes damage", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_interact_entity", "When the holder interacts with an entity", "wand", "sword", "axe");
        registerTrigger("on_item_held", "When the holder changes held item slot", "wand", "sword", "axe", "pickaxe", "shovel", "hoe", "bow", "crossbow", "trident");
        registerTrigger("on_death", "When the holder dies", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_join", "When the holder joins the server", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_sneak", "When the holder starts sneaking", "helmet", "chestplate", "leggings", "boots");
        registerTrigger("on_stop_sneak", "When the holder stops sneaking", "helmet", "chestplate", "leggings", "boots");

        // --- Combat triggers ---
        registerTrigger("on_melee_hit", "When the holder lands a melee hit", "sword", "axe", "trident");
        registerTrigger("on_player_attacked", "When the holder is attacked by another player", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_critical_hit", "When the holder lands a critical melee hit", "sword", "axe", "trident");
        registerTrigger("on_blocked", "When the holder blocks with a shield");
        registerTrigger("on_projectile_launch", "When the holder launches a projectile", "bow", "crossbow", "trident");
        registerTrigger("on_throw", "When the holder throws a throwable item", "bow", "crossbow", "trident");
        registerTrigger("on_bow_release", "When the holder releases a bow", "bow", "crossbow");
        registerTrigger("on_bow_pull", "When the holder starts pulling a bow", "bow", "crossbow");
        registerTrigger("on_projectile_hit_block", "When a projectile fired by the holder hits a block", "bow", "crossbow", "trident");
        registerTrigger("on_ranged_kill", "When the holder kills an entity with a ranged weapon", "bow", "crossbow", "trident");
        registerTrigger("on_targeted", "When a hostile entity targets the holder", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_arm_swing", "When the holder swings their arm", "wand", "sword", "axe", "pickaxe", "shovel", "hoe", "bow", "crossbow", "trident");

        // --- Movement triggers ---
        registerTrigger("on_sprint", "When the holder starts sprinting", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_stop_sprint", "When the holder stops sprinting", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_swim", "When the holder starts swimming", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_stop_swim", "When the holder stops swimming", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_glide", "When the holder starts gliding", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_stop_glide", "When the holder stops gliding", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_start_fly", "When the holder starts flying", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_stop_fly", "When the holder stops flying", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_enter_vehicle", "When the holder enters a vehicle", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_leave_vehicle", "When the holder leaves a vehicle", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_enter_portal", "When the holder enters a portal", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_teleport", "When the holder teleports", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_world_change", "When the holder changes world", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_bed_enter", "When the holder enters a bed", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_bed_leave", "When the holder leaves a bed", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_enter_biome", "When the holder enters a biome", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_jump", "When the holder jumps", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_land", "When the holder lands after falling", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_enter_water", "When the holder enters water", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_enter_lava", "When the holder enters lava", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_climb", "When the holder starts climbing", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_step_on_block", "When the holder steps onto a block", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");

        // --- Environment triggers ---
        registerTrigger("on_lightning", "When the holder is struck by lightning", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_explosion_damage", "When the holder takes explosion damage", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_fire_damage", "When the holder takes fire damage", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_drown", "When the holder drowns", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_freeze", "When the holder takes freeze damage", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_fall_damage", "When the holder takes fall damage", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_void", "When the holder falls into the void", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_starve", "When the holder takes starvation damage", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_suffocate", "When the holder suffocates", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_rain", "When it starts raining", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_thunder", "When a thunderstorm starts", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");

        // --- Inventory triggers ---
        registerTrigger("on_use_item", "When the holder uses an item");
        registerTrigger("on_left_click_item", "When the holder left-clicks with an item");
        registerTrigger("on_swap_hands", "When the holder swaps hands");
        registerTrigger("on_pickup_item", "When the holder picks up an item");
        registerTrigger("on_drop_item", "When the holder drops an item");
        registerTrigger("on_consume_item", "When the holder consumes an item");
        registerTrigger("on_item_damage", "When an item takes durability damage", "sword", "axe", "pickaxe", "shovel", "hoe", "bow", "crossbow", "trident");
        registerTrigger("on_item_break", "When an item breaks", "sword", "axe", "pickaxe", "shovel", "hoe", "bow", "crossbow", "trident");
        registerTrigger("on_repair", "When the holder repairs an item");
        registerTrigger("on_enchant", "When the holder enchants an item");
        registerTrigger("on_open_inventory", "When the holder opens an inventory");
        registerTrigger("on_close_inventory", "When the holder closes an inventory");

        // --- Gathering triggers ---
        registerTrigger("on_break_block", "When the holder breaks a block", "pickaxe", "shovel", "hoe", "sword", "axe");
        registerTrigger("on_mine_ore", "When the holder mines ore", "pickaxe", "shovel", "hoe", "sword", "axe");
        registerTrigger("on_place_block", "When the holder places a block", "pickaxe", "shovel", "hoe", "sword", "axe");
        registerTrigger("on_fish", "When the holder catches a fish", "sword", "axe", "pickaxe", "shovel", "hoe");
        registerTrigger("on_shear", "When the holder shears an entity", "sword", "axe", "pickaxe", "shovel", "hoe");
        registerTrigger("on_fill_bucket", "When the holder fills a bucket", "sword", "axe", "pickaxe", "shovel", "hoe");
        registerTrigger("on_empty_bucket", "When the holder empties a bucket", "sword", "axe", "pickaxe", "shovel", "hoe");

        // --- Player state triggers ---
        registerTrigger("on_respawn", "When the holder respawns", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_level_up", "When the holder levels up", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_xp_gain", "When the holder gains experience", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_food_change", "When the holder's food level changes", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_heal", "When the holder heals", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_low_health", "When the holder's health drops to 20% or below", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_first_join", "When the holder joins for the first time", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_quit", "When the holder quits the server", "helmet", "chestplate", "leggings", "boots", "ring", "necklace", "bracelet", "pendant");
        registerTrigger("on_spell_casted", "When the holder's spell completes casting", "wand");
        registerTrigger("on_spell_fail", "When the holder's spell fails to cast", "wand");
        registerTrigger("on_mana_use", "When the holder spends mana", "wand");
        registerTrigger("on_mana_empty", "When the holder's mana runs out", "wand");
        registerTrigger("on_spell_discovered", "When the holder discovers a spell", "wand");
        registerTrigger("on_research_complete", "When the holder completes a research", "wand");

        // --- Social triggers ---
        registerTrigger("on_chat", "When the holder sends a chat message");
        registerTrigger("on_command", "When the holder runs a command");
        registerTrigger("on_villager_trade", "When the holder trades with a villager");

        // --- Periodic triggers ---
        registerTrigger("on_interval", "Every second while equipped");
        registerTrigger("on_day", "When day starts");
        registerTrigger("on_night", "When night starts");

        // --- WorldGuard triggers ---
        registerTrigger("on_region_enter", "When the holder enters a WorldGuard region");
        registerTrigger("on_region_leave", "When the holder leaves a WorldGuard region");

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
        MagicItemRegistries.CONDITION_TYPES.register(new ConditionType(
                MagicKeys.alkatraz("cooldown"), CooldownCondition::fromConfig));

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
