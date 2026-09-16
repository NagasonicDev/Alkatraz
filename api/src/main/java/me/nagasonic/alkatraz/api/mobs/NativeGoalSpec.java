package me.nagasonic.alkatraz.api.mobs;

import org.bukkit.entity.LivingEntity;

/**
 * Declarative description of a vanilla mob goal. A {@code NativeGoalSpec} is a
 * closed set of records; each version module registers a leaf-builder for it
 * into core's {@code AiSpecRegistry} that turns the spec into its own native
 * {@code net.minecraft.world.entity.ai.goal.Goal}. This keeps the goal inventory
 * reachable from the api without leaking NMS types.
 *
 * <p>Behaviour notes:
 * <ul>
 *   <li>{@code Float} — rises if the mob falls into water/lava.</li>
 *   <li>{@code MeleeAttack} — chase and attack the target while swinging.</li>
 *   <li>{@code WaterAvoidingRandomStroll} — wander, avoiding water.</li>
 *   <li>{@code LookAtPlayer} — turn the head toward nearby players.</li>
 *   <li>{@code RandomLookAround} — periodically look in a random direction.</li>
 *   <li>{@code HurtByTarget} — target any entity that hurts this mob.</li>
 *   <li>{@code NearestAttackableTarget} — target the nearest matching entity.</li>
 *   <li>{@code Panic} — flee from fire/damage at high speed.</li>
 *   <li>{@code AvoidEntity} — flee from a specific entity class.</li>
 * </ul>
 */
public sealed interface NativeGoalSpec
        permits NativeGoalSpec.Float,
        NativeGoalSpec.MeleeAttack,
        NativeGoalSpec.WaterAvoidingRandomStroll,
        NativeGoalSpec.LookAtPlayer,
        NativeGoalSpec.RandomLookAround,
        NativeGoalSpec.HurtByTarget,
        NativeGoalSpec.NearestAttackableTarget,
        NativeGoalSpec.Panic,
        NativeGoalSpec.AvoidEntity {

    /** Swims/paths up while in water. */
    record Float() implements NativeGoalSpec {}

    /** Chases and attacks the target; {@code pauseWhenMobIdle} enables the walk animation pause. */
    record MeleeAttack(double speed, boolean pauseWhenMobIdle) implements NativeGoalSpec {}

    /** Random wandering that avoids water. */
    record WaterAvoidingRandomStroll(double speed) implements NativeGoalSpec {}

    /** Turns the head toward nearby players. */
    record LookAtPlayer(float range) implements NativeGoalSpec {}

    /** Random head movement while idle. */
    record RandomLookAround() implements NativeGoalSpec {}

    /** Targets entities that damage this mob. */
    record HurtByTarget() implements NativeGoalSpec {}

    /** Targets the nearest entity of {@code targetClass}. */
    record NearestAttackableTarget(Class<? extends LivingEntity> targetClass, boolean mustSee) implements NativeGoalSpec {}

    /** Flees from fire/harmful damage at {@code speed}. */
    record Panic(double speed) implements NativeGoalSpec {}

    /** Flees from entities of {@code avoidClass} within {@code maxDist}. */
    record AvoidEntity(
            Class<? extends LivingEntity> avoidClass,
            float maxDist,
            double walkSpeed,
            double sprintSpeed
    ) implements NativeGoalSpec {}
}