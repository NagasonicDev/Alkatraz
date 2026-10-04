package me.nagasonic.alkatraz.spells.implementation;

import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for the Oblivion spell.
 *
 * <p>Parsed from the spell's YAML by {@link Oblivion} and consumed by both the
 * in-game logic and the unit tests. Every field is a primitive, so the record is
 * constructible in a plain JUnit test with no live server.
 *
 * <p>{@code power} is deliberately <b>not</b> a field:
 * {@link me.nagasonic.alkatraz.spells.types.AttackSpell#loadCommonConfig} already owns
 * it as {@code basePower}, and reading it a second time here is exactly how the two
 * would drift apart.
 */
public record OblivionConfig(
        double missingHealthMultiplier,
        double burstDamageCap,
        int targetRange,
        int debuffSeconds,
        int weaknessAmplifier,
        int blindnessAmplifier,
        int erasureWaveTicks,
        double erasureWaveRadius,
        double erasureKnockback,
        double erasureLift) {

    public static OblivionConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new OblivionConfig(
                doubleOf(map.get("missing_health_multiplier"), 3.15),
                doubleOf(map.get("burst_damage_cap"), 90.0),
                intOf(map.get("target_range"), 24),
                intOf(map.get("debuff_duration"), 12),
                intOf(map.get("debuff_weakness_amplifier"), 2),
                intOf(map.get("debuff_blindness_amplifier"), 1),
                intOf(map.get("erasure_wave_ticks"), 30),
                doubleOf(map.get("erasure_wave_radius"), 3.0),
                doubleOf(map.get("erasure_knockback"), 0.6),
                doubleOf(map.get("erasure_lift"), 0.35));
    }

    /**
     * The shipped defaults, carrying the same literals {@code spells/oblivion.yml}
     * does. Lets a test assert the record's own fallbacks without a yml.
     */
    public static OblivionConfig defaults() {
        return fromConfig(Map.of());
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    /**
     * The whole damage model, as one pure function.
     *
     * <p>A full-health target takes {@code basePower}; a target on its last point takes
     * {@code basePower * multiplier}, truncated to {@code burstDamageCap}.
     * {@code 28.6 x 3.15 = 90.09}, so the cap is what actually binds - without it this
     * spell would declare 90.09 and sit over the circle-9 BURST budget of 90.0.
     *
     * @param basePower       the raw base power, before affinity or resistance
     * @param missingFraction how much of the target's maximum health is already gone, 0.0-1.0
     * @return the scaled base damage, never above {@link #burstDamageCap()}
     */
    public double scaledDamage(double basePower, double missingFraction) {
        double fraction = Math.max(0.0, Math.min(1.0, missingFraction));
        double scale = 1.0 + fraction * (missingHealthMultiplier - 1.0);
        return Math.min(basePower * scale, burstDamageCap);
    }

    /** The most a single target can take: a full-missing hit, truncated to the cap. */
    public double worstCaseDamage(double basePower) {
        return scaledDamage(basePower, 1.0);
    }

    /** The least a single target can take: base power, untouched, against a healthy one. */
    public double minimumDamage(double basePower) {
        return scaledDamage(basePower, 0.0);
    }

    /**
     * The curse's duration in ticks. Nothing in {@link Oblivion} ever removes the
     * effect, so vanilla expires it exactly this long after it was applied.
     */
    public int debuffTicks() {
        return Math.max(1, debuffSeconds) * 20;
    }

    /** The erasure wave's radius after {@code ticks} of its {@code erasure_wave_ticks} life. */
    public double erasureRadiusAt(int ticks) {
        int life = Math.max(1, erasureWaveTicks);
        return Math.max(0.1, (double) ticks / life * erasureWaveRadius);
    }
}