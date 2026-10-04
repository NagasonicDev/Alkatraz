package me.nagasonic.alkatraz.spells.implementation;

import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for the Sunfire Barrage spell.
 *
 * <p>Parsed from the spell's YAML by {@link SunfireBarrage} and consumed by both the
 * in-game logic and the unit tests. {@code maxStrikesPerTarget} is read from the yml's
 * {@code hit_count} key, so the number {@code SpellBudgetTest} multiplies by
 * {@code power} is the same number the spell enforces as its per-target cap.
 */
public record SunfireBarrageConfig(
        int strikeCount,
        int maxMarks,
        double markRadius,
        int markIntervalTicks,
        int windUpSeconds,
        int strikeDelayTicks,
        int strikeIntervalTicks,
        int maxStrikesPerTarget,
        double moveCancelThreshold,
        double knockUp) {

    public static SunfireBarrageConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new SunfireBarrageConfig(
                intOf(map.get("strike_count"), 5),
                intOf(map.get("max_marks"), 5),
                doubleOf(map.get("mark_radius"), 18.0),
                intOf(map.get("mark_interval_ticks"), 4),
                intOf(map.get("wind_up_duration"), 2),
                intOf(map.get("strike_delay_ticks"), 6),
                intOf(map.get("strike_interval_ticks"), 2),
                intOf(map.get("hit_count"), 2),
                doubleOf(map.get("move_cancel_threshold"), 0.25),
                doubleOf(map.get("knock_up"), 0.9));
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    /** Mastery shortens the channel to 75% at maximum mastery, exactly as WarpConfig does. */
    public int windUpTicks(int mastery, int maxMastery) {
        double multiplier = (maxMastery > 0 && mastery >= maxMastery) ? 0.75 : 1.0;
        return Math.max(1, (int) Math.round(windUpSeconds * 20.0 * multiplier));
    }

    /**
     * Ticks from the start of the barrage to the last strike: 6 + 5 x 2 = 16 ticks = 0.8s.
     * Spec section 5.1 requires every fire damage event to land within a second.
     */
    public int strikeSpanTicks() {
        return strikeDelayTicks + strikeCount * strikeIntervalTicks;
    }

    public double strikeSpanSeconds() {
        return strikeSpanTicks() / 20.0;
    }

    /** The per-target cap the yml declares as hit_count, floored at 1. */
    public int worstCaseHitsPerTarget() {
        return Math.max(1, maxStrikesPerTarget);
    }

    /** The most strikes one cast can actually deliver once the cap is applied. */
    public int strikesDeliveredPerTarget() {
        return Math.min(maxStrikesPerTarget, strikeCount);
    }
}