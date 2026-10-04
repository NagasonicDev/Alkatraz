package me.nagasonic.alkatraz.spells.implementation;

import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for the Choir of Light spell.
 *
 * <p>Parsed from the spell's YAML by {@link ChoirOfLight} and consumed by both the
 * in-game logic and the unit tests. Every field is a primitive, so the record is
 * constructible with no server running - which is what lets the defaults be asserted
 * as an ordinary equality check inside {@code ChoirOfLightTest}.</p>
 */
public record ChoirOfLightConfig(
        int shardCount,
        double shardSpeed,
        double shardRange,
        double shardLifetimeSeconds,
        double orbitSeconds,
        double lockRange,
        double spreadDegrees,
        double orbitRadius) {

    /** Mastery multiplier applied to the wind-up once the spell is mastered. */
    private static final double MASTERED_WIND_UP_SCALE = 0.75;

    /** Upper bound on a hand-edited volley, so a bad yml cannot spawn an unbounded volley. */
    private static final int MAX_SHARDS = 12;

    public static ChoirOfLightConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new ChoirOfLightConfig(
                intOf(map.get("shard_count"), 2),
                doubleOf(map.get("shard_speed"), 0.9),
                doubleOf(map.get("shard_range"), 28.0),
                doubleOf(map.get("shard_lifetime"), 2.5),
                doubleOf(map.get("orbit_duration"), 0.5),
                doubleOf(map.get("lock_range"), 32.0),
                doubleOf(map.get("spread_degrees"), 24.0),
                doubleOf(map.get("orbit_radius"), 2.2));
    }

    /**
     * The shipped defaults. Identical to {@code fromConfig(Map.of())} and to
     * {@code fromConfig(null)}, which is what lets a unit test assert one set of literals three
     * ways and pin the record against the spell yml.
     */
    public static ChoirOfLightConfig defaults() {
        return fromConfig(Map.of());
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    /** How long the shards orbit the caster before they launch, in ticks. Never zero. */
    public int orbitTicks(int mastery, int maxMastery) {
        double multiplier = (maxMastery > 0 && mastery >= maxMastery) ? MASTERED_WIND_UP_SCALE : 1.0;
        return Math.max(1, (int) Math.round(orbitSeconds * 20.0 * multiplier));
    }

    /** How long a seeking shard lives, in ticks. Never zero. */
    public int lifetimeTicks() {
        return Math.max(1, (int) Math.round(shardLifetimeSeconds * 20.0));
    }

    /** Clamped so a hand-edited yml cannot spawn an unbounded volley. */
    public int safeShardCount() {
        return Math.max(1, Math.min(MAX_SHARDS, shardCount));
    }
}
