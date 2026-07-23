package me.nagasonic.alkatraz.api.dom;

import org.bukkit.Material;
import org.bukkit.block.Biome;

/**
 * Enum representing natural ground block types used by the spell system.
 * Each constant maps to a Bukkit {@link Material} and the enum provides
 * utility methods for classifying ground materials (loose, rock, ore)
 * and for resolving a representative ground material from a {@link Biome}.
 */
public enum Ground {

    // --- Soil & Dirt ---------------------------------------------------------
    /** Dirt block. */
    DIRT(Material.DIRT),
    /** Grass block. */
    GRASS(Material.GRASS_BLOCK),
    /** Coarse dirt block. */
    COARSE_DIRT(Material.COARSE_DIRT),
    /** Rooted dirt block. */
    ROOTED_DIRT(Material.ROOTED_DIRT),
    /** Podzol block. */
    PODZOL(Material.PODZOL),
    /** Mud block. */
    MUD(Material.MUD),
    /** Muddy mangrove roots block. */
    MUDDY_MANGROVE_ROOTS(Material.MUDDY_MANGROVE_ROOTS),
    /** Mycelium block. */
    MYCELIUM(Material.MYCELIUM),

    // --- Sand & Gravel -------------------------------------------------------
    /** Sand block. */
    SAND(Material.SAND),
    /** Red sand block. */
    RED_SAND(Material.RED_SAND),
    /** Gravel block. */
    GRAVEL(Material.GRAVEL),


    // --- Clay & Terracotta ---------------------------------------------------
    /** Clay block. */
    CLAY(Material.CLAY),
    /** Terracotta block. */
    TERRACOTTA(Material.TERRACOTTA),
    /** White terracotta block. */
    WHITE_TERRACOTTA(Material.WHITE_TERRACOTTA),
    /** Orange terracotta block. */
    ORANGE_TERRACOTTA(Material.ORANGE_TERRACOTTA),
    /** Magenta terracotta block. */
    MAGENTA_TERRACOTTA(Material.MAGENTA_TERRACOTTA),
    /** Light blue terracotta block. */
    LIGHT_BLUE_TERRACOTTA(Material.LIGHT_BLUE_TERRACOTTA),
    /** Yellow terracotta block. */
    YELLOW_TERRACOTTA(Material.YELLOW_TERRACOTTA),
    /** Lime terracotta block. */
    LIME_TERRACOTTA(Material.LIME_TERRACOTTA),
    /** Pink terracotta block. */
    PINK_TERRACOTTA(Material.PINK_TERRACOTTA),
    /** Gray terracotta block. */
    GRAY_TERRACOTTA(Material.GRAY_TERRACOTTA),
    /** Light gray terracotta block. */
    LIGHT_GRAY_TERRACOTTA(Material.LIGHT_GRAY_TERRACOTTA),
    /** Cyan terracotta block. */
    CYAN_TERRACOTTA(Material.CYAN_TERRACOTTA),
    /** Purple terracotta block. */
    PURPLE_TERRACOTTA(Material.PURPLE_TERRACOTTA),
    /** Blue terracotta block. */
    BLUE_TERRACOTTA(Material.BLUE_TERRACOTTA),
    /** Brown terracotta block. */
    BROWN_TERRACOTTA(Material.BROWN_TERRACOTTA),
    /** Green terracotta block. */
    GREEN_TERRACOTTA(Material.GREEN_TERRACOTTA),
    /** Red terracotta block. */
    RED_TERRACOTTA(Material.RED_TERRACOTTA),
    /** Black terracotta block. */
    BLACK_TERRACOTTA(Material.BLACK_TERRACOTTA),

    // --- Stone & Variants ----------------------------------------------------
    /** Stone block. */
    STONE(Material.STONE),
    /** Cobblestone block. */
    COBBLESTONE(Material.COBBLESTONE),
    /** Mossy cobblestone block. */
    MOSSY_COBBLESTONE(Material.MOSSY_COBBLESTONE),
    /** Stone bricks block. */
    STONE_BRICKS(Material.STONE_BRICKS),
    /** Mossy stone bricks block. */
    MOSSY_STONE_BRICKS(Material.MOSSY_STONE_BRICKS),
    /** Cracked stone bricks block. */
    CRACKED_STONE_BRICKS(Material.CRACKED_STONE_BRICKS),
    /** Chiseled stone bricks block. */
    CHISELED_STONE_BRICKS(Material.CHISELED_STONE_BRICKS),
    /** Smooth stone block. */
    SMOOTH_STONE(Material.SMOOTH_STONE),
    /** Infested stone block. */
    INFESTED_STONE(Material.INFESTED_STONE),
    /** Infested cobblestone block. */
    INFESTED_COBBLESTONE(Material.INFESTED_COBBLESTONE),
    /** Infested stone bricks block. */
    INFESTED_STONE_BRICKS(Material.INFESTED_STONE_BRICKS),

    // --- Deepslate & Variants ------------------------------------------------
    /** Deepslate block. */
    DEEPSLATE(Material.DEEPSLATE),
    /** Cobbled deepslate block. */
    COBBLED_DEEPSLATE(Material.COBBLED_DEEPSLATE),
    /** Polished deepslate block. */
    POLISHED_DEEPSLATE(Material.POLISHED_DEEPSLATE),
    /** Deepslate bricks block. */
    DEEPSLATE_BRICKS(Material.DEEPSLATE_BRICKS),
    /** Cracked deepslate bricks block. */
    CRACKED_DEEPSLATE_BRICKS(Material.CRACKED_DEEPSLATE_BRICKS),
    /** Deepslate tiles block. */
    DEEPSLATE_TILES(Material.DEEPSLATE_TILES),
    /** Cracked deepslate tiles block. */
    CRACKED_DEEPSLATE_TILES(Material.CRACKED_DEEPSLATE_TILES),
    /** Chiseled deepslate block. */
    CHISELED_DEEPSLATE(Material.CHISELED_DEEPSLATE),
    /** Infested deepslate block. */
    INFESTED_DEEPSLATE(Material.INFESTED_DEEPSLATE),

    // --- Granite, Diorite & Andesite -----------------------------------------
    /** Granite block. */
    GRANITE(Material.GRANITE),
    /** Polished granite block. */
    POLISHED_GRANITE(Material.POLISHED_GRANITE),
    /** Diorite block. */
    DIORITE(Material.DIORITE),
    /** Polished diorite block. */
    POLISHED_DIORITE(Material.POLISHED_DIORITE),
    /** Andesite block. */
    ANDESITE(Material.ANDESITE),
    /** Polished andesite block. */
    POLISHED_ANDESITE(Material.POLISHED_ANDESITE),

    // --- Ts is so tuff ----------------------------------------------------------------
    /** Tuff block. */
    TUFF(Material.TUFF),

    // --- Calcite & Dripstone -------------------------------------------------
    /** Calcite block. */
    CALCITE(Material.CALCITE),
    /** Dripstone block. */
    DRIPSTONE_BLOCK(Material.DRIPSTONE_BLOCK),
    /** Pointed dripstone block. */
    POINTED_DRIPSTONE(Material.POINTED_DRIPSTONE),

    // --- Sandstone -----------------------------------------------------------
    /** Sandstone block. */
    SANDSTONE(Material.SANDSTONE),
    /** Chiseled sandstone block. */
    CHISELED_SANDSTONE(Material.CHISELED_SANDSTONE),
    /** Cut sandstone block. */
    CUT_SANDSTONE(Material.CUT_SANDSTONE),
    /** Smooth sandstone block. */
    SMOOTH_SANDSTONE(Material.SMOOTH_SANDSTONE),
    /** Red sandstone block. */
    RED_SANDSTONE(Material.RED_SANDSTONE),
    /** Chiseled red sandstone block. */
    CHISELED_RED_SANDSTONE(Material.CHISELED_RED_SANDSTONE),
    /** Cut red sandstone block. */
    CUT_RED_SANDSTONE(Material.CUT_RED_SANDSTONE),
    /** Smooth red sandstone block. */
    SMOOTH_RED_SANDSTONE(Material.SMOOTH_RED_SANDSTONE),

    // --- Ores ----------------------------------------------------------------
    /** Coal ore block. */
    COAL_ORE(Material.COAL_ORE),
    /** Deepslate coal ore block. */
    DEEPSLATE_COAL_ORE(Material.DEEPSLATE_COAL_ORE),
    /** Iron ore block. */
    IRON_ORE(Material.IRON_ORE),
    /** Deepslate iron ore block. */
    DEEPSLATE_IRON_ORE(Material.DEEPSLATE_IRON_ORE),
    /** Copper ore block. */
    COPPER_ORE(Material.COPPER_ORE),
    /** Deepslate copper ore block. */
    DEEPSLATE_COPPER_ORE(Material.DEEPSLATE_COPPER_ORE),
    /** Gold ore block. */
    GOLD_ORE(Material.GOLD_ORE),
    /** Deepslate gold ore block. */
    DEEPSLATE_GOLD_ORE(Material.DEEPSLATE_GOLD_ORE),
    /** Nether gold ore block. */
    NETHER_GOLD_ORE(Material.NETHER_GOLD_ORE),
    /** Redstone ore block. */
    REDSTONE_ORE(Material.REDSTONE_ORE),
    /** Deepslate redstone ore block. */
    DEEPSLATE_REDSTONE_ORE(Material.DEEPSLATE_REDSTONE_ORE),
    /** Lapis ore block. */
    LAPIS_ORE(Material.LAPIS_ORE),
    /** Deepslate lapis ore block. */
    DEEPSLATE_LAPIS_ORE(Material.DEEPSLATE_LAPIS_ORE),
    /** Diamond ore block. */
    DIAMOND_ORE(Material.DIAMOND_ORE),
    /** Deepslate diamond ore block. */
    DEEPSLATE_DIAMOND_ORE(Material.DEEPSLATE_DIAMOND_ORE),
    /** Emerald ore block. */
    EMERALD_ORE(Material.EMERALD_ORE),
    /** Deepslate emerald ore block. */
    DEEPSLATE_EMERALD_ORE(Material.DEEPSLATE_EMERALD_ORE),
    /** Nether quartz ore block. */
    NETHER_QUARTZ_ORE(Material.NETHER_QUARTZ_ORE),
    /** Ancient debris block. */
    ANCIENT_DEBRIS(Material.ANCIENT_DEBRIS),

    // --- Nether Ground -------------------------------------------------------
    /** Netherrack block. */
    NETHERRACK(Material.NETHERRACK),
    /** Soul sand block. */
    SOUL_SAND(Material.SOUL_SAND),
    /** Soul soil block. */
    SOUL_SOIL(Material.SOUL_SOIL),
    /** Basalt block. */
    BASALT(Material.BASALT),
    /** Smooth basalt block. */
    SMOOTH_BASALT(Material.SMOOTH_BASALT),
    /** Polished basalt block. */
    POLISHED_BASALT(Material.POLISHED_BASALT),
    /** Blackstone block. */
    BLACKSTONE(Material.BLACKSTONE),
    /** Gilded blackstone block. */
    GILDED_BLACKSTONE(Material.GILDED_BLACKSTONE),
    /** Polished blackstone block. */
    POLISHED_BLACKSTONE(Material.POLISHED_BLACKSTONE),
    /** Chiseled polished blackstone block. */
    CHISELED_POLISHED_BLACKSTONE(Material.CHISELED_POLISHED_BLACKSTONE),
    /** Polished blackstone bricks block. */
    POLISHED_BLACKSTONE_BRICKS(Material.POLISHED_BLACKSTONE_BRICKS),
    /** Cracked polished blackstone bricks block. */
    CRACKED_POLISHED_BLACKSTONE_BRICKS(Material.CRACKED_POLISHED_BLACKSTONE_BRICKS),
    /** Magma block. */
    MAGMA_BLOCK(Material.MAGMA_BLOCK),

    // --- End Ground ----------------------------------------------------------
    /** End stone block. */
    END_STONE(Material.END_STONE),
    /** End stone bricks block. */
    END_STONE_BRICKS(Material.END_STONE_BRICKS);

    // -------------------------------------------------------------------------

    private final Material type;

    Ground(Material type) {
        this.type = type;
    }

    /**
     * Returns the Bukkit {@link Material} associated with this ground type.
     *
     * @return the material type
     */
    public Material getType() {
        return this.type;
    }

    /**
     * Checks whether the given material is a recognized ground material.
     *
     * @param material the material to check
     * @return {@code true} if the material is a known ground type, {@code false} otherwise
     */
    public static boolean isGround(Material material) {
        for (Ground ground : Ground.values()) {
            if (ground.type.equals(material)) return true;
        }
        return false;
    }

    /**
     * Returns true only for loose, unstable ground that would realistically
     * shift or crumble — useful for spells that scatter or sink terrain.
     *
     * @param material the material to check
     * @return {@code true} if the material is a loose ground type
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
     *
     * @param material the material to check
     * @return {@code true} if the material is a rock-type ground block
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
     *
     * @param material the material to check
     * @return {@code true} if the material is an ore-type ground block
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

    /**
     * Returns the representative ground {@link Material} for the given biome.
     * Maps each biome to its most characteristic natural ground block.
     *
     * @param biome the biome to resolve
     * @return the default ground material for that biome, or {@link Material#DIRT} if no specific mapping exists
     */
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
                  WINDSWEPT_SAVANNA       -> GRASS.getType();
            case STONY_PEAKS,
                 JAGGED_PEAKS,
                 FROZEN_PEAKS          -> STONE.getType();
            default                    -> DIRT.getType();
        };
    }
}
