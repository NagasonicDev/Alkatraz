package me.nagasonic.alkatraz.loot.implementation;

import me.nagasonic.alkatraz.loot.LootInjector;
import me.nagasonic.alkatraz.loot.MobLootInjector;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.spellbooks.SpellbookFactory;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

/**
 * Pre-configured loot injections for spellbooks
 * 
 * This class sets up all spellbook drops in various locations.
 * You can easily modify weights and locations here.
 */
public class SpellbookLoot {
    
    /**
     * Registers all spellbook loot injections
     */
    public static void registerAll() {
        registerChestLoot();
        registerMobDrops();
        registerStructureLoot();
        registerFishingLoot();
        registerPiglinBartering();
    }
    
    /**
     * Chest loot - general chests in various structures
     */
    private static void registerChestLoot() {
        // Common chests - common spellbooks only
        LootInjector.builder()
                .forLootTable(
                        "minecraft:chests/village",
                        "chests/shipwreck_map",
                        "chests/buried_treasure",
                        "chests/igloo_chest"
                )
                .addItem(SpellbookFactory.createRandomSpellBook(1), 100)
                .addItem(SpellbookFactory.createRandomSpellBook(1, 2), 30)
                .addItem(new ItemStack(Material.REDSTONE_LAMP), 100)
                .maxItems(5)
                .register();
        
        // Dungeon chests - mix of common and uncommon
        LootInjector.builder()
                .forLootTable(
                        "chests/simple_dungeon",
                        "chests/abandoned_mineshaft"
                )
                .addItem(SpellbookFactory.createRandomSpellBook(1, 2), 80)
                .addItem(SpellbookFactory.createRandomSpellBook(1, 2, 3), 60)
                .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), 20)
                .maxItems(1)
                .register();
        
        // Nether structures - fire spells more common
        LootInjector.builder()
                .forLootTable(
                        "chests/nether_bridge",
                        "chests/bastion"
                )
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.FIRE), 100)
                .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), 50)
                .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), 20)
                .maxItems(1)
                .register();
        
        // End structures - rare/epic spellbooks
        LootInjector.builder()
                .forLootTable(
                        "chests/end_city_treasure"
                )
                .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), 100)
                .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), 80)
                .addItem(SpellbookFactory.createRandomSpellBook(6, 7, 8, 9), 40)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.AIR), 60)
                .maxItems(2) // Up to 2 spellbooks
                .register();
    }
    
    /**
     * Structure-specific loot with themed spellbooks
     */
    private static void registerStructureLoot() {
        // Desert Pyramid - fire spells
        LootInjector.builder()
                .forLootTable("chests/desert_pyramid")
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.FIRE), 100)
                .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), 40)
                .maxItems(1)
                .register();
        
        // Ocean Monument - water spells
        LootInjector.builder()
                .forLootTable("chests/underwater_ruin")
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.WATER), 100)
                .addItem(SpellbookFactory.createRandomSpellBook(1, 2, 3), 50)
                .maxItems(1)
                .register();
        
        // Stronghold - rare and epic
        LootInjector.builder()
                .forLootTable(
                        "chests/stronghold_library",
                        "chests/stronghold_corridor"
                )
                .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), 100)
                .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), 60)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), 40)
                .maxItems(1)
                .register();
        
        // Woodland Mansion - dark spells
        LootInjector.builder()
                .forLootTable("chests/woodland_mansion")
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), 100)
                .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), 80)
                .maxItems(1)
                .register();
    }
    
    /**
     * Mob drops - creatures drop spellbooks
     */
    private static void registerMobDrops() {
        // Zombie - common spellbooks (5% chance)
        MobLootInjector.builder()
                .forEntity(EntityType.ZOMBIE)
                .addItem(SpellbookFactory.createRandomSpellBook(1, 2), 100)
                .addItem(new ItemStack(Material.AIR), 1900) // 95% chance of nothing
                .maxItems(1)
                .register();
        
        // Skeleton - common/uncommon (5% chance)
        MobLootInjector.builder()
                .forEntity(EntityType.SKELETON)
                .addItem(SpellbookFactory.createRandomSpellBook(1, 2), 60)
                .addItem(SpellbookFactory.createRandomSpellBook(1, 2, 3), 40)
                .addItem(new ItemStack(Material.AIR), 1900)
                .maxItems(1)
                .register();
        
        // Witch - uncommon/rare (15% chance)
        MobLootInjector.builder()
                .forEntity(EntityType.WITCH)
                .addItem(SpellbookFactory.createRandomSpellBook(1, 2, 3), 80)
                .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), 60)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), 40)
                .addItem(new ItemStack(Material.AIR), 520) // 85% nothing
                .maxItems(1)
                .register();
        
        // Evoker - rare/epic (50% chance)
        MobLootInjector.builder()
                .forEntity(EntityType.EVOKER)
                .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), 70)
                .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), 50)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), 30)
                .addItem(new ItemStack(Material.AIR), 150) // 50% nothing
                .maxItems(1)
                .register();
        
        // Blaze - fire spellbooks (20% chance)
        MobLootInjector.builder()
                .forEntity(EntityType.BLAZE)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.FIRE), 100)
                .addItem(SpellbookFactory.createSpellBook(SpellRegistry.getSpell("fire_blast")), 50)
                .addItem(new ItemStack(Material.AIR), 350) // 80% nothing
                .maxItems(1)
                .register();
        
        // Drowned - water spellbooks (10% chance)
        MobLootInjector.builder()
                .forEntity(EntityType.DROWNED)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.WATER), 100)
                .addItem(SpellbookFactory.createSpellBook(SpellRegistry.getSpell("water_pulse")), 40)
                .addItem(new ItemStack(Material.AIR), 860) // 90% nothing
                .maxItems(1)
                .register();
        
        // Enderman - dark/air spellbooks (8% chance)
        MobLootInjector.builder()
                .forEntity(EntityType.ENDERMAN)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), 70)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.AIR), 50)
                .addItem(SpellbookFactory.createSpellBook(SpellRegistry.getSpell("dark_tendrils")), 30)
                .addItem(new ItemStack(Material.AIR), 1150) // 92% nothing
                .maxItems(1)
                .register();
        
        // Wither Skeleton - rare/dark (12% chance)
        MobLootInjector.builder()
                .forEntity(EntityType.WITHER_SKELETON)
                .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), 70)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), 80)
                .addItem(new ItemStack(Material.AIR), 683) // 88% nothing
                .maxItems(1)
                .register();
        
        // Elder Guardian - epic/water (100% chance - boss mob)
        MobLootInjector.builder()
                .forEntity(EntityType.ELDER_GUARDIAN)
                .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), 60)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.WATER), 40)
                .maxItems(1)
                .register();
        
        // Ender Dragon - guaranteed epic (100% chance)
        MobLootInjector.builder()
                .forEntity(EntityType.ENDER_DRAGON)
                .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), 80)
                .addItem(SpellbookFactory.createRandomSpellBook(7, 8, 9), 20)
                .maxItems(2) // 2 spellbooks!
                .register();
        
        // Wither - guaranteed epic (100% chance)
        MobLootInjector.builder()
                .forEntity(EntityType.WITHER)
                .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), 80)
                .addItem(SpellbookFactory.createRandomSpellBook(7, 8, 9), 20)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), 60)
                .maxItems(2) // 2 spellbooks!
                .register();
    }
    
    /**
     * Fishing loot
     */
    private static void registerFishingLoot() {
        // Treasure while fishing - rare spellbooks (very low chance)
        LootInjector.builder()
                .forLootTable("gameplay/fishing/treasure")
                .addItem(SpellbookFactory.createRandomSpellBook(1, 2, 3, 4, 5, 6), 30)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.WATER), 50)
                .addItem(new ItemStack(Material.AIR), 920) // 92% nothing
                .maxItems(1)
                .register();
    }
    
    /**
     * Piglin bartering
     */
    private static void registerPiglinBartering() {
        // Piglin barter - fire spellbooks (10% chance)
        LootInjector.builder()
                .forLootTable("gameplay/piglin_bartering")
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.FIRE), 60)
                .addItem(SpellbookFactory.createRandomSpellBook(3, 4, 5, 6), 40)
                .addItem(new ItemStack(Material.AIR), 900) // 90% nothing
                .maxItems(1)
                .register();
    }
    
    /**
     * Unregisters all spellbook loot
     */
    public static void unregisterAll() {
        LootInjector.unregisterAll();
    }
}
