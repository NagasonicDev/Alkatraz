package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.Alkatraz;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Client-only water blocks used to give water spells a visible shape without particles. */
public final class FakeWaterVfx {
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final Map<Location, Long> LAST = new ConcurrentHashMap<>();

    private FakeWaterVfx() {}

    /** Briefly shows water at an otherwise empty block to players who can see the location. */
    public static void show(Location location, long durationTicks) {
        if (location == null || location.getWorld() == null || durationTicks < 1) return;
        World world = location.getWorld();
        Block block = world.getBlockAt(location);
        if (!block.isEmpty()) return;
        Location blockLocation = block.getLocation();
        long generation = GENERATION.incrementAndGet();
        LAST.put(blockLocation, generation);
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(blockLocation) <= 64 * 64) {
                player.sendBlockChange(blockLocation, Material.WATER.createBlockData());
            }
        }
        Bukkit.getScheduler().runTaskLater(Alkatraz.getInstance(), () -> {
            Long current = LAST.get(blockLocation);
            if (current == null || current.longValue() != generation) return;
            LAST.remove(blockLocation);
            BlockData data = world.getBlockAt(blockLocation).getBlockData();
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(blockLocation) <= 64 * 64) {
                    player.sendBlockChange(blockLocation, data);
                }
            }
        }, durationTicks);
    }

    /** Revert any in-flight client-side water blocks, e.g. on plugin disable. */
    public static void shutdown() {
        for (Location blockLocation : new ArrayList<>(LAST.keySet())) {
            World world = blockLocation.getWorld();
            if (world == null) {
                LAST.remove(blockLocation);
                continue;
            }
            BlockData data = world.getBlockAt(blockLocation).getBlockData();
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(blockLocation) <= 64 * 64) {
                    player.sendBlockChange(blockLocation, data);
                }
            }
            LAST.remove(blockLocation);
        }
    }
}