package me.nagasonic.alkatraz.mobs.ai.task;

import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.ai.task.Sensor;
import me.nagasonic.alkatraz.api.ai.task.TaskBrainContext;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs sensors against a {@link TaskBrainContext}, writing observations into the
 * blackboard. {@code HurtBy} is a marker sensor: the damage it records is
 * written by {@link TaskBrainTicker}'s damage listener (it cannot be polled).
 */
public final class SensorRunner {

    /** How long {@code IS_HURT} stays set after damage. */
    public static final long HURT_EXPIRE_TICKS = 200;

    private SensorRunner() {}

    public static void run(List<Sensor> sensors, TaskBrainContext ctx) {
        for (Sensor sensor : sensors) {
            if (sensor instanceof Sensor.NearestLivingEntities nearest) {
                writeNearest(ctx, nearest.radius(), false);
            } else if (sensor instanceof Sensor.NearestPlayers nearest) {
                writeNearest(ctx, nearest.radius(), true);
            }
            // Sensor.HurtBy and CustomSensor have no polling behaviour in the core engine.
        }
    }

    private static void writeNearest(TaskBrainContext ctx, int radius, boolean playersOnly) {
        LivingEntity entity = ctx.getEntity();
        List<LivingEntity> nearby;
        List<UUID> nearbyIds;
        if (ctx instanceof TaskBrainEngine engine) {
            nearby = engine.nearbyScratch();
            nearbyIds = engine.nearbyScratchIds();
        } else {
            nearby = new ArrayList<>(16);
            nearbyIds = new ArrayList<>(16);
        }
        nearby.clear();
        nearbyIds.clear();
        for (Entity candidate : entity.getNearbyEntities(radius, radius, radius)) {
            if (candidate.equals(entity)) continue;
            if (!(candidate instanceof LivingEntity living)) continue;
            if (playersOnly && !(living instanceof Player)) continue;
            nearby.add(living);
            nearbyIds.add(living.getUniqueId());
        }
        if (nearby.isEmpty()) {
            ctx.eraseMemory(MemoryKey.NEAREST_HOSTILES);
            return;
        }
        if (unchanged(ctx, nearby, nearbyIds)) return;
        ctx.setMemory(MemoryKey.NEAREST_HOSTILES, List.copyOf(nearby));
    }

    /**
     * Whether the freshly scanned neighbourhood is the same one already on the
     * blackboard. Compared by id, not by identity: CraftBukkit hands out a new
     * wrapper per entity per tick, so reference equality would never match. The
     * stored list is refreshed every heartbeat anyway, so skipping a redundant
     * write only saves the copy and the blackboard churn.
     */
    private static boolean unchanged(TaskBrainContext ctx, List<LivingEntity> scanned, List<UUID> scannedIds) {
        List<LivingEntity> stored = ctx.getMemory(MemoryKey.NEAREST_HOSTILES).orElse(null);
        if (stored == null || stored.size() != scanned.size()) return false;
        for (int i = 0; i < scanned.size(); i++) {
            if (!stored.get(i).getUniqueId().equals(scannedIds.get(i))) return false;
        }
        return true;
    }
}