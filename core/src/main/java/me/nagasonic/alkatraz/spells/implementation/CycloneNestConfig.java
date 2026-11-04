package me.nagasonic.alkatraz.spells.implementation;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for Cyclone Nest.
 *
 * <p>Parsed from the spell's YAML by {@link CycloneNest} and consumed by both the
 * in-game logic and the unit tests. Every field has an explicit default, so a
 * missing key can never silently read as {@code 0.0} - which is the exact class
 * of bug this record exists to make impossible.</p>
 *
 * <p>No component is a Bukkit type, which is what keeps the whole record
 * constructible in a unit test with no server running. The only Bukkit type
 * anywhere in this file is the {@code YamlConfiguration} parameter of
 * {@link #from(YamlConfiguration)}, and that is a method argument, never a field.</p>
 */
public record CycloneNestConfig(
        int nestCount,
        double nestLifetime,
        double nestRadius,
        double nestHeight,
        double nestPull,
        double nestWanderSpeed,
        double nestSpawnSpread) {

    /**
     * Every literal the spell's yml declares, with nothing overridden.
     *
     * <p>This is the factory a test asserts against so the record's defaults and
     * the yml cannot drift apart: if an admin edits {@code nest_radius} in the
     * yml, {@code CycloneNestTest} fails rather than silently shipping a spell
     * whose code and config disagree.</p>
     */
    public static CycloneNestConfig defaults() {
        return fromConfig(Map.of());
    }

    /** Reads the record out of a live spell yml. A null config falls back to {@link #defaults()}. */
    public static CycloneNestConfig from(YamlConfiguration config) {
        return config == null ? defaults() : fromConfig(config.getValues(false));
    }

    public static CycloneNestConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new CycloneNestConfig(
                Math.max(1, intOf(map.get("nest_count"), 3)),
                doubleOf(map.get("nest_lifetime"), 8.0),
                doubleOf(map.get("nest_radius"), 2.5),
                doubleOf(map.get("nest_height"), 6.0),
                doubleOf(map.get("nest_pull"), 0.55),
                doubleOf(map.get("nest_wander_speed"), 0.4),
                doubleOf(map.get("nest_spawn_spread"), 4.0));
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    /** The whole life of one nest, in ticks. Also the lifetime of the cast. */
    public int lifetimeTicks() {
        return Math.max(1, (int) Math.round(nestLifetime * 20.0));
    }

    /**
     * The number of discrete damage applications one target can take from one
     * nest. This is the value the yml's {@code hit_count} key must equal, because
     * all three nests share ONE cadence clock.
     */
    public int hitsPerTarget() {
        return Math.max(1, (int) Math.round(nestLifetime));
    }

    /** Worst-case total damage to one target from one cast, all three nests combined. */
    public double declaredTotal(double power) {
        return power * hitsPerTarget();
    }

    /**
     * The funnel's radius at height fraction {@code t} of the cone: 35% of
     * {@code nest_radius} at the throat, 115% at the mouth.
     */
    public double funnelRadiusAt(double t) {
        return nestRadius * (0.35 + 0.80 * t);
    }

    /**
     * How many damage windows actually open inside the field's life.
     *
     * <p>The gate opens on ticks 0, 20, 40 ... of a {@link #lifetimeTicks()} run,
     * so this is the count the budget test's {@code power x hit_count} assumes.
     * With {@code nest_lifetime} 8.0 it returns 8.</p>
     */
    public int damageWindows() {
        return (lifetimeTicks() + 19) / 20;
    }
}
