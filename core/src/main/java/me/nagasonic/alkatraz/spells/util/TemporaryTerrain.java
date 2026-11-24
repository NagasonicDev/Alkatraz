package me.nagasonic.alkatraz.spells.util;

import me.nagasonic.alkatraz.util.Utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Place-then-restore wrapper around {@link TerrainEditor}, and the only owner of a
 * temporary structure's restore ticket. {@link #restore()} is idempotent, and the
 * block budget truncates placement rather than overrunning it.
 *
 * <p>Writes still gate through {@link TerrainEditor#placeBlocks} (Protection) and
 * restore through {@link TerrainEditor#restoreAll} (Protection re-checked per
 * block), so this class adds no new block-writing path.
 */
public final class TemporaryTerrain {

    private static final long OCCUPIED_RESTORE_GRACE_MS = 5_000L;
    private static final double PLAYER_HALF_WIDTH = 0.8;
    private static final double PLAYER_HEIGHT = 1.8;

    private final LivingEntity caster;
    private final int maxBlocks;
    private final Map<Location, Material> originals = new LinkedHashMap<>();
    private boolean restored;
    private long deferUntilMs = -1L;
    private boolean warnedOccupied;

    public TemporaryTerrain(LivingEntity caster, int maxBlocks) {
        this.caster = caster;
        this.maxBlocks = Math.max(0, maxBlocks);
    }

    /** Places {@code material} if the budget allows. Returns the number of blocks placed (0 or 1). */
    public int place(Location loc, Material material) {
        if (restored || loc == null || loc.getWorld() == null || material == null) return 0;
        Location key = blockKey(loc);
        if (originals.containsKey(key)) return 0;
        if (originals.size() >= maxBlocks) return 0;
        if (isPlayerOccupied(key)) return 0;
        Material original = key.getBlock().getType();
        int placed = TerrainEditor.placeBlocks(caster, List.of(key), material);
        if (placed > 0) originals.put(key, original);
        return placed;
    }

    /** Places {@code material} at each target in order until the budget runs out. */
    public int place(Iterable<Location> targets, Material material) {
        if (targets == null) return 0;
        int placed = 0;
        for (Location target : targets) {
            placed += place(target, material);
        }
        return placed;
    }

    /** Restores every recorded block. Calling it twice is a no-op; the second call returns 0. */
    public int restore() {
        if (restored) return 0;
        restored = true;
        deferUntilMs = -1L;
        return TerrainEditor.restoreAll(caster, new TerrainEditor.RestoreTicket(originals));
    }

    /**
     * Timer-restores only when no player is standing in a recorded block. Occupancy
     * delays the restore for five seconds; once that grace expires the warning is
     * shown and the restore proceeds. {@code warningMessage} must already be resolved
     * from the language file by the caller.
     */
    public int restoreWhenClear(long nowMs, Player owner, String warningMessage) {
        if (restored) return 0;
        if (!hasOccupiedBlock()) {
            deferUntilMs = -1L;
            return restore();
        }
        if (deferUntilMs < 0L) deferUntilMs = nowMs + OCCUPIED_RESTORE_GRACE_MS;
        if (nowMs < deferUntilMs) return 0;
        if (!warnedOccupied && owner != null && warningMessage != null) {
            Utils.sendActionBar(owner, warningMessage);
            warnedOccupied = true;
        }
        return restore();
    }

    /** True while a timed restore is waiting for a player to step out of the structure. */
    public boolean isDeferred() {
        return !restored && deferUntilMs >= 0L && hasOccupiedBlock();
    }

    /** True when the block's volume overlaps a living player's body. */
    public static boolean isPlayerOccupied(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        double centreX = loc.getBlockX() + 0.5;
        double centreZ = loc.getBlockZ() + 0.5;
        int blockY = loc.getBlockY();
        for (Player player : loc.getWorld().getPlayers()) {
            if (!player.isOnline() || player.isDead()) continue;
            Location feet = player.getLocation();
            if (Math.abs(feet.getX() - centreX) > PLAYER_HALF_WIDTH
                    || Math.abs(feet.getZ() - centreZ) > PLAYER_HALF_WIDTH) continue;
            if (feet.getY() <= blockY + PLAYER_HEIGHT && feet.getY() + PLAYER_HEIGHT > blockY) return true;
        }
        return false;
    }

    private boolean hasOccupiedBlock() {
        for (Location block : originals.keySet()) {
            if (isPlayerOccupied(block)) return true;
        }
        return false;
    }

    public int size() {
        return originals.size();
    }

    public boolean isRestored() {
        return restored;
    }

    public int remainingBudget() {
        return Math.max(0, maxBlocks - originals.size());
    }

    private static Location blockKey(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
