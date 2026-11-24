package me.nagasonic.alkatraz.spells.util;

import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.hooks.Protection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Shared landing checks for player-facing spell teleports. Locations are feet positions. */
public final class TeleportSafety {

    private static final double HALF_WIDTH = 0.29;
    private static final double[] FOOTPRINT = {-HALF_WIDTH, HALF_WIDTH};
    private static final double[] BODY_HEIGHTS = {0.05, 0.9, 1.7};
    /** Persisted on the player so consent survives relog and plugin reload. */
    private static final NamespacedKey TELEPORT_CONSENT = MagicKeys.alkatraz("teleport_consent");

    private TeleportSafety() {}

    /** True only when the player has explicitly opted in to being moved by other players' spells. */
    public static boolean hasTeleportConsent(Player player) {
        if (player == null) return false;
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (data == null) return false;
        Byte value = data.get(TELEPORT_CONSENT, PersistentDataType.BYTE);
        return value != null && value != 0;
    }

    /** Sets the opt-in and returns the new state. */
    public static boolean setTeleportConsent(Player player, boolean consent) {
        if (player == null) return false;
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (data == null) return false;
        if (consent) data.set(TELEPORT_CONSENT, PersistentDataType.BYTE, (byte) 1);
        else data.remove(TELEPORT_CONSENT);
        return consent;
    }

    /** Flips the opt-in and returns the new state. */
    public static boolean toggleTeleportConsent(Player player) {
        return setTeleportConsent(player, !hasTeleportConsent(player));
    }

    /**
     * Per-affected-player gate for moving somebody else. The caster never needs consent,
     * but every traveller still needs Protection approval at the destination.
     */
    public static boolean canTeleportAlly(Player caster, Player traveller, Location destination) {
        if (traveller == null || destination == null) return false;
        if (caster == null || (!caster.getUniqueId().equals(traveller.getUniqueId())
                && !hasTeleportConsent(traveller))) return false;
        return Protection.teleport(traveller, destination);
    }

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
     * Checks a player-sized movement step for a water pull. It permits water, but
     * stops before walls, hazards, void gaps, unloaded chunks, and the world border.
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
