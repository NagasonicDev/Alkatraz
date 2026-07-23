package me.nagasonic.alkatraz.loot.implementation;

import me.nagasonic.alkatraz.config.SpellbookConfig;
import me.nagasonic.alkatraz.loot.LootInjector;
import me.nagasonic.alkatraz.loot.MobLootInjector;
import me.nagasonic.alkatraz.spells.Element;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.spellbooks.SpellbookFactory;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/**
 * Pre-configured loot injections for spellbooks.
 * All values are read from config.yml via SpellbookConfig.
 */
public class SpellbookLoot {

    public static void registerAll() {
        registerChestLoot();
        registerMobDrops();
        registerStructureLoot();
        registerFishingLoot();
        registerPiglinBartering();
    }

    private static void registerChestLoot() {
        if (!SpellbookConfig.isChestLootEnabled()) return;
        double m = SpellbookConfig.getLootMultiplier();

        if (SpellbookConfig.isChestCategoryEnabled("common")) {
            int sbW = (int)(SpellbookConfig.getChestInt("common", "spellbook_weight", 100) * m);
            int sb12W = (int)(SpellbookConfig.getChestInt("common", "spellbook_1_2_weight", 30) * m);
            int filler = SpellbookConfig.getChestInt("common", "filler_weight", 100);
            int max = SpellbookConfig.getChestInt("common", "max_items", 5);
            LootInjector.builder()
                    .forLootTable("minecraft:chests/village", "chests/shipwreck_map", "chests/buried_treasure", "chests/igloo_chest")
                    .addItem(SpellbookFactory.createRandomSpellBook(1), sbW)
                    .addItem(SpellbookFactory.createRandomSpellBook(1, 2), sb12W)
                    .addItem(new ItemStack(Material.REDSTONE_LAMP), filler)
                    .maxItems(max)
                    .register();
        }

        if (SpellbookConfig.isChestCategoryEnabled("dungeon")) {
            int sb12W = (int)(SpellbookConfig.getChestInt("dungeon", "spellbook_1_2_weight", 80) * m);
            int sb13W = (int)(SpellbookConfig.getChestInt("dungeon", "spellbook_1_3_weight", 60) * m);
            int sb24W = (int)(SpellbookConfig.getChestInt("dungeon", "spellbook_2_4_weight", 20) * m);
            int max = SpellbookConfig.getChestInt("dungeon", "max_items", 1);
            LootInjector.builder()
                    .forLootTable("chests/simple_dungeon", "chests/abandoned_mineshaft")
                    .addItem(SpellbookFactory.createRandomSpellBook(1, 2), sb12W)
                    .addItem(SpellbookFactory.createRandomSpellBook(1, 2, 3), sb13W)
                    .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), sb24W)
                    .maxItems(max)
                    .register();
        }

        if (SpellbookConfig.isChestCategoryEnabled("nether")) {
            int fireW = (int)(SpellbookConfig.getChestInt("nether", "fire_element_weight", 100) * m);
            int sb24W = (int)(SpellbookConfig.getChestInt("nether", "spellbook_2_4_weight", 50) * m);
            int sb46W = (int)(SpellbookConfig.getChestInt("nether", "spellbook_4_6_weight", 20) * m);
            int max = SpellbookConfig.getChestInt("nether", "max_items", 1);
            LootInjector.builder()
                    .forLootTable("chests/nether_bridge", "chests/bastion")
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.FIRE), fireW)
                    .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), sb24W)
                    .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), sb46W)
                    .maxItems(max)
                    .register();
        }

        if (SpellbookConfig.isChestCategoryEnabled("end")) {
            int sb24W = (int)(SpellbookConfig.getChestInt("end", "spellbook_2_4_weight", 100) * m);
            int sb46W = (int)(SpellbookConfig.getChestInt("end", "spellbook_4_6_weight", 80) * m);
            int sb69W = (int)(SpellbookConfig.getChestInt("end", "spellbook_6_9_weight", 40) * m);
            int airW = (int)(SpellbookConfig.getChestInt("end", "air_element_weight", 60) * m);
            int max = SpellbookConfig.getChestInt("end", "max_items", 2);
            LootInjector.builder()
                    .forLootTable("chests/end_city_treasure")
                    .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), sb24W)
                    .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), sb46W)
                    .addItem(SpellbookFactory.createRandomSpellBook(6, 7, 8, 9), sb69W)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.AIR), airW)
                    .maxItems(max)
                    .register();
        }
    }

    private static void registerStructureLoot() {
        if (!SpellbookConfig.isStructureLootEnabled()) return;
        double m = SpellbookConfig.getLootMultiplier();

        if (SpellbookConfig.isStructureCategoryEnabled("desert_pyramid")) {
            int fireW = (int)(SpellbookConfig.getStructureInt("desert_pyramid", "fire_element_weight", 100) * m);
            int sbW = (int)(SpellbookConfig.getStructureInt("desert_pyramid", "spellbook_2_4_weight", 40) * m);
            int max = SpellbookConfig.getStructureInt("desert_pyramid", "max_items", 1);
            LootInjector.builder()
                    .forLootTable("chests/desert_pyramid")
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.FIRE), fireW)
                    .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), sbW)
                    .maxItems(max)
                    .register();
        }

        if (SpellbookConfig.isStructureCategoryEnabled("ocean_ruins")) {
            int waterW = (int)(SpellbookConfig.getStructureInt("ocean_ruins", "water_element_weight", 100) * m);
            int sbW = (int)(SpellbookConfig.getStructureInt("ocean_ruins", "spellbook_1_3_weight", 50) * m);
            int max = SpellbookConfig.getStructureInt("ocean_ruins", "max_items", 1);
            LootInjector.builder()
                    .forLootTable("chests/underwater_ruin")
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.WATER), waterW)
                    .addItem(SpellbookFactory.createRandomSpellBook(1, 2, 3), sbW)
                    .maxItems(max)
                    .register();
        }

        if (SpellbookConfig.isStructureCategoryEnabled("stronghold")) {
            int sb24W = (int)(SpellbookConfig.getStructureInt("stronghold", "spellbook_2_4_weight", 100) * m);
            int sb46W = (int)(SpellbookConfig.getStructureInt("stronghold", "spellbook_4_6_weight", 60) * m);
            int darkW = (int)(SpellbookConfig.getStructureInt("stronghold", "dark_element_weight", 40) * m);
            int max = SpellbookConfig.getStructureInt("stronghold", "max_items", 1);
            LootInjector.builder()
                    .forLootTable("chests/stronghold_library", "chests/stronghold_corridor")
                    .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), sb24W)
                    .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), sb46W)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), darkW)
                    .maxItems(max)
                    .register();
        }

        if (SpellbookConfig.isStructureCategoryEnabled("woodland_mansion")) {
            int darkW = (int)(SpellbookConfig.getStructureInt("woodland_mansion", "dark_element_weight", 100) * m);
            int sbW = (int)(SpellbookConfig.getStructureInt("woodland_mansion", "spellbook_2_4_weight", 80) * m);
            int max = SpellbookConfig.getStructureInt("woodland_mansion", "max_items", 1);
            LootInjector.builder()
                    .forLootTable("chests/woodland_mansion")
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), darkW)
                    .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), sbW)
                    .maxItems(max)
                    .register();
        }
    }

    private static void registerMobDrops() {
        if (!SpellbookConfig.isMobDropsEnabled()) return;
        double m = SpellbookConfig.getLootMultiplier();

        registerSimpleMob("zombie", EntityType.ZOMBIE, m,
                new String[][]{ new String[]{"1,2", "spellbook_weight"} });
        registerSimpleMob("stray", EntityType.STRAY, m,
                new String[][]{ new String[]{"1,2", "spellbook_weight"} });

        if (SpellbookConfig.isMobEnabled("skeleton")) {
            int w12 = (int)(SpellbookConfig.getMobInt("skeleton", "spellbook_1_2_weight", 60) * m);
            int w13 = (int)(SpellbookConfig.getMobInt("skeleton", "spellbook_1_3_weight", 40) * m);
            int filler = SpellbookConfig.getMobInt("skeleton", "filler_weight", 1900);
            MobLootInjector.builder()
                    .forEntity(EntityType.SKELETON)
                    .addItem(SpellbookFactory.createRandomSpellBook(1, 2), w12)
                    .addItem(SpellbookFactory.createRandomSpellBook(1, 2, 3), w13)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("witch")) {
            int w13 = (int)(SpellbookConfig.getMobInt("witch", "spellbook_1_3_weight", 80) * m);
            int w24 = (int)(SpellbookConfig.getMobInt("witch", "spellbook_2_4_weight", 60) * m);
            int dark = (int)(SpellbookConfig.getMobInt("witch", "dark_element_weight", 40) * m);
            int filler = SpellbookConfig.getMobInt("witch", "filler_weight", 520);
            MobLootInjector.builder()
                    .forEntity(EntityType.WITCH)
                    .addItem(SpellbookFactory.createRandomSpellBook(1, 2, 3), w13)
                    .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), w24)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), dark)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("piglin")) {
            int fire = (int)(SpellbookConfig.getMobInt("piglin", "fire_element_weight", 80) * m);
            int w23 = (int)(SpellbookConfig.getMobInt("piglin", "spellbook_2_3_weight", 60) * m);
            int filler = SpellbookConfig.getMobInt("piglin", "filler_weight", 1360);
            MobLootInjector.builder()
                    .forEntity(EntityType.PIGLIN)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.FIRE), fire)
                    .addItem(SpellbookFactory.createRandomSpellBook(2, 3), w23)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("hoglin")) {
            int w23 = (int)(SpellbookConfig.getMobInt("hoglin", "spellbook_2_3_weight", 100) * m);
            int filler = SpellbookConfig.getMobInt("hoglin", "filler_weight", 900);
            MobLootInjector.builder()
                    .forEntity(EntityType.HOGLIN)
                    .addItem(SpellbookFactory.createRandomSpellBook(2, 3), w23)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("phantom")) {
            int air = (int)(SpellbookConfig.getMobInt("phantom", "air_element_weight", 80) * m);
            int w12 = (int)(SpellbookConfig.getMobInt("phantom", "spellbook_1_2_weight", 50) * m);
            int filler = SpellbookConfig.getMobInt("phantom", "filler_weight", 1370);
            MobLootInjector.builder()
                    .forEntity(EntityType.PHANTOM)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.AIR), air)
                    .addItem(SpellbookFactory.createRandomSpellBook(1, 2), w12)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("guardian")) {
            int water = (int)(SpellbookConfig.getMobInt("guardian", "water_element_weight", 100) * m);
            int w23 = (int)(SpellbookConfig.getMobInt("guardian", "spellbook_2_3_weight", 60) * m);
            int filler = SpellbookConfig.getMobInt("guardian", "filler_weight", 1040);
            MobLootInjector.builder()
                    .forEntity(EntityType.GUARDIAN)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.WATER), water)
                    .addItem(SpellbookFactory.createRandomSpellBook(2, 3), w23)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("evoker")) {
            int w24 = (int)(SpellbookConfig.getMobInt("evoker", "spellbook_2_4_weight", 70) * m);
            int w46 = (int)(SpellbookConfig.getMobInt("evoker", "spellbook_4_6_weight", 50) * m);
            int dark = (int)(SpellbookConfig.getMobInt("evoker", "dark_element_weight", 30) * m);
            int filler = SpellbookConfig.getMobInt("evoker", "filler_weight", 150);
            MobLootInjector.builder()
                    .forEntity(EntityType.EVOKER)
                    .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), w24)
                    .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), w46)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), dark)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("vindicator")) {
            int dark = (int)(SpellbookConfig.getMobInt("vindicator", "dark_element_weight", 80) * m);
            int w23 = (int)(SpellbookConfig.getMobInt("vindicator", "spellbook_2_3_weight", 60) * m);
            int filler = SpellbookConfig.getMobInt("vindicator", "filler_weight", 340);
            MobLootInjector.builder()
                    .forEntity(EntityType.VINDICATOR)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), dark)
                    .addItem(SpellbookFactory.createRandomSpellBook(2, 3), w23)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("ravager")) {
            int earth = (int)(SpellbookConfig.getMobInt("ravager", "earth_element_weight", 100) * m);
            int w34 = (int)(SpellbookConfig.getMobInt("ravager", "spellbook_3_4_weight", 70) * m);
            int filler = SpellbookConfig.getMobInt("ravager", "filler_weight", 203);
            MobLootInjector.builder()
                    .forEntity(EntityType.RAVAGER)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.EARTH), earth)
                    .addItem(SpellbookFactory.createRandomSpellBook(3, 4), w34)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("blaze")) {
            int fire = (int)(SpellbookConfig.getMobInt("blaze", "fire_element_weight", 100) * m);
            int specific = (int)(SpellbookConfig.getMobInt("blaze", "specific_spellbook_weight", 50) * m);
            int filler = SpellbookConfig.getMobInt("blaze", "filler_weight", 350);
            MobLootInjector.builder()
                    .forEntity(EntityType.BLAZE)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.FIRE), fire)
                    .addItem(SpellbookFactory.createSpellBook(SpellRegistry.getSpell("fire_blast")), specific)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("drowned")) {
            int water = (int)(SpellbookConfig.getMobInt("drowned", "water_element_weight", 100) * m);
            int specific = (int)(SpellbookConfig.getMobInt("drowned", "specific_spellbook_weight", 40) * m);
            int filler = SpellbookConfig.getMobInt("drowned", "filler_weight", 860);
            MobLootInjector.builder()
                    .forEntity(EntityType.DROWNED)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.WATER), water)
                    .addItem(SpellbookFactory.createSpellBook(SpellRegistry.getSpell("water_pulse")), specific)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("enderman")) {
            int dark = (int)(SpellbookConfig.getMobInt("enderman", "dark_element_weight", 70) * m);
            int air = (int)(SpellbookConfig.getMobInt("enderman", "air_element_weight", 50) * m);
            int specific = (int)(SpellbookConfig.getMobInt("enderman", "specific_spellbook_weight", 20) * m);
            int filler = SpellbookConfig.getMobInt("enderman", "filler_weight", 1150);
            MobLootInjector.builder()
                    .forEntity(EntityType.ENDERMAN)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), dark)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.AIR), air)
                    .addItem(SpellbookFactory.createSpellBook(SpellRegistry.getSpell("blink")), specific)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("wither_skeleton")) {
            int w24 = (int)(SpellbookConfig.getMobInt("wither_skeleton", "spellbook_2_4_weight", 70) * m);
            int dark = (int)(SpellbookConfig.getMobInt("wither_skeleton", "dark_element_weight", 80) * m);
            int filler = SpellbookConfig.getMobInt("wither_skeleton", "filler_weight", 683);
            MobLootInjector.builder()
                    .forEntity(EntityType.WITHER_SKELETON)
                    .addItem(SpellbookFactory.createRandomSpellBook(2, 3, 4), w24)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), dark)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("elder_guardian")) {
            int water = (int)(SpellbookConfig.getMobInt("elder_guardian", "water_element_weight", 100) * m);
            int w46 = (int)(SpellbookConfig.getMobInt("elder_guardian", "spellbook_4_6_weight", 60) * m);
            int max = SpellbookConfig.getMobInt("elder_guardian", "max_items", 2);
            MobLootInjector.builder()
                    .forEntity(EntityType.ELDER_GUARDIAN)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.WATER), water)
                    .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), w46)
                    .maxItems(max)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("ender_dragon")) {
            int air = (int)(SpellbookConfig.getMobInt("ender_dragon", "air_element_weight", 100) * m);
            int w46 = (int)(SpellbookConfig.getMobInt("ender_dragon", "spellbook_4_6_weight", 80) * m);
            int w79 = (int)(SpellbookConfig.getMobInt("ender_dragon", "spellbook_7_9_weight", 20) * m);
            int max = SpellbookConfig.getMobInt("ender_dragon", "max_items", 2);
            MobLootInjector.builder()
                    .forEntity(EntityType.ENDER_DRAGON)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.AIR), air)
                    .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), w46)
                    .addItem(SpellbookFactory.createRandomSpellBook(7, 8, 9), w79)
                    .maxItems(max)
                    .register();
        }

        if (SpellbookConfig.isMobEnabled("wither")) {
            int dark = (int)(SpellbookConfig.getMobInt("wither", "dark_element_weight", 100) * m);
            int w46 = (int)(SpellbookConfig.getMobInt("wither", "spellbook_4_6_weight", 80) * m);
            int w79 = (int)(SpellbookConfig.getMobInt("wither", "spellbook_7_9_weight", 20) * m);
            int max = SpellbookConfig.getMobInt("wither", "max_items", 2);
            MobLootInjector.builder()
                    .forEntity(EntityType.WITHER)
                    .addItem(SpellbookFactory.createRandomElementSpellBook(Element.DARK), dark)
                    .addItem(SpellbookFactory.createRandomSpellBook(4, 5, 6), w46)
                    .addItem(SpellbookFactory.createRandomSpellBook(7, 8, 9), w79)
                    .maxItems(max)
                    .register();
        }
    }

    private static void registerSimpleMob(String configKey, EntityType entityType, double multiplier, String[][] spellbookEntries) {
        if (!SpellbookConfig.isMobEnabled(configKey)) return;
        MobLootInjector.Builder builder = MobLootInjector.builder().forEntity(entityType);
        for (String[] entry : spellbookEntries) {
            String circles = entry[0];
            int weight = (int)(SpellbookConfig.getMobInt(configKey, entry[1], 100) * multiplier);
            int[] circleArr = parseCircles(circles);
            builder.addItem(SpellbookFactory.createRandomSpellBook(circleArr), weight);
        }
        int filler = SpellbookConfig.getMobInt(configKey, "filler_weight", 1900);
        builder.addItem(new ItemStack(Material.AIR), filler).maxItems(1).register();
    }

    private static int[] parseCircles(String csv) {
        String[] parts = csv.split(",");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Integer.parseInt(parts[i].trim());
        }
        return result;
    }

    private static void registerFishingLoot() {
        if (!SpellbookConfig.isFishingEnabled()) return;
        double m = SpellbookConfig.getLootMultiplier();

        int sbW = (int)(SpellbookConfig.getFishingInt("random_spellbook_weight", 40) * m);
        int waterW = (int)(SpellbookConfig.getFishingInt("water_element_weight", 50) * m);
        int airW = (int)(SpellbookConfig.getFishingInt("air_element_weight", 30) * m);
        int lightW = (int)(SpellbookConfig.getFishingInt("light_element_weight", 20) * m);
        int max = SpellbookConfig.getFishingInt("max_items", 2);

        LootInjector.builder()
                .forLootTable("gameplay/fishing/treasure")
                .addItem(SpellbookFactory.createRandomSpellBook(1, 2, 3, 4, 5, 6), sbW)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.WATER), waterW)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.AIR), airW)
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.LIGHT), lightW)
                .maxItems(max)
                .register();
    }

    private static void registerPiglinBartering() {
        if (!SpellbookConfig.isPiglinBarteringEnabled()) return;
        double m = SpellbookConfig.getLootMultiplier();

        int fireW = (int)(SpellbookConfig.getPiglinInt("fire_element_weight", 60) * m);
        int sbW = (int)(SpellbookConfig.getPiglinInt("random_spellbook_weight", 40) * m);
        int nothingW = SpellbookConfig.getPiglinInt("nothing_weight", 900);
        int max = SpellbookConfig.getPiglinInt("max_items", 1);

        LootInjector.builder()
                .forLootTable("gameplay/piglin_bartering")
                .addItem(SpellbookFactory.createRandomElementSpellBook(Element.FIRE), fireW)
                .addItem(SpellbookFactory.createRandomSpellBook(3, 4, 5, 6), sbW)
                .addItem(new ItemStack(Material.AIR), nothingW)
                .maxItems(max)
                .register();
    }

    public static void unregisterAll() {
        LootInjector.unregisterAll();
    }
}
