package me.nagasonic.alkatraz.spells.implementation;

import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for the Inferno Pilgrimage spell.
 *
 * <p>Parsed from the spell's YAML by {@link InfernoPilgrimage} and consumed by both the
 * in-game logic and the unit tests. {@code pulseCount} is read from the yml's
 * {@code hit_count} key so the number {@code SpellBudgetTest} asserts is the number the
 * spell actually fires.
 */
public record InfernoPilgrimageConfig(
        int pulseCount,
        int pulseIntervalTicks,
        int travelTicks,
        double wallSpeed,
        int wallWidth,
        int wallHeight,
        double wallCenterHeight,
        double pulseReach,
        double pulseWidth,
        double pulseHeight,
        int igniteTicks,
        double knockback,
        double knockbackY,
        int escortTicks,
        double escortSpeed,
        int dissipateTicks) {

    public static InfernoPilgrimageConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new InfernoPilgrimageConfig(
                intOf(map.get("hit_count"), 3),
                intOf(map.get("pulse_interval_ticks"), 8),
                intOf(map.get("travel_ticks"), 24),
                doubleOf(map.get("wall_speed"), 0.75),
                intOf(map.get("wall_width"), 5),
                intOf(map.get("wall_height"), 3),
                doubleOf(map.get("wall_center_height"), 1.0),
                doubleOf(map.get("pulse_reach"), 8.0),
                doubleOf(map.get("pulse_width"), 3.5),
                doubleOf(map.get("pulse_height"), 2.0),
                intOf(map.get("ignite_ticks"), 40),
                doubleOf(map.get("knockback"), 0.4),
                doubleOf(map.get("knockback_y"), 0.3),
                intOf(map.get("escort_ticks"), 8),
                doubleOf(map.get("escort_speed"), 0.3),
                intOf(map.get("dissipate_ticks"), 10));
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    /** How far the wall front sits along the look direction after {@code tick} ticks. */
    public double frontAt(int tick) {
        return wallSpeed * tick;
    }

    /** Total distance the wall travels, in blocks. travelTicks 24 x wallSpeed 0.75 = 18.0 */
    public double travelBlocks() {
        return travelTicks * wallSpeed;
    }

    /** The tick index of the last pulse: pulseCount 3 x interval 8 - 8 = 16. */
    public int lastPulseTick() {
        return pulseCount > 0 ? (pulseCount - 1) * pulseIntervalTicks : -1;
    }

    /**
     * True when every declared pulse actually fires inside the wall's travel.
     * 16 &lt; 24, so the third pulse is reachable - a hit_count the wall cannot deliver
     * would make SpellBudgetTest assert a total no cast ever produces.
     */
    public boolean pulsesFitTravel() {
        return pulseCount > 0 && lastPulseTick() < travelTicks;
    }

    /** Half the visible wall width: wallWidth 5 -&gt; 2. */
    public int wallHalfWidth() {
        return Math.max(0, wallWidth / 2);
    }

    /** Worst-case discrete damage applications one cast makes to one target. */
    public int worstCaseHitsPerTarget() {
        return Math.max(1, pulseCount);
    }

    /** Along-axis window start that pulse {@code pulse} (0-based) sweeps. */
    public double pulseWindowFrom(int pulse) {
        return pulse <= 0 ? -pulseReach : frontAt((pulse - 1) * pulseIntervalTicks);
    }

    /** Along-axis window end that pulse {@code pulse} (0-based) sweeps. */
    public double pulseWindowTo(int pulse) {
        return frontAt(pulse * pulseIntervalTicks);
    }
}