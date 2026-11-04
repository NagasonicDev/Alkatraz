package me.nagasonic.alkatraz.spells.implementation;

import java.util.Map;

/**
 * Pure, Bukkit-free configuration for the Oblivion spell.
 *
 * <p>Parsed from the spell's YAML by {@link Oblivion} and consumed by both the
 * in-game logic and the unit tests. Every field is a primitive, so the record is
 * constructible in a plain JUnit test with no live server.
 *
 * <p>{@code power} is deliberately <b>not</b> a field:
 * {@link me.nagasonic.alkatraz.spells.types.AttackSpell#loadCommonConfig} already owns
 * it as {@code basePower}, and reading it a second time here is exactly how the two
 * would drift apart. {@code hit_count} is not a field either - it exists so
 * {@code SpellBudgetTest} can read {@code power x hit_count} and hold this spell to the
 * circle-9 SUSTAIN budget, which is the only thing that ever used the old burst cap.
 */
public record OblivionConfig(
        int targetRange,
        int damageIntervalTicks,
        double upkeepManaPerSecond,
        double vampirismRatio,
        double chasmRadius,
        int debuffSeconds,
        int debuffWeaknessAmplifier,
        int debuffBlindnessAmplifier,
        int debuffWitherAmplifier) {

    public static OblivionConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new OblivionConfig(
                intOf(map.get("target_range"), 24),
                intOf(map.get("damage_interval_ticks"), 48),
                doubleOf(map.get("upkeep_mana_per_second"), 75.0),
                doubleOf(map.get("vampirism_ratio"), 0.25),
                doubleOf(map.get("chasm_radius"), 3.0),
                intOf(map.get("debuff_duration"), 12),
                intOf(map.get("debuff_weakness_amplifier"), 2),
                intOf(map.get("debuff_blindness_amplifier"), 1),
                intOf(map.get("debuff_wither_amplifier"), 1));
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
     * The curse's duration in ticks. {@link Oblivion} re-applies it at every opening and
     * the teardown removes it, so this is how long a victim keeps it after the hole closes
     * only if the teardown never ran.
     */
    public int debuffTicks() {
        return Math.max(1, debuffSeconds) * 20;
    }
}
