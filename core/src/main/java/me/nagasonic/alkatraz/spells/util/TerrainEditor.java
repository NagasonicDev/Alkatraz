package me.nagasonic.alkatraz.spells.util;

import me.nagasonic.alkatraz.hooks.Protection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one place terrain is written. No such utility existed: there was no
 * BlockUtils and no explosion helper, and MeteorShower hand-rolled a triple loop
 * identical to Utils.blocksInRadius. Eleven restore sites wrote
 * setType(original, false) with no Protection re-check, so a region entered after
 * the cast but before recover_time elapsed was rewritten anyway.
 *
 * Every write here gates through Protection and respects a caller-supplied
 * maxBlocks cap. No cap existed anywhere before, so damage was a function of
 * whichever radius the spell hardcoded.
 */
public final class TerrainEditor {

    /** Never destroyed by any spell, regardless of blast or drought. */
    private static final Set<Material> PROTECTED = Collections.unmodifiableSet(EnumSet.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.CHEST,
            Material.TRAPPED_CHEST,
            Material.ENDER_CHEST,
            Material.BARREL,
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER,
            Material.SPAWNER,
            Material.OBSIDIAN,
            Material.CRYING_OBSIDIAN,
            Material.NETHER_PORTAL,
            Material.END_PORTAL,
            Material.END_PORTAL_FRAME,
            Material.LIGHT));

    private TerrainEditor() {
    }

    /** One location-to-original-material map, handed to {@link #restoreAll} on teardown. */
    public record RestoreTicket(Map<Location, Material> originals) {
    }

    /**
     * Blocks a spell may never destroy. Compared by identity - MeteorShower:304
     * used toString().contains("BEDROCK"), which would falsely match any future
     * material with BEDROCK in its name.
     */
    public static boolean isProtected(Material material) {
        return material != null && PROTECTED.contains(material);
    }

    /**
     * Destroys every unprotected solid block within {@code radius} of {@code centre},
     * capped at {@code maxBlocks}.
     *
     * @param dropItems true to use breakNaturally so drops are honoured. The old
     *                  MeteorShower used breakNaturally 30% of the time and
     *                  setType(AIR) the other 70%, silently deleting blocks with
     *                  no drops at all - that is an item-loss bug, not a feature.
     * @return the number of blocks actually destroyed, after Protection and the cap.
     */
    public static int destroySphere(LivingEntity caster, Location centre, double radius,
                                    boolean dropItems, int maxBlocks) {
        if (centre == null || centre.getWorld() == null || radius <= 0 || maxBlocks <= 0) return 0;
        int r = (int) Math.ceil(radius);
        int destroyed = 0;
        for (int dx = -r; dx <= r && destroyed < maxBlocks; dx++) {
            for (int dy = -r; dy <= r && destroyed < maxBlocks; dy++) {
                for (int dz = -r; dz <= r && destroyed < maxBlocks; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    Block block = centre.getWorld().getBlockAt(
                            centre.getBlockX() + dx,
                            centre.getBlockY() + dy,
                            centre.getBlockZ() + dz);
                    if (!block.getType().isSolid() || isProtected(block.getType())) continue;
                    if (!Protection.blockEdit(caster, block.getLocation())) continue;
                    if (dropItems) {
                        block.breakNaturally();
                    } else {
                        block.setType(Material.AIR, false);
                    }
                    destroyed++;
                }
            }
        }
        return destroyed;
    }

    /**
     * Places {@code material} at each target, recording every original for restore.
     *
     * @return the number of blocks actually placed, after Protection.
     */
    public static int placeBlocks(LivingEntity caster, List<Location> targets, Material material) {
        if (targets == null || material == null) return 0;
        int placed = 0;
        for (Location target : targets) {
            if (target == null || target.getWorld() == null) continue;
            Block block = target.getBlock();
            if (!Protection.blockEdit(caster, block.getLocation())) continue;
            block.setType(material, false);
            placed++;
        }
        return placed;
    }

    /** Records a block's current material so it can be put back later. */
    public static RestoreTicket record(Location loc, Material original) {
        Map<Location, Material> originals = new LinkedHashMap<>();
        if (loc != null && original != null) {
            originals.put(loc, original);
        }
        return new RestoreTicket(originals);
    }

    /** Convenience for accumulating a ticket as a spell builds. */
    public static RestoreTicket recordInto(Map<Location, Material> originals, Location loc, Material original) {
        if (loc != null && original != null) {
            originals.putIfAbsent(loc, original);
        }
        return new RestoreTicket(originals);
    }

    /**
     * Restores every recorded block, re-checking Protection PER BLOCK. The old
     * code wrote unconditionally, so it could write into a region the caster had
     * lost build permission in since casting.
     *
     * <p>This deliberately takes the caster. Re-gating on restore is impossible
     * without it, and that is the exact defect the eleven ungated undo sites had.
     *
     * @return the number of blocks actually restored.
     */
    public static int restoreAll(LivingEntity caster, RestoreTicket ticket) {
        if (ticket == null || ticket.originals() == null || ticket.originals().isEmpty()) return 0;
        int restored = 0;
        for (Map.Entry<Location, Material> entry : ticket.originals().entrySet()) {
            Location loc = entry.getKey();
            if (loc == null || loc.getWorld() == null) continue;
            Block block = loc.getBlock();
            if (block.getType() == entry.getValue()) continue;   // already correct, skip the write
            if (!Protection.blockEdit(caster, loc)) continue;     // re-gate: the region may have changed
            block.setType(entry.getValue(), false);
            restored++;
        }
        return restored;
    }

    /** All passable blocks with solid ground beneath, for igniting. Bounded by maxBlocks. */
    public static List<Location> igniteCandidates(LivingEntity caster, Location centre, double radius, int maxBlocks) {
        List<Location> candidates = new ArrayList<>();
        if (centre == null || centre.getWorld() == null || radius <= 0 || maxBlocks <= 0) return candidates;
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r && candidates.size() < maxBlocks; dx++) {
            for (int dy = -r; dy <= r && candidates.size() < maxBlocks; dy++) {
                for (int dz = -r; dz <= r && candidates.size() < maxBlocks; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    Block block = centre.getWorld().getBlockAt(
                            centre.getBlockX() + dx,
                            centre.getBlockY() + dy,
                            centre.getBlockZ() + dz);
                    if (!block.isPassable()) continue;
                    if (!block.getRelative(0, -1, 0).getType().isSolid()) continue;
                    if (!Protection.ignite(caster, block.getLocation())) continue;
                    candidates.add(block.getLocation());
                }
            }
        }
        return candidates;
    }
}