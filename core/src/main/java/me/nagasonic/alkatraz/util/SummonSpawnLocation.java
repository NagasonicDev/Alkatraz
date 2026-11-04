package me.nagasonic.alkatraz.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Resolves an aimed or forward summon point to the nearest clear surface position. */
public final class SummonSpawnLocation {
    private static final double TARGET_RANGE = 20.0;
    private static final double FORWARD_DISTANCE = 5.0;
    private static final int SEARCH_RADIUS = 5;

    private SummonSpawnLocation() {}

    public static Location resolve(Player player) {
        Location eye = player.getEyeLocation();
        Block target = player.getTargetBlockExact((int) TARGET_RANGE);
        if (target != null) {
            Location targetPoint = target.getLocation().add(0.5, 0, 0.5);
            if (eye.distanceSquared(targetPoint) < TARGET_RANGE * TARGET_RANGE) {
                Location safe = closestSurface(targetPoint);
                if (safe != null) return safe;
            }
        }

        Vector direction = player.getLocation().getDirection().setY(0);
        if (direction.lengthSquared() < 1.0E-8) direction = new Vector(0, 0, 1);
        Location forward = player.getLocation().add(direction.normalize().multiply(FORWARD_DISTANCE));
        return closestSurface(forward);
    }

    public static Location resolve(LivingEntity entity) {
        if (entity instanceof Player player) {
            return resolve(player);
        }
        Location loc = entity.getLocation();
        Vector direction = loc.getDirection().setY(0);
        if (direction.lengthSquared() < 1.0E-8) direction = new Vector(0, 0, 1);
        Location forward = loc.add(direction.normalize().multiply(FORWARD_DISTANCE));
        return closestSurface(forward);
    }

    private static Location closestSurface(Location center) {
        World world = center.getWorld();
        if (world == null) return null;
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        Location best = null;
        double bestDistance = Double.POSITIVE_INFINITY;

        for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    int y = world.getHighestBlockYAt(x, z);
                    Block floor = world.getBlockAt(x, y, z);
                    Block feet = world.getBlockAt(x, y + 1, z);
                    Block head = world.getBlockAt(x, y + 2, z);
                    if (!isSafeFloor(floor.getType()) || !passable(feet) || !passable(head)) continue;

                    double px = x + 0.5;
                    double pz = z + 0.5;
                    double distance = center.distanceSquared(new Location(world, px, y + 1, pz));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new Location(world, px, y + 1, pz, center.getYaw(), 0);
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static boolean passable(Block block) {
        Material type = block.getType();
        return block.isPassable() && type != Material.LAVA && type != Material.FIRE && type != Material.SOUL_FIRE;
    }

    private static boolean isSafeFloor(Material type) {
        return type.isSolid()
                && type != Material.LAVA
                && type != Material.FIRE
                && type != Material.SOUL_FIRE
                && type != Material.MAGMA_BLOCK
                && type != Material.CACTUS
                && type != Material.POWDER_SNOW
                && type != Material.SWEET_BERRY_BUSH
                && type != Material.CAMPFIRE
                && type != Material.SOUL_CAMPFIRE;
    }
}
