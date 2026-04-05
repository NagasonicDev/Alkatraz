package me.nagasonic.alkatraz.dom;

import org.bukkit.Material;
import org.bukkit.block.Biome;

public enum Ground {

    // --- Soil & Dirt ---------------------------------------------------------
    DIRT(Material.DIRT),
    GRASS(Material.GRASS_BLOCK),
    COARSE_DIRT(Material.COARSE_DIRT),
    ROOTED_DIRT(Material.ROOTED_DIRT),
    PODZOL(Material.PODZOL),
    MUD(Material.MUD),
    MUDDY_MANGROVE_ROOTS(Material.MUDDY_MANGROVE_ROOTS),
    MYCELIUM(Material.MYCELIUM),

    // --- Sand & Gravel -------------------------------------------------------
    SAND(Material.SAND),
    RED_SAND(Material.RED_SAND),
    GRAVEL(Material.GRAVEL),
    SUSPICIOUS_SAND(Material.SUSPICIOUS_SAND),

    // --- Clay & Terracotta ---------------------------------------------------
    CLAY(Material.CLAY),
    TERRACOTTA(Material.TERRACOTTA),
    WHITE_TERRACOTTA(Material.WHITE_TERRACOTTA),
    ORANGE_TERRACOTTA(Material.ORANGE_TERRACOTTA),
    MAGENTA_TERRACOTTA(Material.MAGENTA_TERRACOTTA),
    LIGHT_BLUE_TERRACOTTA(Material.LIGHT_BLUE_TERRACOTTA),
    YELLOW_TERRACOTTA(Material.YELLOW_TERRACOTTA),
    LIME_TERRACOTTA(Material.LIME_TERRACOTTA),
    PINK_TERRACOTTA(Material.PINK_TERRACOTTA),
    GRAY_TERRACOTTA(Material.GRAY_TERRACOTTA),
    LIGHT_GRAY_TERRACOTTA(Material.LIGHT_GRAY_TERRACOTTA),
    CYAN_TERRACOTTA(Material.CYAN_TERRACOTTA),
    PURPLE_TERRACOTTA(Material.PURPLE_TERRACOTTA),
    BLUE_TERRACOTTA(Material.BLUE_TERRACOTTA),
    BROWN_TERRACOTTA(Material.BROWN_TERRACOTTA),
    GREEN_TERRACOTTA(Material.GREEN_TERRACOTTA),
    RED_TERRACOTTA(Material.RED_TERRACOTTA),
    BLACK_TERRACOTTA(Material.BLACK_TERRACOTTA),

    // --- Stone & Variants ----------------------------------------------------
    STONE(Material.STONE),
    COBBLESTONE(Material.COBBLESTONE),
    MOSSY_COBBLESTONE(Material.MOSSY_COBBLESTONE),
    STONE_BRICKS(Material.STONE_BRICKS),
    MOSSY_STONE_BRICKS(Material.MOSSY_STONE_BRICKS),
    CRACKED_STONE_BRICKS(Material.CRACKED_STONE_BRICKS),
    CHISELED_STONE_BRICKS(Material.CHISELED_STONE_BRICKS),
    SMOOTH_STONE(Material.SMOOTH_STONE),
    INFESTED_STONE(Material.INFESTED_STONE),
    INFESTED_COBBLESTONE(Material.INFESTED_COBBLESTONE),
    INFESTED_STONE_BRICKS(Material.INFESTED_STONE_BRICKS),

    // --- Deepslate & Variants ------------------------------------------------
    DEEPSLATE(Material.DEEPSLATE),
    COBBLED_DEEPSLATE(Material.COBBLED_DEEPSLATE),
    POLISHED_DEEPSLATE(Material.POLISHED_DEEPSLATE),
    DEEPSLATE_BRICKS(Material.DEEPSLATE_BRICKS),
    CRACKED_DEEPSLATE_BRICKS(Material.CRACKED_DEEPSLATE_BRICKS),
    DEEPSLATE_TILES(Material.DEEPSLATE_TILES),
    CRACKED_DEEPSLATE_TILES(Material.CRACKED_DEEPSLATE_TILES),
    CHISELED_DEEPSLATE(Material.CHISELED_DEEPSLATE),
    INFESTED_DEEPSLATE(Material.INFESTED_DEEPSLATE),

    // --- Granite, Diorite & Andesite -----------------------------------------
    GRANITE(Material.GRANITE),
    POLISHED_GRANITE(Material.POLISHED_GRANITE),
    DIORITE(Material.DIORITE),
    POLISHED_DIORITE(Material.POLISHED_DIORITE),
    ANDESITE(Material.ANDESITE),
    POLISHED_ANDESITE(Material.POLISHED_ANDESITE),

    // --- Ts is so tuff ----------------------------------------------------------------
    TUFF(Material.TUFF),

    // --- Calcite & Dripstone -------------------------------------------------
    CALCITE(Material.CALCITE),
    DRIPSTONE_BLOCK(Material.DRIPSTONE_BLOCK),
    POINTED_DRIPSTONE(Material.POINTED_DRIPSTONE),

    // --- Sandstone -----------------------------------------------------------
    SANDSTONE(Material.SANDSTONE),
    CHISELED_SANDSTONE(Material.CHISELED_SANDSTONE),
    CUT_SANDSTONE(Material.CUT_SANDSTONE),
    SMOOTH_SANDSTONE(Material.SMOOTH_SANDSTONE),
    RED_SANDSTONE(Material.RED_SANDSTONE),
    CHISELED_RED_SANDSTONE(Material.CHISELED_RED_SANDSTONE),
    CUT_RED_SANDSTONE(Material.CUT_RED_SANDSTONE),
    SMOOTH_RED_SANDSTONE(Material.SMOOTH_RED_SANDSTONE),

    // --- Ores ----------------------------------------------------------------
    COAL_ORE(Material.COAL_ORE),
    DEEPSLATE_COAL_ORE(Material.DEEPSLATE_COAL_ORE),
    IRON_ORE(Material.IRON_ORE),
    DEEPSLATE_IRON_ORE(Material.DEEPSLATE_IRON_ORE),
    COPPER_ORE(Material.COPPER_ORE),
    DEEPSLATE_COPPER_ORE(Material.DEEPSLATE_COPPER_ORE),
    GOLD_ORE(Material.GOLD_ORE),
    DEEPSLATE_GOLD_ORE(Material.DEEPSLATE_GOLD_ORE),
    NETHER_GOLD_ORE(Material.NETHER_GOLD_ORE),
    REDSTONE_ORE(Material.REDSTONE_ORE),
    DEEPSLATE_REDSTONE_ORE(Material.DEEPSLATE_REDSTONE_ORE),
    LAPIS_ORE(Material.LAPIS_ORE),
    DEEPSLATE_LAPIS_ORE(Material.DEEPSLATE_LAPIS_ORE),
    DIAMOND_ORE(Material.DIAMOND_ORE),
    DEEPSLATE_DIAMOND_ORE(Material.DEEPSLATE_DIAMOND_ORE),
    EMERALD_ORE(Material.EMERALD_ORE),
    DEEPSLATE_EMERALD_ORE(Material.DEEPSLATE_EMERALD_ORE),
    NETHER_QUARTZ_ORE(Material.NETHER_QUARTZ_ORE),
    ANCIENT_DEBRIS(Material.ANCIENT_DEBRIS),

    // --- Nether Ground -------------------------------------------------------
    NETHERRACK(Material.NETHERRACK),
    SOUL_SAND(Material.SOUL_SAND),
    SOUL_SOIL(Material.SOUL_SOIL),
    BASALT(Material.BASALT),
    SMOOTH_BASALT(Material.SMOOTH_BASALT),
    POLISHED_BASALT(Material.POLISHED_BASALT),
    BLACKSTONE(Material.BLACKSTONE),
    GILDED_BLACKSTONE(Material.GILDED_BLACKSTONE),
    POLISHED_BLACKSTONE(Material.POLISHED_BLACKSTONE),
    CHISELED_POLISHED_BLACKSTONE(Material.CHISELED_POLISHED_BLACKSTONE),
    POLISHED_BLACKSTONE_BRICKS(Material.POLISHED_BLACKSTONE_BRICKS),
    CRACKED_POLISHED_BLACKSTONE_BRICKS(Material.CRACKED_POLISHED_BLACKSTONE_BRICKS),
    MAGMA_BLOCK(Material.MAGMA_BLOCK),

    // --- End Ground ----------------------------------------------------------
    END_STONE(Material.END_STONE),
    END_STONE_BRICKS(Material.END_STONE_BRICKS);

    // -------------------------------------------------------------------------

    private final Material type;

    Ground(Material type) {
        this.type = type;
    }

    public Material getType() {
        return this.type;
    }

    public static boolean isGround(Material material) {
        for (Ground ground : Ground.values()) {
            if (ground.type.equals(material)) return true;
        }
        return false;
    }

    /**
     * Returns true only for loose, unstable ground that would realistically
     * shift or crumble — useful for spells that scatter or sink terrain.
     */
    public static boolean isLoose(Material material) {
        return switch (material) {
            case DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL, MUD,
                 SAND, RED_SAND, GRAVEL, CLAY,
                 SOUL_SAND, SOUL_SOIL -> true;
            default -> false;
        };
    }

    /**
     * Returns true for stone-tier and harder blocks — useful for spells that
     * should only affect solid rock, like a fissure or boulder throw.
     */
    public static boolean isRock(Material material) {
        return switch (material) {
            case STONE, COBBLESTONE, MOSSY_COBBLESTONE, SMOOTH_STONE,
                 STONE_BRICKS, MOSSY_STONE_BRICKS, CRACKED_STONE_BRICKS, CHISELED_STONE_BRICKS,
                 DEEPSLATE, COBBLED_DEEPSLATE, POLISHED_DEEPSLATE,
                 DEEPSLATE_BRICKS, CRACKED_DEEPSLATE_BRICKS, DEEPSLATE_TILES, CRACKED_DEEPSLATE_TILES,
                 GRANITE, POLISHED_GRANITE, DIORITE, POLISHED_DIORITE, ANDESITE, POLISHED_ANDESITE,
                 TUFF, CALCITE,
                 SANDSTONE, SMOOTH_SANDSTONE, RED_SANDSTONE, SMOOTH_RED_SANDSTONE,
                 BASALT, SMOOTH_BASALT, BLACKSTONE, NETHERRACK,
                 END_STONE, END_STONE_BRICKS -> true;
            default -> false;
        };
    }

    /**
     * Returns true for ore-bearing blocks — useful for spells that interact
     * with the earth's mineral content, like a divination or vein-burst effect.
     */
    public static boolean isOre(Material material) {
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE,
                 IRON_ORE, DEEPSLATE_IRON_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                 LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                 NETHER_QUARTZ_ORE, ANCIENT_DEBRIS -> true;
            default -> false;
        };
    }

    public static Material getGround(Biome biome) {
        return switch (biome) {
            case DESERT                -> SAND.getType();
            case BADLANDS,
                 WOODED_BADLANDS,
                 ERODED_BADLANDS       -> RED_SAND.getType();
            case MUSHROOM_FIELDS       -> MYCELIUM.getType();
            case SOUL_SAND_VALLEY      -> SOUL_SAND.getType();
            case BASALT_DELTAS         -> BASALT.getType();
            case CRIMSON_FOREST,
                 WARPED_FOREST,
                 NETHER_WASTES         -> NETHERRACK.getType();
            case THE_END,
                 END_HIGHLANDS,
                 END_MIDLANDS,
                 END_BARRENS,
                 SMALL_END_ISLANDS     -> END_STONE.getType();
            case BEACH,
                 STONY_SHORE           -> GRAVEL.getType();
            case MANGROVE_SWAMP        -> MUD.getType();
            case SWAMP                 -> CLAY.getType();
            case PLAINS,
                 MEADOW,
                 SUNFLOWER_PLAINS,
                 FLOWER_FOREST,
                 FOREST,
                 BIRCH_FOREST,
                 OLD_GROWTH_BIRCH_FOREST,
                 DARK_FOREST,
                 JUNGLE,
                 SPARSE_JUNGLE,
                 BAMBOO_JUNGLE,
                 SAVANNA,
                 SAVANNA_PLATEAU,
                 WINDSWEPT_SAVANNA,
                 CHERRY_GROVE          -> GRASS.getType();
            case STONY_PEAKS,
                 JAGGED_PEAKS,
                 FROZEN_PEAKS          -> STONE.getType();
            default                    -> DIRT.getType();
        };
    }
}