package me.nagasonic.alkatraz.spells.implementation;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for Skywrath.
 *
 * <p>Parsed from the spell's YAML by {@link Skywrath} and consumed by both the
 * in-game logic and the unit tests. Every field has an explicit default.</p>
 *
 * <p>The flight keys live here too, so the grant is unit-testable: a spell that
 * flies the caster for fifteen seconds and a spell that flies the caster forever
 * differ only by whether {@code flightAllow} is true and whether
 * {@code flightTicksPerStormTick} is above or below 1.0.</p>
 *
 * <p>No component is a Bukkit type, which is what keeps the whole record
 * constructible in a unit test with no server running. The only Bukkit type
 * anywhere in this file is the {@code YamlConfiguration} parameter of
 * {@link #from(YamlConfiguration)}, and that is a method argument, never a field.</p>
 */
public record SkywrathConfig(
        double stormDuration,
        double stormRadius,
        double stormHeight,
        double strikeIntervalSeconds,
        boolean flightAllow,
        double flightTicksPerStormTick,
        double flightSpeed) {

    /**
     * Every literal the spell's yml declares, with nothing overridden. A test
     * asserts against this so the record's defaults and the yml cannot drift.
     */
    public static SkywrathConfig defaults() {
        return fromConfig(Map.of());
    }

    /** Reads the record out of a live spell yml. A null config falls back to {@link #defaults()}. */
    public static SkywrathConfig from(YamlConfiguration config) {
        return config == null ? defaults() : fromConfig(config.getValues(false));
    }

    public static SkywrathConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new SkywrathConfig(
                doubleOf(map.get("storm_duration"), 15.0),
                doubleOf(map.get("storm_radius"), 8.0),
                doubleOf(map.get("storm_height"), 12.0),
                doubleOf(map.get("strike_interval_seconds"), 1.0),
                boolOf(map.get("flight_allow"), true),
                doubleOf(map.get("flight_ticks_per_storm_tick"), 1.0),
                doubleOf(map.get("flight_speed"), 0.15));
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private static boolean boolOf(Object o, boolean def) {
        return o instanceof Boolean b ? b : def;
    }

    /** The storm's life, in ticks. 15.0 -&gt; 300. */
    public int stormTicks() {
        return Math.max(1, (int) Math.round(stormDuration * 20.0));
    }

    /** The mandated cadence gap in ticks. Always 20 at this circle. */
    public int strikeIntervalTicks() {
        return Math.max(1, (int) Math.round(strikeIntervalSeconds * 20.0));
    }

    /** Discrete damage applications one target can take. Equals the yml's {@code hit_count}. */
    public int hitsPerTarget() {
        return Math.max(1, (int) Math.round(stormDuration));
    }

    /** How many damage windows actually open inside the storm's life. */
    public int damageWindows() {
        return (stormTicks() + strikeIntervalTicks() - 1) / strikeIntervalTicks();
    }

    /**
     * The flight grant, in ticks, in its natural unit: {@code 1} means "for
     * exactly the storm's duration". The caller multiplies this by
     * {@link #stormTicks()} and the product must never exceed the storm's life, so
     * {@link #flightTicksWithin(int)} is what the implementation actually grants.
     */
    public int flightTicks() {
        return (int) Math.round(stormTicks() * flightTicksPerStormTick);
    }

    /**
     * The grant clamped to the storm's own life. The spell asserts on launch that
     * the unclamped value fits, but a hand-edited config must never leave a player
     * airborne after the storm has gone.
     */
    public int flightTicksWithin(int stormLifeTicks) {
        return Math.min(flightTicks(), Math.max(0, stormLifeTicks));
    }

    public double declaredTotal(double power) {
        return power * hitsPerTarget();
    }
}