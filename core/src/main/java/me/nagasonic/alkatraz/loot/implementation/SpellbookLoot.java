package me.nagasonic.alkatraz.loot.implementation;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.SpellbookConfig;
import me.nagasonic.alkatraz.loot.LootInjector;
import me.nagasonic.alkatraz.loot.MobLootInjector;
import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import me.nagasonic.alkatraz.spells.spellbooks.SpellbookFactory;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

/**
 * Pre-configured loot injections for spellbooks.
 *
 * <p>Non-loot values are read from config.yml via {@link SpellbookConfig}; everything
 * under {@code loot:} is read from loot.yml.
 *
 * <p><b>Drop chance.</b> {@code book weight / (book weight + filler_weight)}. The
 * {@code loot_multiplier} scales the book, element and specific weights but NOT
 * {@code filler_weight}, {@code nothing_weight} or {@code max_items}, so raising it
 * raises the drop CHANCE and not only the mix. That asymmetry is why the two farmable
 * bosses - elder guardian and wither - got ballast added this wave: a guaranteed
 * high-circle book per kill is an infinite supply of the rarest tier.
 *
 * <p><b>Every entry may be absent.</b> {@link SpellbookFactory} returns {@code null}
 * when it cannot find a single matching spell, and {@code specificBook} returns
 * {@code null} for an unregistered id. Both are skipped with a warning rather than
 * registered, because this class runs from {@code onEnable} and a throw there stops the
 * plugin loading. {@code addIfPresent} is what makes that true.
 */
public class SpellbookLoot {

    public static void registerAll() {
        registerChestLoot();
        registerMobDrops();
        registerStructureLoot();
        registerFishingLoot();
        registerPiglinBartering();
    }

    /**
     * A random element book, capped by loot.element_book_caps.
     *
     * @param capKey the {@code element_book_caps} key, e.g. {@code "mob.blaze"}
     * @param def    the cap to use when the key is absent from loot.yml
     */
    private static ItemStack elementBook(Element element, String capKey, int def) {
        return SpellbookFactory.createRandomElementSpellBook(
                element, SpellbookConfig.getElementMaxCircle(capKey, def));
    }

    /**
     * A random book from an explicit circle band.
     *
     * @return null when no spell matches, which the caller skips.
     */
    private static ItemStack randomBook(int... circles) {
        return SpellbookFactory.createRandomSpellBook(circles);
    }

    /**
     * The fixed signature spellbook for {@code spellId}, or {@code null} if it is not
     * registered.
     *
     * <p>This replaces three copy-pasted
     * {@code createSpellBook(SpellRegistry.getSpell("fire_blast"))} calls, which NPE'd
     * inside {@code createSpellBook} when the id was missing - and because
     * {@code registerAll()} runs from {@code onEnable}, renaming a spell hard-crashed
     * plugin enable rather than dropping one loot entry. Now a rename logs one warning
     * and skips one entry.
     */
    private static ItemStack specificBook(String spellId) {
        Spell spell = SpellRegistry.getSpell(spellId);
        if (spell == null) {
            Alkatraz.logWarning("SpellbookLoot: specific spellbook '" + spellId
                    + "' is not registered. That loot entry was skipped.");
            return null;
        }
        return SpellbookFactory.createSpellBook(spell);
    }

    /** The id of a mob's signature drop, from loot.yml. Data, not a code literal. */
    private static String specificId(String mob, String def) {
        return SpellbookConfig.getMobString(mob, "specific_spell", def);
    }

    private static LootInjector.Builder addIfPresent(LootInjector.Builder builder, ItemStack item, int weight) {
        if (item == null) return builder;
        return builder.addItem(item, weight);
    }

    private static MobLootInjector.Builder addIfPresent(MobLootInjector.Builder builder, ItemStack item, int weight) {
        if (item == null) return builder;
        return builder.addItem(item, weight);
    }

    // ==========================================
    // CHEST LOOT
    // ==========================================

    private static void registerChestLoot() {
        if (!SpellbookConfig.isChestLootEnabled()) return;
        double m = SpellbookConfig.getLootMultiplier();

        if (SpellbookConfig.isChestCategoryEnabled("common")) {
            int sbW = (int) (SpellbookConfig.getChestInt("common", "spellbook_weight", 100) * m);
            int sb12W = (int) (SpellbookConfig.getChestInt("common", "spellbook_1_2_weight", 30) * m);
            int filler = SpellbookConfig.getChestInt("common", "filler_weight", 100);
            int max = SpellbookConfig.getChestInt("common", "max_items", 5);
            LootInjector.Builder builder = LootInjector.builder()
                    .forLootTable("minecraft:chests/village", "chests/shipwreck_map",
                            "chests/buried_treasure", "chests/igloo_chest")
                    .addItem(new ItemStack(Material.REDSTONE_LAMP), filler)
                    .maxItems(max);
            addIfPresent(builder, randomBook(1), sbW);
            addIfPresent(builder, randomBook(1, 2), sb12W);
            builder.register();
        }

        if (SpellbookConfig.isChestCategoryEnabled("dungeon")) {
            int sb12W = (int) (SpellbookConfig.getChestInt("dungeon", "spellbook_1_2_weight", 80) * m);
            int sb13W = (int) (SpellbookConfig.getChestInt("dungeon", "spellbook_1_3_weight", 60) * m);
            int sb24W = (int) (SpellbookConfig.getChestInt("dungeon", "spellbook_2_4_weight", 20) * m);
            int max = SpellbookConfig.getChestInt("dungeon", "max_items", 1);
            // No filler: a dungeon is a guaranteed single book. That is the design.
            LootInjector.Builder builder = LootInjector.builder()
                    .forLootTable("chests/simple_dungeon", "chests/abandoned_mineshaft")
                    .maxItems(max);
            addIfPresent(builder, randomBook(1, 2), sb12W);
            addIfPresent(builder, randomBook(1, 2, 3), sb13W);
            addIfPresent(builder, randomBook(2, 3, 4), sb24W);
            builder.register();
        }

        if (SpellbookConfig.isChestCategoryEnabled("nether")) {
            int fireW = (int) (SpellbookConfig.getChestInt("nether", "fire_element_weight", 100) * m);
            int sb24W = (int) (SpellbookConfig.getChestInt("nether", "spellbook_2_4_weight", 50) * m);
            int sb46W = (int) (SpellbookConfig.getChestInt("nether", "spellbook_4_6_weight", 20) * m);
            int max = SpellbookConfig.getChestInt("nether", "max_items", 1);
            // Cap 6 matches this category's own (4,5,6) band. Uncapped, a nether bridge
            // could hand over inferno_rite at 30/20130 = 0.149%.
            LootInjector.Builder builder = LootInjector.builder()
                    .forLootTable("chests/nether_bridge", "chests/bastion")
                    .maxItems(max);
            addIfPresent(builder, elementBook(Element.FIRE, "chest.nether", 6), fireW);
            addIfPresent(builder, randomBook(2, 3, 4), sb24W);
            addIfPresent(builder, randomBook(4, 5, 6), sb46W);
            builder.register();
        }

        if (SpellbookConfig.isChestCategoryEnabled("end")) {
            int sb24W = (int) (SpellbookConfig.getChestInt("end", "spellbook_2_4_weight", 100) * m);
            int sb46W = (int) (SpellbookConfig.getChestInt("end", "spellbook_4_6_weight", 80) * m);
            int sb69W = (int) (SpellbookConfig.getChestInt("end", "spellbook_6_9_weight", 40) * m);
            int airW = (int) (SpellbookConfig.getChestInt("end", "air_element_weight", 60) * m);
            int max = SpellbookConfig.getChestInt("end", "max_items", 2);
            // AIR stays uncapped at 9. An End city is the endgame structure and one of
            // only three places a circle-9 AIR capstone should be obtainable.
            // The (6,7,8,9) band was DEAD until wave 5 - circles 7/8/9 held no spells,
            // so this book shipped as spellbook_type=random, spell_count=0, which prints
            // spellbook.empty on right-click and is never consumed.
            LootInjector.Builder builder = LootInjector.builder()
                    .forLootTable("chests/end_city_treasure")
                    .maxItems(max);
            addIfPresent(builder, randomBook(2, 3, 4), sb24W);
            addIfPresent(builder, randomBook(4, 5, 6), sb46W);
            addIfPresent(builder, randomBook(6, 7, 8, 9), sb69W);
            addIfPresent(builder, elementBook(Element.AIR, "chest.end", 9), airW);
            builder.register();
        }
    }

    // ==========================================
    // STRUCTURE LOOT
    // ==========================================

    private static void registerStructureLoot() {
        if (!SpellbookConfig.isStructureLootEnabled()) return;
        double m = SpellbookConfig.getLootMultiplier();

        if (SpellbookConfig.isStructureCategoryEnabled("desert_pyramid")) {
            int fireW = (int) (SpellbookConfig.getStructureInt("desert_pyramid", "fire_element_weight", 100) * m);
            int sbW = (int) (SpellbookConfig.getStructureInt("desert_pyramid", "spellbook_2_4_weight", 40) * m);
            int max = SpellbookConfig.getStructureInt("desert_pyramid", "max_items", 1);
            // Cap 4 matches the (2,3,4) band. A desert pyramid is reachable at any
            // progression point and is not behind the progression gate that
            // flame_lance (circle 5) and meteor_shower (circle 6) exist to enforce.
            LootInjector.Builder builder = LootInjector.builder()
                    .forLootTable("chests/desert_pyramid")
                    .maxItems(max);
            addIfPresent(builder, elementBook(Element.FIRE, "structure.desert_pyramid", 4), fireW);
            addIfPresent(builder, randomBook(2, 3, 4), sbW);
            builder.register();
        }

        if (SpellbookConfig.isStructureCategoryEnabled("ocean_ruins")) {
            int waterW = (int) (SpellbookConfig.getStructureInt("ocean_ruins", "water_element_weight", 100) * m);
            int sbW = (int) (SpellbookConfig.getStructureInt("ocean_ruins", "spellbook_1_3_weight", 50) * m);
            int max = SpellbookConfig.getStructureInt("ocean_ruins", "max_items", 1);
            LootInjector.Builder builder = LootInjector.builder()
                    .forLootTable("chests/underwater_ruin")
                    .maxItems(max);
            addIfPresent(builder, elementBook(Element.WATER, "structure.ocean_ruins", 3), waterW);
            addIfPresent(builder, randomBook(1, 2, 3), sbW);
            builder.register();
        }

        if (SpellbookConfig.isStructureCategoryEnabled("stronghold")) {
            int sb24W = (int) (SpellbookConfig.getStructureInt("stronghold", "spellbook_2_4_weight", 100) * m);
            int sb46W = (int) (SpellbookConfig.getStructureInt("stronghold", "spellbook_4_6_weight", 60) * m);
            int darkW = (int) (SpellbookConfig.getStructureInt("stronghold", "dark_element_weight", 40) * m);
            int max = SpellbookConfig.getStructureInt("stronghold", "max_items", 1);
            // Cap 6 matches the (4,5,6) band. DARK holds no spell below circle 3, so an
            // uncapped DARK book was circles 3-9 and a stronghold library had a
            // 30/5130 = 0.585% chance of handing out oblivion, a circle-9 capstone.
            LootInjector.Builder builder = LootInjector.builder()
                    .forLootTable("chests/stronghold_library", "chests/stronghold_corridor")
                    .maxItems(max);
            addIfPresent(builder, randomBook(2, 3, 4), sb24W);
            addIfPresent(builder, randomBook(4, 5, 6), sb46W);
            addIfPresent(builder, elementBook(Element.DARK, "structure.stronghold", 6), darkW);
            builder.register();
        }

        if (SpellbookConfig.isStructureCategoryEnabled("woodland_mansion")) {
            int darkW = (int) (SpellbookConfig.getStructureInt("woodland_mansion", "dark_element_weight", 100) * m);
            int sbW = (int) (SpellbookConfig.getStructureInt("woodland_mansion", "spellbook_2_4_weight", 80) * m);
            int max = SpellbookConfig.getStructureInt("woodland_mansion", "max_items", 1);
            LootInjector.Builder builder = LootInjector.builder()
                    .forLootTable("chests/woodland_mansion")
                    .maxItems(max);
            addIfPresent(builder, elementBook(Element.DARK, "structure.woodland_mansion", 4), darkW);
            addIfPresent(builder, randomBook(2, 3, 4), sbW);
            builder.register();
        }
    }

    // ==========================================
    // MOB DROPS
    // ==========================================

    private static void registerMobDrops() {
        if (!SpellbookConfig.isMobDropsEnabled()) return;
        double m = SpellbookConfig.getLootMultiplier();

        registerSimpleMob("zombie", EntityType.ZOMBIE, m, "spellbook_weight", 1, 2);
        registerSimpleMob("stray", EntityType.STRAY, m, "spellbook_weight", 1, 2);

        if (SpellbookConfig.isMobEnabled("skeleton")) {
            int w12 = (int) (SpellbookConfig.getMobInt("skeleton", "spellbook_1_2_weight", 60) * m);
            int w13 = (int) (SpellbookConfig.getMobInt("skeleton", "spellbook_1_3_weight", 40) * m);
            int filler = SpellbookConfig.getMobInt("skeleton", "filler_weight", 1900);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.SKELETON)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1);
            addIfPresent(builder, randomBook(1, 2), w12);
            addIfPresent(builder, randomBook(1, 2, 3), w13);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("witch")) {
            int w13 = (int) (SpellbookConfig.getMobInt("witch", "spellbook_1_3_weight", 80) * m);
            int w24 = (int) (SpellbookConfig.getMobInt("witch", "spellbook_2_4_weight", 60) * m);
            int dark = (int) (SpellbookConfig.getMobInt("witch", "dark_element_weight", 40) * m);
            int filler = SpellbookConfig.getMobInt("witch", "filler_weight", 520);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.WITCH)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1);
            addIfPresent(builder, randomBook(1, 2, 3), w13);
            addIfPresent(builder, randomBook(2, 3, 4), w24);
            addIfPresent(builder, elementBook(Element.DARK, "mob.witch", 4), dark);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("piglin")) {
            int fire = (int) (SpellbookConfig.getMobInt("piglin", "fire_element_weight", 80) * m);
            int w23 = (int) (SpellbookConfig.getMobInt("piglin", "spellbook_2_3_weight", 60) * m);
            int filler = SpellbookConfig.getMobInt("piglin", "filler_weight", 1360);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.PIGLIN)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1);
            addIfPresent(builder, elementBook(Element.FIRE, "mob.piglin", 3), fire);
            addIfPresent(builder, randomBook(2, 3), w23);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("hoglin")) {
            int w23 = (int) (SpellbookConfig.getMobInt("hoglin", "spellbook_2_3_weight", 100) * m);
            int filler = SpellbookConfig.getMobInt("hoglin", "filler_weight", 900);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.HOGLIN)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1);
            addIfPresent(builder, randomBook(2, 3), w23);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("phantom")) {
            // Cap 2 matches the phantom's own (1,2) band. AIR holds THREE spells at
            // circle 2 (air_blades, swift, wind_vortex), so under the old per-spell
            // weighting an uncapped AIR book gave circle 2 15000 of a 30130-point
            // budget - 49.8% of the book out of a single circle. Capped at 2 it is
            // 5000 of 15000, so circle 2 is a third.
            int air = (int) (SpellbookConfig.getMobInt("phantom", "air_element_weight", 80) * m);
            int w12 = (int) (SpellbookConfig.getMobInt("phantom", "spellbook_1_2_weight", 50) * m);
            int filler = SpellbookConfig.getMobInt("phantom", "filler_weight", 1370);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.PHANTOM)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1);
            addIfPresent(builder, elementBook(Element.AIR, "mob.phantom", 2), air);
            addIfPresent(builder, randomBook(1, 2), w12);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("guardian")) {
            int water = (int) (SpellbookConfig.getMobInt("guardian", "water_element_weight", 100) * m);
            int w23 = (int) (SpellbookConfig.getMobInt("guardian", "spellbook_2_3_weight", 60) * m);
            int filler = SpellbookConfig.getMobInt("guardian", "filler_weight", 1040);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.GUARDIAN)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1);
            addIfPresent(builder, elementBook(Element.WATER, "mob.guardian", 3), water);
            addIfPresent(builder, randomBook(2, 3), w23);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("evoker")) {
            int w24 = (int) (SpellbookConfig.getMobInt("evoker", "spellbook_2_4_weight", 70) * m);
            int w46 = (int) (SpellbookConfig.getMobInt("evoker", "spellbook_4_6_weight", 50) * m);
            int dark = (int) (SpellbookConfig.getMobInt("evoker", "dark_element_weight", 30) * m);
            // NEW. The evoker's whole identity is summoning and skeletal_knights is the
            // circle-6 summoning spell, so it is the evoker and only the evoker.
            int specific = (int) (SpellbookConfig.getMobInt("evoker", "specific_spellbook_weight", 25) * m);
            // Left at 150 on purpose. ConfigUpdater propagates NEW keys but never changed
            // default values, so editing it would make new and existing installs disagree.
            // P(book) therefore rises 50.000% -> 53.846%, which is the price of the drop.
            int filler = SpellbookConfig.getMobInt("evoker", "filler_weight", 150);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.EVOKER)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1);
            addIfPresent(builder, randomBook(2, 3, 4), w24);
            addIfPresent(builder, randomBook(4, 5, 6), w46);
            addIfPresent(builder, elementBook(Element.DARK, "mob.evoker", 6), dark);
            addIfPresent(builder, specificBook(specificId("evoker", "skeletal_knights")), specific);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("vindicator")) {
            int dark = (int) (SpellbookConfig.getMobInt("vindicator", "dark_element_weight", 80) * m);
            int w23 = (int) (SpellbookConfig.getMobInt("vindicator", "spellbook_2_3_weight", 60) * m);
            int filler = SpellbookConfig.getMobInt("vindicator", "filler_weight", 340);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.VINDICATOR)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1);
            addIfPresent(builder, elementBook(Element.DARK, "mob.vindicator", 3), dark);
            addIfPresent(builder, randomBook(2, 3), w23);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("ravager")) {
            int earth = (int) (SpellbookConfig.getMobInt("ravager", "earth_element_weight", 100) * m);
            int w34 = (int) (SpellbookConfig.getMobInt("ravager", "spellbook_3_4_weight", 70) * m);
            // NEW. Without a (4,5,6) band, EARTH had no circle-6 source from any mob.
            int w46 = (int) (SpellbookConfig.getMobInt("ravager", "spellbook_4_6_weight", 30) * m);
            // NEW. A ravager is a focused slam with heavy knockback, which is what a
            // ravager already is. The only circle-6 EARTH capstone from a mob.
            int specific = (int) (SpellbookConfig.getMobInt("ravager", "specific_spellbook_weight", 30) * m);
            // Left at 203 on purpose - see the evoker comment. P(book) rises
            // 45.576% -> 53.118% because a band and a specific were added.
            int filler = SpellbookConfig.getMobInt("ravager", "filler_weight", 203);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.RAVAGER)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1);
            addIfPresent(builder, elementBook(Element.EARTH, "mob.ravager", 6), earth);
            addIfPresent(builder, randomBook(3, 4), w34);
            addIfPresent(builder, randomBook(4, 5, 6), w46);
            addIfPresent(builder, specificBook(specificId("ravager", "tectonic_strike")), specific);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("blaze")) {
            int fire = (int) (SpellbookConfig.getMobInt("blaze", "fire_element_weight", 100) * m);
            int specific = (int) (SpellbookConfig.getMobInt("blaze", "specific_spellbook_weight", 50) * m);
            int filler = SpellbookConfig.getMobInt("blaze", "filler_weight", 350);
            // Cap 3 is the blaze's own signature circle. A blaze must not be able to
            // hand out flame_lance (120 mana) or meteor_shower (165 mana).
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.BLAZE)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1);
            addIfPresent(builder, elementBook(Element.FIRE, "mob.blaze", 3), fire);
            addIfPresent(builder, specificBook(specificId("blaze", "fire_blast")), specific);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("drowned")) {
            int water = (int) (SpellbookConfig.getMobInt("drowned", "water_element_weight", 100) * m);
            int specific = (int) (SpellbookConfig.getMobInt("drowned", "specific_spellbook_weight", 40) * m);
            int filler = SpellbookConfig.getMobInt("drowned", "filler_weight", 860);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.DROWNED)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1);
            addIfPresent(builder, elementBook(Element.WATER, "mob.drowned", 3), water);
            addIfPresent(builder, specificBook(specificId("drowned", "water_pulse")), specific);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("enderman")) {
            int dark = (int) (SpellbookConfig.getMobInt("enderman", "dark_element_weight", 70) * m);
            int air = (int) (SpellbookConfig.getMobInt("enderman", "air_element_weight", 50) * m);
            int specific = (int) (SpellbookConfig.getMobInt("enderman", "specific_spellbook_weight", 20) * m);
            int filler = SpellbookConfig.getMobInt("enderman", "filler_weight", 1150);
            // Both element books cap at 4, the circle of this mob's own blink.
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.ENDERMAN)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1);
            addIfPresent(builder, elementBook(Element.DARK, "mob.enderman.dark", 4), dark);
            addIfPresent(builder, elementBook(Element.AIR, "mob.enderman.air", 4), air);
            addIfPresent(builder, specificBook(specificId("enderman", "blink")), specific);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("wither_skeleton")) {
            int w24 = (int) (SpellbookConfig.getMobInt("wither_skeleton", "spellbook_2_4_weight", 70) * m);
            int dark = (int) (SpellbookConfig.getMobInt("wither_skeleton", "dark_element_weight", 80) * m);
            int filler = SpellbookConfig.getMobInt("wither_skeleton", "filler_weight", 683);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.WITHER_SKELETON)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(1);
            addIfPresent(builder, randomBook(2, 3, 4), w24);
            addIfPresent(builder, elementBook(Element.DARK, "mob.wither_skeleton", 4), dark);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("elder_guardian")) {
            int water = (int) (SpellbookConfig.getMobInt("elder_guardian", "water_element_weight", 100) * m);
            // Band moved (4,5,6) -> (5,6,7). The elder guardian is the most accessible
            // boss in the game and should sit exactly one circle below the End's bosses.
            // Sum w(5..7) = 1275, giving C5 58.82% / C6 29.41% / C7 11.76%.
            int w57 = (int) (SpellbookConfig.getMobInt("elder_guardian", "spellbook_5_7_weight", 60) * m);
            // NEW. Undertow drags one target toward you and scales with the drag
            // distance. An elder guardian pulls you into the monument.
            int specific = (int) (SpellbookConfig.getMobInt("elder_guardian", "specific_spellbook_weight", 40) * m);
            // NEW ballast. This mob is farmable in an ocean biome, and with max_items 2
            // and no filler both of those rolls were guaranteed books. Against w(7)=150
            // in the 1275-point (5,6,7) band that is 1-(1-0.375*150/1275)^2 = 8.63% for at
            // least one circle-7 book per kill, forever. It is now 2.60%.
            // P(book) falls 100% -> 37.037%.
            int filler = SpellbookConfig.getMobInt("elder_guardian", "filler_weight", 340);
            int max = SpellbookConfig.getMobInt("elder_guardian", "max_items", 2);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.ELDER_GUARDIAN)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(max);
            addIfPresent(builder, elementBook(Element.WATER, "mob.elder_guardian", 7), water);
            addIfPresent(builder, randomBook(5, 6, 7), w57);
            addIfPresent(builder, specificBook(specificId("elder_guardian", "undertow")), specific);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("ender_dragon")) {
            int air = (int) (SpellbookConfig.getMobInt("ender_dragon", "air_element_weight", 100) * m);
            int w46 = (int) (SpellbookConfig.getMobInt("ender_dragon", "spellbook_4_6_weight", 80) * m);
            // DEAD until wave 5: circles 7-9 held no spells, so this book shipped as
            // spell_count=0 and printed spellbook.empty on right-click.
            int w79 = (int) (SpellbookConfig.getMobInt("ender_dragon", "spellbook_7_9_weight", 20) * m);
            // NEW. Skywrath is the only flight-granting spell in the roster and the only
            // circle-9 AIR spell, and the dragon is the mob that flies.
            int specific = (int) (SpellbookConfig.getMobInt("ender_dragon", "specific_spellbook_weight", 25) * m);
            // NO filler, deliberately. The ender dragon is a once-per-world-event boss:
            // there is no farm to protect against and ballast would only reduce the
            // guaranteed reward of the most valuable kill in the game.
            int max = SpellbookConfig.getMobInt("ender_dragon", "max_items", 2);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.ENDER_DRAGON)
                    .maxItems(max);
            addIfPresent(builder, elementBook(Element.AIR, "mob.ender_dragon", 9), air);
            addIfPresent(builder, randomBook(4, 5, 6), w46);
            addIfPresent(builder, randomBook(7, 8, 9), w79);
            addIfPresent(builder, specificBook(specificId("ender_dragon", "skywrath")), specific);
            builder.register();
        }

        if (SpellbookConfig.isMobEnabled("wither")) {
            int dark = (int) (SpellbookConfig.getMobInt("wither", "dark_element_weight", 100) * m);
            int w46 = (int) (SpellbookConfig.getMobInt("wither", "spellbook_4_6_weight", 80) * m);
            // DEAD until wave 5, same as the dragon's.
            int w79 = (int) (SpellbookConfig.getMobInt("wither", "spellbook_7_9_weight", 20) * m);
            // NEW. Oblivion scales with the target's missing health, which is the
            // wither's whole kill mechanic, and dark's circle-9 capstone has no other mob source.
            int specific = (int) (SpellbookConfig.getMobInt("wither", "specific_spellbook_weight", 25) * m);
            // NEW ballast, for the same reason as the elder guardian: a wither fight is
            // repeatable on any end-stone platform. P(book) falls 100% -> 45.000%.
            int filler = SpellbookConfig.getMobInt("wither", "filler_weight", 275);
            int max = SpellbookConfig.getMobInt("wither", "max_items", 2);
            MobLootInjector.Builder builder = MobLootInjector.builder()
                    .forEntity(EntityType.WITHER)
                    .addItem(new ItemStack(Material.AIR), filler)
                    .maxItems(max);
            addIfPresent(builder, elementBook(Element.DARK, "mob.wither", 9), dark);
            addIfPresent(builder, randomBook(4, 5, 6), w46);
            addIfPresent(builder, randomBook(7, 8, 9), w79);
            addIfPresent(builder, specificBook(specificId("wither", "oblivion")), specific);
            builder.register();
        }
    }

    /**
     * A mob with one random book band and nothing else.
     *
     * <p>The circles are passed as ints rather than a {@code "1,2"} csv string so the
     * band is visible where it is declared. The csv form needed an
     * {@code Integer.parseInt} at registration time, which throws out of
     * {@code registerAll()} - and therefore out of {@code onEnable} - if the string is
     * ever malformed, and it hid the band from any test that reads this file.
     *
     * @param weightKey the loot.yml key holding this band's weight
     */
    private static void registerSimpleMob(String configKey, EntityType entityType, double multiplier,
                                          String weightKey, int... circles) {
        if (!SpellbookConfig.isMobEnabled(configKey)) return;
        int weight = (int) (SpellbookConfig.getMobInt(configKey, weightKey, 100) * multiplier);
        int filler = SpellbookConfig.getMobInt(configKey, "filler_weight", 1900);
        MobLootInjector.Builder builder = MobLootInjector.builder()
                .forEntity(entityType)
                .addItem(new ItemStack(Material.AIR), filler)
                .maxItems(1);
        addIfPresent(builder, randomBook(circles), weight);
        builder.register();
    }

    // ==========================================
    // FISHING
    // ==========================================

    private static void registerFishingLoot() {
        if (!SpellbookConfig.isFishingEnabled()) return;
        double m = SpellbookConfig.getLootMultiplier();

        // Band moved (1..6) -> (1..7). Fishing is the only renewable source available at
        // circle 1 with no combat gate, so it keeps the lowest ceiling of the extended
        // set. Sum w(1..7) = 20025, so circle 7 is 150/20025 = 0.749%. The entry is chosen
        // 40/140 = 28.571% per roll with max_items 2, giving
        // 1 - (1 - 0.28571 * 0.00749)^2 = 0.428% for at least one circle-7 book per treasure.
        int sbW = (int) (SpellbookConfig.getFishingInt("random_spellbook_weight", 40) * m);
        int waterW = (int) (SpellbookConfig.getFishingInt("water_element_weight", 50) * m);
        int airW = (int) (SpellbookConfig.getFishingInt("air_element_weight", 30) * m);
        int lightW = (int) (SpellbookConfig.getFishingInt("light_element_weight", 20) * m);
        int max = SpellbookConfig.getFishingInt("max_items", 2);

        LootInjector.Builder builder = LootInjector.builder()
                .forLootTable("gameplay/fishing/treasure")
                .maxItems(max);
        addIfPresent(builder, randomBook(1, 2, 3, 4, 5, 6, 7), sbW);
        addIfPresent(builder, elementBook(Element.WATER, "fishing.water", 7), waterW);
        addIfPresent(builder, elementBook(Element.AIR, "fishing.air", 7), airW);
        addIfPresent(builder, elementBook(Element.LIGHT, "fishing.light", 7), lightW);
        builder.register();
    }

    // ==========================================
    // PIGLIN BARTERING
    // ==========================================

    private static void registerPiglinBartering() {
        if (!SpellbookConfig.isPiglinBarteringEnabled()) return;
        double m = SpellbookConfig.getLootMultiplier();

        // Band moved (3..6) -> (3..7). Bartering is the Nether's renewable source, gated
        // behind bastions and portal access, so it sits one circle above the surface
        // world's. Sum w(3..7) = 5025, so circle 7 is 150/5025 = 2.985%. Against the 900
        // nothing ballast that is 0.04 * 0.02985 = 0.1194% per barter, or 11.3% over 100.
        int fireW = (int) (SpellbookConfig.getPiglinInt("fire_element_weight", 60) * m);
        int sbW = (int) (SpellbookConfig.getPiglinInt("random_spellbook_weight", 40) * m);
        int nothingW = SpellbookConfig.getPiglinInt("nothing_weight", 900);
        int max = SpellbookConfig.getPiglinInt("max_items", 1);

        LootInjector.Builder builder = LootInjector.builder()
                .forLootTable("gameplay/piglin_bartering")
                .addItem(new ItemStack(Material.AIR), nothingW)
                .maxItems(max);
        addIfPresent(builder, elementBook(Element.FIRE, "piglin_bartering.fire", 7), fireW);
        addIfPresent(builder, randomBook(3, 4, 5, 6, 7), sbW);
        builder.register();
    }

    // ==========================================
    // LIFECYCLE
    // ==========================================

    /**
     * Clears BOTH injector registries.
     *
     * <p>The previous version cleared only {@link LootInjector}. Every mob drop rule
     * lives in {@link MobLootInjector}, so an unregister-then-register cycle - which is
     * what {@code /alkatraz reload} now does - would leave all seventeen mob injectors
     * in place and add a second copy of each on top.
     * {@code MobLootInjector.LootListener.onMobDeath} walks the whole list per death, so
     * mob spellbook drops would double with every reload.
     */
    public static void unregisterAll() {
        LootInjector.unregisterAll();
        MobLootInjector.unregisterAll();
    }

    /**
     * Re-reads loot.yml and rebuilds every injector. Call from {@code /alkatraz reload}.
     *
     * <p><b>What this does not fix, stated plainly:</b> a spellbook's circle weights
     * are baked into its ItemStack NBT when it is built, so books already sitting in a
     * player's inventory, or already generated into a chunk's loot, keep the weights
     * they were created with. Editing {@code spellbook.circle_weights} therefore needs
     * a <b>full server restart</b> to apply everywhere; reload only fixes rolls
     * generated from now on. <b>That limitation is accepted.</b> Everything else in
     * loot.yml - which bands exist, which elements, the caps, the specific ids, the
     * weights - takes effect on reload.
     */
    public static void reloadLoot() {
        unregisterAll();
        SpellbookConfig.reload();
        registerAll();
    }
}