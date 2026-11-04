package me.nagasonic.alkatraz.spells.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Shared landing checks for player-facing spell teleports. Locations are feet positions. */
public final class TeleportSafety {

    private static final double HALF_WIDTH = 0.29;
    private static final double[] FOOTPRINT = {-HALF_WIDTH, HALF_WIDTH};
    private static final double[] BODY_HEIGHTS = {0.05, 0.9, 1.7};

    private TeleportSafety() {}

    /**
     * Checks that a player-sized body can stand at the destination without entering a
     * solid/liquid block, leaving the world border, landing in an unloaded chunk, or
     * standing on a common damage block.
     */
    public static boolean isSafePlayerDestination(Location location) {
        return isSafePlayerDestination(location, true);
    }

    /** Same collision and hazard checks as a grounded landing, but allows a clear midair stop. */
    public static boolean isSafePlayerDestinationInAir(Location location) {
        return isSafePlayerDestination(location, false);
    }

    private static boolean isSafePlayerDestination(Location location, boolean requireFloor) {
        if (location == null) return false;
        World world = location.getWorld();
        if (world == null) return false;

        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();
        if (blockY <= world.getMinHeight() || blockY + 2 >= world.getMaxHeight()) return false;
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) return false;

        if (!world.getWorldBorder().isInside(location)) return false;

        for (double x : FOOTPRINT) {
            for (double z : FOOTPRINT) {
                Location corner = location.clone().add(x, 0, z);
                if (!world.getWorldBorder().isInside(corner)) return false;
                for (double height : BODY_HEIGHTS) {
                    Block body = world.getBlockAt(
                            floor(corner.getX()),
                            floor(location.getY() + height),
                            floor(corner.getZ()));
                    if (!body.isPassable() || body.isLiquid() || isHazard(body.getType())) return false;
                }
            }
        }

        if (requireFloor) {
            for (double x : FOOTPRINT) {
                for (double z : FOOTPRINT) {
                    Block cornerFloor = world.getBlockAt(floor(location.getX() + x), blockY - 1, floor(location.getZ() + z));
                    if (!cornerFloor.getType().isSolid() || cornerFloor.isLiquid() || isHazard(cornerFloor.getType())) return false;
                }
            }
        }
        return !isHazard(world.getBlockAt(blockX, blockY, blockZ).getType());
    }

    /**
     * Checks a player-sized movement step for Undertow. It permits water, but stops
     * before walls, hazards, void gaps, unloaded chunks, and the world border.
     */
    public static boolean isSafePullPosition(Location location) {
        if (location == null) return false;
        World world = location.getWorld();
        if (world == null) return false;
        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();
        if (blockY <= world.getMinHeight() || blockY + 2 >= world.getMaxHeight()) return false;
        if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4) || !world.getWorldBorder().isInside(location)) return false;

        for (double x : FOOTPRINT) {
            for (double z : FOOTPRINT) {
                Location corner = location.clone().add(x, 0, z);
                if (!world.getWorldBorder().isInside(corner)) return false;
                for (double height : BODY_HEIGHTS) {
                    Block body = world.getBlockAt(floor(corner.getX()), floor(location.getY() + height),
                            floor(corner.getZ()));
                    if ((!body.isPassable() && !body.isLiquid()) || isHazard(body.getType())) return false;
                }
            }
        }

        Block support = world.getBlockAt(blockX, blockY - 1, blockZ);
        return (support.getType().isSolid() || support.isLiquid()) && !isHazard(support.getType());
    }

    private static int floor(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private static boolean isHazard(Material material) {
        return material == Material.LAVA
                || material == Material.FIRE
                || material == Material.SOUL_FIRE
                || material == Material.CACTUS
                || material == Material.MAGMA_BLOCK
                || material == Material.CAMPFIRE
                || material == Material.SOUL_CAMPFIRE
                || material == Material.POWDER_SNOW
                || material == Material.SWEET_BERRY_BUSH;
    }
}
