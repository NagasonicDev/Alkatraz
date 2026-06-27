package me.nagasonic.alkatraz.mobs;

/**
 * Configuration for a magic mob's spell-casting AI. Consumed by the
 * version-specific {@code GoalBuilder} to wire up {@code KeepSpellRangeGoal}
 * and {@code CastSpellGoal}.
 *
 * <p>Set {@code minCastDist} and {@code maxCastDist} to {@code 0} to skip the
 * range-keeping goal entirely (e.g. for a ZombieFighter that casts only
 * opportunistically and relies on its melee goal for positioning).
 *
 * @param minCastDist   Back away from the target if closer than this (blocks).
 *                      {@code 0} disables range-keeping.
 * @param maxCastDist   Close in on the target if farther than this (blocks).
 *                      {@code 0} disables range-keeping.
 * @param castRange     Maximum distance at which a spell can be cast (blocks).
 * @param cooldownTicks Ticks to wait between cast attempts.
 */
public record SpellCastConfig(
        double minCastDist,
        double maxCastDist,
        double castRange,
        int    cooldownTicks
) {
    /**
     * Returns {@code true} when the mob should actively reposition to stay
     * within the preferred casting band.
     */
    public boolean hasRangeKeeping() {
        return minCastDist > 0 && maxCastDist > 0;
    }
}
