package me.nagasonic.alkatraz.spells.implementation;

import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for the Crown of Dawn spell.
 *
 * <p>Parsed from the spell's YAML by {@link CrownOfDawn} and consumed by both the
 * in-game logic and the unit tests. Every field is a primitive, so the record is
 * constructible with no server running - which is what lets the defaults be asserted
 * as an ordinary equality check inside {@code CrownOfDawnTest}.</p>
 */
public record CrownOfDawnConfig(
        double hoverRadius,
        int hoverAltitude,
        int damageIntervalTicks,
        int upkeepManaPerSecond) {

    private static final double TICKS_PER_SECOND = 20.0;

    public static CrownOfDawnConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new CrownOfDawnConfig(
                doubleOf(map.get("hover_radius"), 10.0),
                intOf(map.get("hover_altitude"), 6),
                intOf(map.get("damage_interval_ticks"), 40),
                intOf(map.get("upkeep_mana_per_second"), 60));
    }

    /**
     * The shipped defaults. Identical to {@code fromConfig(Map.of())} and to
     * {@code fromConfig(null)}, which is what lets a unit test assert one set of literals three
     * ways and pin the record against the spell yml.
     */
    public static CrownOfDawnConfig defaults() {
        return fromConfig(Map.of());
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    /**
     * How many armour-sear openings one target takes over a sustain of that length. 60s at the
     * declared 40-tick cadence is the 30 the yml declares in {@code hit_count}.
     */
    public int hitsPerTarget(double durationSeconds) {
        if (damageIntervalTicks <= 0) return 1;
        return Math.max(1, (int) Math.round(durationSeconds * TICKS_PER_SECOND / damageIntervalTicks));
    }
}
