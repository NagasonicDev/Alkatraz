package me.nagasonic.alkatraz.spells.implementation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure, Bukkit-free tuning configuration for Arcane Resonance.
 *
 * <p>Parsed from the spell's YAML by {@link ArcaneResonance} and consumed by both the in-game logic
 * and the unit tests.
 *
 * <h2>The caps are the spell</h2>
 * An amplified cast lands twice at {@link #powerMultiplier()} each, so this is a real 2.5x on every
 * cast made while the sustain is live. Without {@code charge_count} the four charges would be an
 * unbounded damage button; without {@link #duplicateDelayTicks()} the twin would land on the same
 * tick and collide with the original's hit set; without {@link #duplicateSkipIds()} copying a
 * sustain would toggle it straight back off. All of them are server-authority knobs, which is why
 * this spell ships no options file at all.
 */
public record ArcaneResonanceConfig(
        int chargeCount,
        double powerMultiplier,
        int duplicateDelayTicks,
        double upkeepManaPerSecond,
        List<String> skipIds,
        List<String> duplicateSkipIds) {

    /**
     * Spells that are never amplified.
     *
     * <p>{@code arcane_resonance} is self-amplification, which would compound without bound.
     * {@code echo_cast} re-fires other spells, so amplifying it would double an already-multiplied
     * rotation. {@code warp} calls {@code cancelCast(caster)} unconditionally and opens a GUI, so
     * firing it twice opens two menus and poisons the caster's next genuine warp.
     */
    public static final List<String> DEFAULT_SKIP_IDS = List.of("arcane_resonance", "echo_cast", "warp");

    /**
     * Spells the sustain never copies.
     *
     * <p>A DIFFERENT question from {@link #DEFAULT_SKIP_IDS}: the skip list decides whether a
     * spell is amplified, this one decides whether it is fired a second time. The five circle-9
     * sustains are here because a duplicated toggle cast would end the sustain on the same tick
     * it amplified something, and the two terrain spells because a second cast spends their block
     * budget twice.
     */
    public static final List<String> DEFAULT_DUPLICATE_SKIP_IDS = List.of(
            "arcane_resonance", "skywrath", "tide_of_the_deep", "crown_of_dawn", "oblivion",
            "worldbreaker", "inferno_rite");

    public ArcaneResonanceConfig {
        skipIds = skipIds == null || skipIds.isEmpty() ? DEFAULT_SKIP_IDS : List.copyOf(skipIds);
        duplicateSkipIds = duplicateSkipIds == null || duplicateSkipIds.isEmpty()
                ? DEFAULT_DUPLICATE_SKIP_IDS : List.copyOf(duplicateSkipIds);
    }

    public static ArcaneResonanceConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new ArcaneResonanceConfig(
                intOf(map.get("charge_count"), 4),
                doubleOf(map.get("power_multiplier"), 1.25),
                // A zero here would put the twin on the same tick as the original, colliding with
                // its SpellParticleComponent registrations and AttackProperties hit set.
                Math.max(1, intOf(map.get("duplicate_delay_ticks"), 10)),
                Math.max(0.0, doubleOf(map.get("upkeep_mana_per_second"), 55.0)),
                stringsOf(map.get("skip_ids"), DEFAULT_SKIP_IDS),
                stringsOf(map.get("duplicate_skip_ids"), DEFAULT_DUPLICATE_SKIP_IDS));
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static double doubleOf(Object o, double def) {
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private static List<String> stringsOf(Object o, List<String> def) {
        if (o instanceof List<?> list && !list.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (Object e : list) {
                if (e != null) out.add(String.valueOf(e));
            }
            if (!out.isEmpty()) return out;
        }
        return def;
    }

    /** Whether a spell id is amplified while the sustain is live. */
    public boolean isSkipped(String spellId) {
        return spellId != null && skipIds.contains(spellId);
    }

    /**
     * Whether a spell id may be fired a second time by the sustain.
     *
     * <p>Inverse of {@link #duplicateSkipIds}, so a null id is not duplicable rather than an NPE -
     * {@link List#contains} on an immutable list rejects null.
     */
    public boolean isDuplicable(String spellId) {
        return spellId != null && !duplicateSkipIds.contains(spellId);
    }
}
