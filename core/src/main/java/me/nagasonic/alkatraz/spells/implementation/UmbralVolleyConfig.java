package me.nagasonic.alkatraz.spells.implementation;

import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for the Umbral Volley spell.
 *
 * <p>Parsed from the spell's YAML by {@link UmbralVolley} and consumed by both the
 * in-game logic and the unit tests. Every field is a primitive, so the record is
 * constructible in a plain JUnit test with no live server - which is the whole
 * reason it exists rather than a pile of {@code spellConfig.getDouble(...)} calls
 * scattered through the spell class.
 *
 * <p>Every read carries a literal default that matches the shipped yml, so an
 * absent or unreadable key degrades to the documented value rather than to
 * {@code 0.0}.
 */
public record UmbralVolleyConfig(
        int dartCount,
        double dartSpread,
        double dartSpeed,
        double dartRange,
        int dartLifetimeTicks,
        double dartTrackRange,
        double dartHoming,
        int splitCount,
        int splitShardTicks,
        double splitSpeed,
        int debuffSeconds,
        int weaknessAmplifier,
        int blindnessAmplifier,
        int mobDartCount) {

    public static UmbralVolleyConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new UmbralVolleyConfig(
                intOf(map.get("dart_count"), 3),
                doubleOf(map.get("dart_spread"), 12.0),
                doubleOf(map.get("dart_speed"), 0.9),
                doubleOf(map.get("dart_range"), 26.0),
                intOf(map.get("dart_lifetime_ticks"), 40),
                doubleOf(map.get("dart_track_range"), 18.0),
                doubleOf(map.get("dart_homing"), 0.35),
                intOf(map.get("split_count"), 2),
                intOf(map.get("split_shard_ticks"), 6),
                doubleOf(map.get("split_speed"), 0.55),
                intOf(map.get("debuff_duration"), 8),
                intOf(map.get("debuff_weakness_amplifier"), 1),
                intOf(map.get("debuff_blindness_amplifier"), 0),
                intOf(map.get("mob_dart_count"), 2));
    }

    /**
     * The shipped defaults, carrying the same literals {@code spells/umbral_volley.yml}
     * does. Lets a test assert the record's own fallbacks without a yml, and keeps the
     * two in one place.
     */
    public static UmbralVolleyConfig defaults() {
        return fromConfig(Map.of());
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    /**
     * The even horizontal fan, in degrees, for a volley of {@code count} darts at this
     * record's {@code dart_spread}. Three darts at 12 degrees is -6.0, 0.0, +6.0.
     *
     * <p>A count of one or less gets a single zero offset rather than a division by
     * {@code count - 1}.
     */
    public double[] yawOffsets(int count) {
        return yawOffsets(dartSpread, count);
    }

    /**
     * The same fan at an explicit spread, which is how the mob path halves the fan
     * without a second config record.
     */
    public static double[] yawOffsets(double spreadDegrees, int count) {
        int n = Math.max(1, count);
        double[] offsets = new double[n];
        if (n == 1) return offsets;
        double half = spreadDegrees / 2.0;
        double step = spreadDegrees / (n - 1);
        for (int i = 0; i < n; i++) offsets[i] = -half + step * i;
        return offsets;
    }

    /**
     * The curse's duration in ticks. This is the whole of dark's identity for this
     * spell: nothing in {@link UmbralVolley} ever removes the effect, so vanilla
     * expires it exactly this long after it was applied - long after the cast is over.
     */
    public int debuffTicks() {
        return Math.max(1, debuffSeconds) * 20;
    }

    /** How far one dart may travel before it gives up, in blocks. */
    public double dartRangeBlocks() {
        return Math.max(0.0, dartSpeed) * Math.max(0, dartLifetimeTicks);
    }

    /** Worst-case discrete damage applications one cast makes to one target: the dart count. */
    public int worstCaseHitsPerTarget() {
        return Math.max(1, dartCount);
    }
}