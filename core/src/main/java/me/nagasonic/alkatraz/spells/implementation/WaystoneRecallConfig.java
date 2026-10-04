package me.nagasonic.alkatraz.spells.implementation;

import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for Waystone Recall.
 *
 * <p>Parsed from the spell's YAML by {@link WaystoneRecall} and consumed by both the in-game logic
 * and the unit tests. Every field is a primitive, so the record is constructible in a unit test
 * with no server - which is the whole reason it exists rather than four fields on the spell.
 *
 * <p>The record carries <b>no</b> warp formula. The distance and slot rules belong to
 * {@link WarpConfig}, reached through {@link Warp}'s public accessors, so this spell cannot drift
 * away from the budget its own profile has earned. What lives here is only the two multipliers
 * recall layers on top of that budget, and the aim cone that picks a destination.
 */
public record WaystoneRecallConfig(
        double distanceMultiplier,
        double aimConeDegrees,
        int minRecallSlots,
        int maxRecallSlots,
        int arrivalExpandTicks) {

    public static WaystoneRecallConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new WaystoneRecallConfig(
                doubleOf(map.get("distance_multiplier"), 2.0),
                doubleOf(map.get("aim_cone_degrees"), 6.0),
                intOf(map.get("min_recall_slots"), 1),
                intOf(map.get("max_recall_slots"), 5),
                Math.max(1, intOf(map.get("arrival_expand_ticks"), 10)));
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    /**
     * The number of warp-point slots recall may consider.
     *
     * <p>Always {@code min(warp's own mastery-gated budget, max_recall_slots)}, so recall can
     * only ever narrow what the profile was already allowed to hold - never widen it. A caster
     * with no warp mastery gets {@code 0}, and {@link WaystoneRecall} self-cancels.
     */
    public int effectiveSlotBudget(int warpSlotBudget) {
        return Math.max(0, Math.min(Math.max(0, warpSlotBudget), maxRecallSlots));
    }

    /**
     * The recall reach, from warp's own budgeted distance multiplied by this spell's one knob.
     *
     * <p>The circle ramp and the mastery ramp are inherited unchanged; only the multiplier is new.
     */
    public double recallDistance(double warpDistance) {
        return Math.max(0.0, warpDistance) * Math.max(0.0, distanceMultiplier);
    }
}