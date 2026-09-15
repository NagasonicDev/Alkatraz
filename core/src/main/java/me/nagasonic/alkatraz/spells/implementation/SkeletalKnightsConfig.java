package me.nagasonic.alkatraz.spells.implementation;

import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for the Skeletal Knights spell.
 *
 * <p>Parsed from the spell's YAML by {@link SkeletalKnights} and consumed by both the
 * in-game logic and the unit tests.</p>
 */
public record SkeletalKnightsConfig(
        int knightCount,
        int knightDuration,
        double knightPower,
        double summonRange,
        int windUpSeconds,
        double moveCancelThreshold) {

    public static SkeletalKnightsConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new SkeletalKnightsConfig(
                intOf(map.get("knight_count"), 3),
                intOf(map.get("knight_duration"), 30),
                doubleOf(map.get("knight_power"), 6.0),
                doubleOf(map.get("summon_range"), 5.0),
                intOf(map.get("wind_up_duration"), 6),
                doubleOf(map.get("move_cancel_threshold"), 0.25));
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    public int windUpTicks(int mastery, int maxMastery) {
        double multiplier = (maxMastery > 0 && mastery >= maxMastery) ? 0.75 : 1.0;
        return Math.max(1, (int) Math.round(windUpSeconds * 20.0 * multiplier));
    }
}
