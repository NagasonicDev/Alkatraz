package me.nagasonic.alkatraz.api.mobs;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

/**
 * Version-agnostic view over the vanilla mob an api {@link Goal} runs on. The
 * api module never touches NMS; each version module implements this interface by
 * wrapping its own {@code net.minecraft.world.entity.Mob}.
 *
 * <p>Everything here is {@code org.bukkit}-typed so that core goals can be written
 * once and run against every supported Minecraft version.
 */
public interface MobBrainContext {

    /** The Bukkit mob this goal is attached to. */
    Mob self();

    /** The mob's current target, usually set by a target goal. */
    LivingEntity getTarget();

    /** Sets the mob's target directly. */
    void setTarget(LivingEntity target);

    /**
     * Tells the mob to path to {@code destination} at the given speed.
     *
     * @return {@code true} if a path to the destination was found.
     */
    boolean moveTo(Location destination, double speed);

    /**
     * Backs the mob away from {@code target}'s position at the given speed.
     * Uses the same retreat math as the original {@code KeepSpellRangeGoal}:
     * normalize the vector from target to self, aim for a point {@code keepDistance + 1}
     * blocks away, and nudge the mob's velocity when no path can be found.
     *
     * @param target       the position to retreat from
     * @param keepDistance the desired distance to keep from the target
     * @param speed        movement speed (blocks per tick)
     */
    void strafeAwayFrom(Location target, double keepDistance, double speed);

    /** The mob's current motion vector. */
    Vector getVelocity();

    /** Overrides the mob's motion vector. */
    void setVelocity(Vector velocity);

    /** Whether the mob can see {@code entity} (line of sight). */
    boolean hasLineOfSight(Entity entity);

    /**
     * Makes the mob look at the given eye position using the vanilla look control
     * defaults (30&deg; yaw speed, max head pitch).
     */
    void lookAt(Location eyeLocation);

    /** Whether the mob is currently computing/traversing a path. */
    boolean isNavigating();

    /** Squared distance from the mob to {@code entity}. */
    double distanceSq(Entity entity);

    /** The item in the mob's main hand, or {@code AIR} if empty. */
    ItemStack getMainHandItem();

    /** Stops the mob's current pathfinding. */
    void stopNavigating();
}