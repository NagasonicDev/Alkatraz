package me.nagasonic.alkatraz.spells.implementation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for Arcane Resonance.
 *
 * <p>Parsed from the spell's YAML by {@link ArcaneResonance} and consumed by both the in-game logic
 * and the unit tests.
 *
 * <h2>The three caps are the spell</h2>
 * An amplified cast lands twice at {@link #powerMultiplier()} each, so this is a real 2.4x on up to
 * {@link #chargeCount()} casts. Without {@code charge_count} it would be a permanent damage button;
 * without {@link #chargeExpirySeconds()} a charge could be banked across a whole fight; without the
 * unstackable guard in {@code castAction} it would compound. All three are server-authority knobs,
 * which is why this spell ships no options file at all.
 */
public record ArcaneResonanceConfig(
        int chargeCount,
        double powerMultiplier,
        double chargeExpirySeconds,
        List<String> skipIds) {

    /** Ticks per second; the yml declares the expiry in seconds and the timer wants ticks. */
    private static final double TICKS_PER_SECOND = 20.0;

    /**
     * Spells that are never amplified and never burn a charge.
     *
     * <p>{@code arcane_resonance} is self-amplification, which would compound without bound.
     * {@code echo_cast} re-fires other spells, so amplifying it would double an already-multiplied
     * rotation. {@code warp} calls {@code cancelCast(caster)} unconditionally and opens a GUI, so
     * firing it twice opens two menus and poisons the caster's next genuine warp.
     */
    public static final List<String> DEFAULT_SKIP_IDS = List.of("arcane_resonance", "echo_cast", "warp");

    public ArcaneResonanceConfig {
        skipIds = skipIds == null || skipIds.isEmpty() ? DEFAULT_SKIP_IDS : List.copyOf(skipIds);
    }

    public static ArcaneResonanceConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new ArcaneResonanceConfig(
                intOf(map.get("charge_count"), 3),
                doubleOf(map.get("power_multiplier"), 1.2),
                doubleOf(map.get("charge_expiry_seconds"), 20.0),
                stringsOf(map.get("skip_ids"), DEFAULT_SKIP_IDS));
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

    /** Whether a spell id is skipped: never amplified, and casting it never burns a charge. */
    public boolean isSkipped(String spellId) {
        return spellId != null && skipIds.contains(spellId);
    }

    /**
     * The unused-charge timer, in ticks.
     *
     * <p>Floored at one: {@code runTaskLater(..., 0)} fires on the current tick, which would clear
     * the charge before the caster could ever spend it.
     */
    public int expiryTicks() {
        return Math.max(1, (int) Math.round(chargeExpirySeconds * TICKS_PER_SECOND));
    }
}