package me.nagasonic.alkatraz.spells.implementation;

import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for the Inferno Rite spell.
 *
 * <p>Parsed from the spell's YAML by {@link InfernoRite} and consumed by both the in-game
 * logic and the unit tests. {@code maxChainTargets} is read from the yml's
 * {@code hit_count} key, so the number {@code SpellBudgetTest} multiplies by
 * {@code power} is the same number the spell enforces as its hard per-cast cap.
 */
public record InfernoRiteConfig(
        int maxChainTargets,
        int chainTargetsPerTrigger,
        double chainRadius,
        double duration,
        int igniteTicks,
        int openingRingTicks,
        double detonationKnockback,
        double detonationKnockbackY) {

    public static InfernoRiteConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new InfernoRiteConfig(
                intOf(map.get("hit_count"), 3),
                intOf(map.get("chain_targets_per_trigger"), 2),
                doubleOf(map.get("chain_radius"), 8.0),
                doubleOf(map.get("duration"), 12.0),
                intOf(map.get("ignite_ticks"), 240),
                intOf(map.get("opening_ring_ticks"), 12),
                doubleOf(map.get("detonation_knockback"), 0.6),
                doubleOf(map.get("detonation_knockback_y"), 0.5));
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    /** 8.0 seconds x 20 = 160 ticks of eligibility. NOT a damage cadence. */
    public int durationTicks() {
        return Math.max(1, (int) Math.round(duration * 20.0));
    }

    /** The per-cast cap, floored at 1. Equals the yml's hit_count. */
    public int worstCaseDetonations() {
        return Math.max(1, maxChainTargets);
    }

    /** How many bodies a single triggering hit may detonate. Never above the per-cast cap. */
    public int detonationsPerTrigger() {
        return Math.max(1, Math.min(worstCaseDetonations(), chainTargetsPerTrigger));
    }

    /** power x hit_count, which is what SpellBudgetTest asserts against burstBudget(9). */
    public double worstCaseTotal(double power) {
        return worstCaseDetonations() * power;
    }
}
