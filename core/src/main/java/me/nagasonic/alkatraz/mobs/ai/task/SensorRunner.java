package me.nagasonic.alkatraz.mobs.ai.task;

import me.nagasonic.alkatraz.api.ai.task.MemoryKey;
import me.nagasonic.alkatraz.api.ai.task.Sensor;
import me.nagasonic.alkatraz.api.ai.task.TaskBrainContext;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

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
        List<LivingEntity> nearby = new ArrayList<>();
        for (Entity candidate : entity.getNearbyEntities(radius, radius, radius)) {
            if (candidate.equals(entity)) continue;
            if (!(candidate instanceof LivingEntity living)) continue;
            if (playersOnly && !(living instanceof Player)) continue;
            nearby.add(living);
        }
        if (nearby.isEmpty()) {
            ctx.eraseMemory(MemoryKey.NEAREST_HOSTILES);
        } else {
            ctx.setMemory(MemoryKey.NEAREST_HOSTILES, nearby);
        }
    }
}