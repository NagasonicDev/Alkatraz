package me.nagasonic.alkatraz.spells.implementation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure, Bukkit-free scaling configuration for Echo Cast.
 *
 * <p>Parsed from the spell's YAML by {@link EchoCast} and consumed by both the in-game logic and
 * the unit tests.
 *
 * <h2>The 60% lives here and nowhere else</h2>
 * {@link #replayCost(int)} is a <b>mana</b> model. There is no {@code damage} modifier, no
 * {@code StatModifierImpact} and no {@code getPower} call anywhere in {@code EchoCast}, so the
 * percentage cannot leak into a damage path - it has none to leak into.
 */
public record EchoCastConfig(
        int historySize,
        int replayCostPercent,
        int replayGapTicks,
        List<String> skipIds) {

    /**
     * Spells that are never recorded and never replayed.
     *
     * <p>{@code echo_cast} is a self-reference. {@code arcane_resonance} installs pooled
     * {@code StatModifierImpact}s and its own listener would amplify the replay. {@code warp} and
     * {@code blink} both call {@code cancelCast(caster)} on a path a replay can reach, which would
     * leave the caster registered in that spell's cancel set until its <i>next</i> genuine cast -
     * a silent refund plus a skipped cooldown and mastery.
     */
    public static final List<String> DEFAULT_SKIP_IDS =
            List.of("echo_cast", "arcane_resonance", "warp", "blink");

    public EchoCastConfig {
        skipIds = skipIds == null || skipIds.isEmpty() ? DEFAULT_SKIP_IDS : List.copyOf(skipIds);
    }

    public static EchoCastConfig fromConfig(Map<String, Object> map) {
        if (map == null) map = Map.of();
        return new EchoCastConfig(
                Math.max(1, intOf(map.get("history_size"), 3)),
                intOf(map.get("replay_cost_percent"), 60),
                Math.max(1, intOf(map.get("replay_gap_ticks"), 8)),
                stringsOf(map.get("skip_ids"), DEFAULT_SKIP_IDS));
    }

    private static int intOf(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
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

    /** Whether a spell id is on the skip list and so must never be echoed. */
    public boolean isSkipped(String spellId) {
        return spellId != null && skipIds.contains(spellId);
    }

    /**
     * What one replay of a spell costing {@code baseCost} mana charges the caster.
     *
     * <p>Rounded <b>up</b>, never down: 60% of 15 is 9.0, and a floor would let a cheap spell
     * round to a mana price of zero.
     */
    public int replayCost(int baseCost) {
        if (baseCost <= 0) return 0;
        return (int) Math.ceil(baseCost * (replayCostPercent / 100.0));
    }
}