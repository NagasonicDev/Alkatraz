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
        double wallDuration,
        double wallLength,
        double wallHeight,
        double barrierHitpoints,
        double wallHalfThickness,
        double pushStrength,
        double executionThreshold,
        double segmentSpacing,
        double riseTicks,
        double denialDamageCap) {

    /**
     * Mirrors {@code SpellAttackContext.DAMAGE_INTERVAL_TICKS}. Declared locally so this
     * record stays provably Bukkit-free; {@code CrownOfDawnTest} pins the two constants
     * together so they cannot drift.
     */
    public static final long DAMAGE_INTERVAL_TICKS = 20L;

    private static final double TICKS_PER_SECOND = 20.0;
    /** Floor on the segment spacing divisor, so a zero cannot divide by zero. */
    private static final double MIN_SPACING = 0.1;

    public static CrownOfDawnConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new CrownOfDawnConfig(
                doubleOf(map.get("wall_duration"), 15.0),
                doubleOf(map.get("wall_length"), 9.0),
                doubleOf(map.get("wall_height"), 3.5),
                doubleOf(map.get("barrier_hitpoints"), 60.0),
                doubleOf(map.get("wall_half_thickness"), 0.9),
                doubleOf(map.get("push_strength"), 0.45),
                doubleOf(map.get("execution_threshold"), 0.3),
                doubleOf(map.get("segment_spacing"), 0.5),
                doubleOf(map.get("rise_ticks"), 10.0),
                doubleOf(map.get("denial_damage_cap"), 90.0));
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

    /** How long the wall stands, in ticks. Never zero. */
    public int durationTicks() {
        return Math.max(1, (int) Math.round(wallDuration * TICKS_PER_SECOND));
    }

    /** How many segments the wall is built from along its length. Never fewer than one. */
    public int segmentCount() {
        return Math.max(1, (int) Math.round(wallLength / Math.max(MIN_SPACING, segmentSpacing)));
    }

    /** How many ticks the wall takes to reach full height. Never zero. */
    public int riseTickCount() {
        return Math.max(1, (int) Math.round(riseTicks));
    }

    /**
     * How many damage passes one cast makes. {@code SpellBudgetTest} reads
     * {@code power * hit_count} as the spell's PER-CAST total, so this is a cadence
     * count, not a per-target one - {@code AttackProperties.hasHit} caps any single
     * entity at one application per cast.
     */
    public int hitCount() {
        return Math.max(1, durationTicks() / (int) DAMAGE_INTERVAL_TICKS);
    }

    /** The per-application power that fills {@code denialDamageCap} exactly. */
    public double powerForCap() {
        return denialDamageCap / hitCount();
    }
}
