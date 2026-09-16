package me.nagasonic.alkatraz.api.mobs;

import java.util.EnumSet;

/**
 * Version-agnostic behaviour unit for a magic mob. Implementations live in the
 * core module (or a plugin) and are wired into a specific Minecraft version by
 * the version-specific {@code GoalBridge}; the vanilla goal lifecycle
 * (canUse/canContinueToUse/start/stop/tick) maps onto
 * {@link #canStart(MobBrainContext)},
 * {@link #shouldContinue(MobBrainContext)}, {@link #start(MobBrainContext)},
 * {@link #stop(MobBrainContext)} and {@link #tick(MobBrainContext)}.
 */
public interface Goal {

    /** Equivalent of vanilla {@code canUse()}. */
    boolean canStart(MobBrainContext context);

    /** Equivalent of vanilla {@code canContinueToUse()}. Defaults to {@link #canStart}. */
    default boolean shouldContinue(MobBrainContext context) {
        return canStart(context);
    }

    /** Equivalent of vanilla {@code start()}. */
    default void start(MobBrainContext context) {}

    /** Equivalent of vanilla {@code stop()}. */
    default void stop(MobBrainContext context) {}

    /** Equivalent of vanilla {@code tick()}. */
    void tick(MobBrainContext context);

    /**
     * Flags this goal sets on the mob while running. Mapped onto the vanilla
     * {@code Goal$Flag} set by the version-specific {@code GoalBridge}.
     */
    default EnumSet<GoalFlag> flags() {
        return EnumSet.noneOf(GoalFlag.class);
    }
}