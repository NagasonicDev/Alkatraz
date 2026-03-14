package me.nagasonic.alkatraz.loot.implementation;

import me.nagasonic.alkatraz.loot.LootInjector;
import me.nagasonic.alkatraz.spells.spellbooks.SpellbookFactory;
import org.bukkit.Material;
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
                .addItem(SpellbookFactory.createCommonRandomSpellbook(), 100)
                .addItem(SpellbookFactory.createUncommonRandomSpellbook(), 30)
                .addItem(new ItemStack(Material.REDSTONE_LAMP), 100)
                .maxItems(5)
                .register();
        
        // Dungeon chests - mix of common and uncommon
        LootInjector.builder()
                .forLootTable(
                        "chests/simple_dungeon",
                        "chests/abandoned_mineshaft"
                )
                .addItem(SpellbookFactory.createCommonRandomSpellbook(), 80)
                .addItem(SpellbookFactory.createUncommonRandomSpellbook(), 60)
                .addItem(SpellbookFactory.createRareRandomSpellbook(), 20)
                .maxItems(1)
                .register();
        
        // Nether structures - fire spells more common
        LootInjector.builder()
                .forLootTable(
                        "chests/nether_bridge",
                        "chests/bastion"
                )
                .addItem(SpellbookFactory.createFireRandomSpellbook(), 100)
                .addItem(SpellbookFactory.createRareRandomSpellbook(), 50)
                .addItem(SpellbookFactory.createEpicRandomSpellbook(), 20)
                .maxItems(1)
                .register();
        
        // End structures - rare/epic spellbooks
        LootInjector.builder()
                .forLootTable(
                        "chests/end_city_treasure"
                )
                .addItem(SpellbookFactory.createRareRandomSpellbook(), 100)
                .addItem(SpellbookFactory.createEpicRandomSpellbook(), 80)
                .addItem(SpellbookFactory.createAirRandomSpellbook(), 60)
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
                .addItem(SpellbookFactory.createFireRandomSpellbook(), 100)
                .addItem(SpellbookFactory.createRareRandomSpellbook(), 40)
                .maxItems(1)
                .register();
        
        // Ocean Monument - water spells
        LootInjector.builder()
                .forLootTable("chests/underwater_ruin")
                .addItem(SpellbookFactory.createWaterRandomSpellbook(), 100)
                .addItem(SpellbookFactory.createUncommonRandomSpellbook(), 50)
                .maxItems(1)
                .register();
        
        // Stronghold - rare and epic
        LootInjector.builder()
                .forLootTable(
                        "chests/stronghold_library",
                        "chests/stronghold_corridor"
                )
                .addItem(SpellbookFactory.createRareRandomSpellbook(), 100)
                .addItem(SpellbookFactory.createEpicRandomSpellbook(), 60)
                .addItem(SpellbookFactory.createDarkRandomSpellbook(), 40)
                .maxItems(1)
                .register();
        
        // Woodland Mansion - dark spells
        LootInjector.builder()
                .forLootTable("chests/woodland_mansion")
                .addItem(SpellbookFactory.createDarkRandomSpellbook(), 100)
                .addItem(SpellbookFactory.createEpicRandomSpellbook(), 80)
                .maxItems(1)
                .register();
    }
    
    /**
     * Mob drops - creatures drop spellbooks
     */
    private static void registerMobDrops() {
        // Zombie - common spellbooks (5% chance)
        LootInjector.builder()
                .forLootTable("entities/zombie")
                .addItem(SpellbookFactory.createCommonRandomSpellbook(), 100)
                .addItem(new ItemStack(Material.AIR), 1900) // 95% chance of nothing
                .maxItems(1)
                .register();
        
        // Skeleton - common/uncommon (5% chance)
        LootInjector.builder()
                .forLootTable("entities/skeleton")
                .addItem(SpellbookFactory.createCommonRandomSpellbook(), 60)
                .addItem(SpellbookFactory.createUncommonRandomSpellbook(), 40)
                .addItem(new ItemStack(Material.AIR), 1900)
                .maxItems(1)
                .register();
        
        // Witch - uncommon/rare (15% chance)
        LootInjector.builder()
                .forLootTable("entities/witch")
                .addItem(SpellbookFactory.createUncommonRandomSpellbook(), 80)
                .addItem(SpellbookFactory.createRareRandomSpellbook(), 60)
                .addItem(SpellbookFactory.createDarkRandomSpellbook(), 40)
                .addItem(new ItemStack(Material.AIR), 520) // 85% nothing
                .maxItems(1)
                .register();
        
        // Evoker - rare/epic (50% chance)
        LootInjector.builder()
                .forLootTable("entities/evoker")
                .addItem(SpellbookFactory.createRareRandomSpellbook(), 70)
                .addItem(SpellbookFactory.createEpicRandomSpellbook(), 50)
                .addItem(SpellbookFactory.createDarkRandomSpellbook(), 30)
                .addItem(new ItemStack(Material.AIR), 150) // 50% nothing
                .maxItems(1)
                .register();
        
        // Blaze - fire spellbooks (20% chance)
        LootInjector.builder()
                .forLootTable("entities/blaze")
                .addItem(SpellbookFactory.createFireRandomSpellbook(), 100)
                .addItem(SpellbookFactory.createFireballSpellbook(), 50)
                .addItem(new ItemStack(Material.AIR), 350) // 80% nothing
                .maxItems(1)
                .register();
        
        // Drowned - water spellbooks (10% chance)
        LootInjector.builder()
                .forLootTable("entities/drowned")
                .addItem(SpellbookFactory.createWaterRandomSpellbook(), 100)
                .addItem(SpellbookFactory.createWaterPulseSpellbook(), 40)
                .addItem(new ItemStack(Material.AIR), 860) // 90% nothing
                .maxItems(1)
                .register();
        
        // Enderman - dark/air spellbooks (8% chance)
        LootInjector.builder()
                .forLootTable("entities/enderman")
                .addItem(SpellbookFactory.createDarkRandomSpellbook(), 70)
                .addItem(SpellbookFactory.createAirRandomSpellbook(), 50)
                .addItem(SpellbookFactory.createDarkTendrilsSpellbook(), 30)
                .addItem(new ItemStack(Material.AIR), 1150) // 92% nothing
                .maxItems(1)
                .register();
        
        // Wither Skeleton - rare/dark (12% chance)
        LootInjector.builder()
                .forLootTable("entities/wither_skeleton")
                .addItem(SpellbookFactory.createRareRandomSpellbook(), 70)
                .addItem(SpellbookFactory.createDarkRandomSpellbook(), 80)
                .addItem(new ItemStack(Material.AIR), 683) // 88% nothing
                .maxItems(1)
                .register();
        
        // Elder Guardian - epic/water (100% chance - boss mob)
        LootInjector.builder()
                .forLootTable("entities/elder_guardian")
                .addItem(SpellbookFactory.createEpicRandomSpellbook(), 60)
                .addItem(SpellbookFactory.createWaterRandomSpellbook(), 40)
                .maxItems(1)
                .register();
        
        // Ender Dragon - guaranteed epic (100% chance)
        LootInjector.builder()
                .forLootTable("entities/ender_dragon")
                .addItem(SpellbookFactory.createEpicRandomSpellbook(), 100)
                .maxItems(2) // 2 spellbooks!
                .register();
        
        // Wither - guaranteed epic (100% chance)
        LootInjector.builder()
                .forLootTable("entities/wither")
                .addItem(SpellbookFactory.createEpicRandomSpellbook(), 80)
                .addItem(SpellbookFactory.createDarkRandomSpellbook(), 60)
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
                .addItem(SpellbookFactory.createRareRandomSpellbook(), 30)
                .addItem(SpellbookFactory.createWaterRandomSpellbook(), 50)
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
                .addItem(SpellbookFactory.createFireRandomSpellbook(), 60)
                .addItem(SpellbookFactory.createUncommonRandomSpellbook(), 40)
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
