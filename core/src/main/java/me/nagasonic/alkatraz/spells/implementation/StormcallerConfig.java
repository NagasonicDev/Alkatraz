package me.nagasonic.alkatraz.spells.implementation;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for Stormcaller.
 *
 * <p>Parsed from the spell's YAML by {@link Stormcaller} and consumed by both the
 * in-game logic and the unit tests. Every field has an explicit default, so a
 * missing key can never silently read as {@code 0.0}.</p>
 *
 * <p>No component is a Bukkit type, which is what keeps the whole record
 * constructible in a unit test with no server running. The only Bukkit type
 * anywhere in this file is the {@code YamlConfiguration} parameter of
 * {@link #from(YamlConfiguration)}, and that is a method argument, never a field.</p>
 */
public record StormcallerConfig(
        double channelDuration,
        double stormDuration,
        double stormRadius,
        double stormHeight,
        double strikeIntervalSeconds,
        double moveCancelThreshold) {

    /**
     * Every literal the spell's yml declares, with nothing overridden. A test
     * asserts against this so the record's defaults and the yml cannot drift.
     */
    public static StormcallerConfig defaults() {
        return fromConfig(Map.of());
    }

    /** Reads the record out of a live spell yml. A null config falls back to {@link #defaults()}. */
    public static StormcallerConfig from(YamlConfiguration config) {
        return config == null ? defaults() : fromConfig(config.getValues(false));
    }

    public static StormcallerConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new StormcallerConfig(
                doubleOf(map.get("channel_duration"), 2.25),
                doubleOf(map.get("storm_duration"), 12.0),
                doubleOf(map.get("storm_radius"), 6.0),
                doubleOf(map.get("storm_height"), 9.0),
                doubleOf(map.get("strike_interval_seconds"), 1.0),
                doubleOf(map.get("move_cancel_threshold"), 0.30));
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    /**
     * Channel length in ticks, with the same 0.75x max-mastery discount
     * {@code WarpConfig.windUpTicks} applies: 2.25 s -&gt; 45 ticks, or 34 at max
     * mastery.
     */
    public int channelTicks(int mastery, int maxMastery) {
        return channelTicksOf(channelDuration, mastery, maxMastery);
    }

    /**
     * The static half of {@link #channelTicks(int, int)}, so a caller that has
     * already resolved a {@code channel_duration} stat modifier through
     * {@code getModifiedStat} can still fold in the mastery discount.
     */
    public static int channelTicksOf(double channelDuration, int mastery, int maxMastery) {
        double multiplier = (maxMastery > 0 && mastery >= maxMastery) ? 0.75 : 1.0;
        return Math.max(1, (int) Math.round(channelDuration * 20.0 * multiplier));
    }

    /** Total lifetime of the storm, in ticks. 12.0 -&gt; 240. */
    public int stormTicks() {
        return Math.max(1, (int) Math.round(stormDuration * 20.0));
    }

    /** The cadence gap in ticks. 20 at this circle - the SUSTAIN rule is not configurable. */
    public int strikeIntervalTicks() {
        return Math.max(1, (int) Math.round(strikeIntervalSeconds * 20.0));
    }

    /**
     * The number of discrete damage applications one target can take. The gate
     * opens on tick 0 and every {@link #strikeIntervalTicks()} after it, inside a
     * window of {@link #stormTicks()} ticks, which is exactly
     * {@code round(stormDuration)} openings - and therefore exactly the yml's
     * {@code hit_count}.
     */
    public int hitsPerTarget() {
        return Math.max(1, (int) Math.round(stormDuration));
    }

    /** How many damage windows actually open inside the storm's life. */
    public int damageWindows() {
        return (stormTicks() + strikeIntervalTicks() - 1) / strikeIntervalTicks();
    }

    public double declaredTotal(double power) {
        return power * hitsPerTarget();
    }
}