package me.nagasonic.alkatraz.spells.implementation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for the Warp spell.
 *
 * <p>Parsed from the spell's YAML by {@link Warp} and consumed by both the
 * in-game logic and the unit tests.</p>
 */
public record WarpConfig(
        int baseSlots,
        List<Integer> masteryBonusSlots,
        double baseDistance,
        double distancePerCircle,
        double masteryDistanceBonus,
        int windUpSeconds,
        double moveCancelThreshold) {

    public static final List<Integer> DEFAULT_BONUS_SLOTS = List.of(25, 50, 75);

    public static WarpConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new WarpConfig(
                intOf(map.get("base_slots"), 2),
                intsOf(map.get("mastery_bonus_slots"), DEFAULT_BONUS_SLOTS),
                doubleOf(map.get("base_distance"), 500.0),
                doubleOf(map.get("distance_per_circle"), 100.0),
                doubleOf(map.get("mastery_distance_bonus"), 0.5),
                intOf(map.get("wind_up_duration"), 2),
                doubleOf(map.get("move_cancel_threshold"), 0.25));
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private static List<Integer> intsOf(Object o, List<Integer> def) {
        if (o instanceof List<?> list) {
            List<Integer> out = new ArrayList<>();
            for (Object e : list) {
                if (e instanceof Number n) out.add(n.intValue());
            }
            if (!out.isEmpty() && out.size() == list.size()) return out;
        }
        return def;
    }

    public int maxSlots(int mastery) {
        int slots = baseSlots;
        for (int threshold : masteryBonusSlots) {
            if (mastery >= threshold) slots++;
        }
        return slots;
    }

    public int maxSlotsAbsolute() {
        return baseSlots + masteryBonusSlots.size();
    }

    public int masteryForSlot(int slot) {
        int idx = slot - baseSlots;
        if (idx < 0 || idx >= masteryBonusSlots.size()) return 0;
        return masteryBonusSlots.get(idx);
    }

    public double maxDistance(int circleLevel, int mastery, int maxMastery) {
        double base = baseDistance + distancePerCircle * Math.max(0, circleLevel - 6);
        double masteryMultiplier = maxMastery <= 0 ? 0.0 : (double) mastery / maxMastery;
        return base * (1.0 + masteryDistanceBonus * masteryMultiplier);
    }

    public int windUpTicks(int mastery, int maxMastery) {
        double multiplier = (maxMastery > 0 && mastery >= maxMastery) ? 0.75 : 1.0;
        return Math.max(1, (int) Math.round(windUpSeconds * 20.0 * multiplier));
    }
}