package me.nagasonic.alkatraz.spells.implementation;

import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for the Apotheosis spell.
 *
 * <p>Parsed from the spell's YAML by {@link Apotheosis} and consumed by both the
 * in-game logic and the unit tests. Every field is a primitive, so the record is
 * constructible with no server running - which is what lets the defaults be asserted
 * as an ordinary equality check inside {@code ApotheosisTest}.</p>
 */
public record ApotheosisConfig(
        double channelSeconds,
        double buffDurationSeconds,
        double auraRadius,
        double moveCancelThreshold) {

    /** Mastery multiplier applied to the channel once the spell is mastered. */
    private static final double MASTERED_CHANNEL_SCALE = 0.75;

    public static ApotheosisConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new ApotheosisConfig(
                doubleOf(map.get("channel_duration"), 6.0),
                doubleOf(map.get("buff_duration"), 30.0),
                doubleOf(map.get("aura_radius"), 6.0),
                doubleOf(map.get("move_cancel_threshold"), 0.3));
    }

    /**
     * The shipped defaults. Identical to {@code fromConfig(Map.of())} and to
     * {@code fromConfig(null)}, which is what lets a unit test assert one set of literals three
     * ways and pin the record against the spell yml.
     */
    public static ApotheosisConfig defaults() {
        return fromConfig(Map.of());
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    /** How long the caster must stand still, in ticks. Never zero. */
    public int channelTicks(int mastery, int maxMastery) {
        double multiplier = (maxMastery > 0 && mastery >= maxMastery) ? MASTERED_CHANNEL_SCALE : 1.0;
        return Math.max(1, (int) Math.round(channelSeconds * 20.0 * multiplier));
    }

    /** How long the ascendant holds once the channel lands, in ticks. Never zero. */
    public int buffTicks() {
        return Math.max(1, (int) Math.round(buffDurationSeconds * 20.0));
    }
}
