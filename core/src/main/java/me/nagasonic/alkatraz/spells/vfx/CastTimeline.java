package me.nagasonic.alkatraz.spells.vfx;

import me.nagasonic.alkatraz.spells.util.SpellBudgets;

/**
 * The circle-to-spectacle ladder from spec section 5.1, in one place. Pure math:
 * no Bukkit, no state. Every number here is a literal transcription of the spec
 * table so {@code CastTimelineTest} and the design cannot disagree.
 *
 * <p>Circles outside 0-9 clamp, so a typo cannot produce an array overrun.
 */
public final class CastTimeline {

    /**
     * Visual tier by circle. Circles 0-1 share the plainest tier, then pairs of
     * circles share a tier up to circle 8, and circle 9 is its own capstone tier.
     */
    private static final int[] TIER = {0, 0, 1, 1, 2, 2, 3, 3, 4, 5};

    /**
     * Spectacle ticks that play <em>after</em> the payload fires. Zero below circle 6;
     * circles 0-5 keep today's visuals unchanged (spec section 3 item 7).
     */
    private static final int[] AFTERMATH_TICKS = {0, 0, 0, 0, 0, 0, 40, 60, 90, 130};

    /**
     * Ceiling on particles spent by the incremental tier layers of the cast glyph, per
     * tick. The base cast glyph is drawn once per repaint period and is not counted
     * against this budget.
     */
    private static final int[] PARTICLE_BUDGET = {24, 24, 48, 80, 140, 220, 260, 300, 340, 340};

    /** Number of tier layers drawn during the cast phase, by circle. */
    private static final int[] LAYER_COUNT = {1, 1, 2, 3, 3, 4, 4, 5, 5, 6};

    private CastTimeline() {
    }

    private static int clamp(int circle) {
        return Math.max(0, Math.min(9, circle));
    }

    public static int tier(int circle) {
        return TIER[clamp(circle)];
    }

    public static int aftermathTicks(int circle) {
        return AFTERMATH_TICKS[clamp(circle)];
    }

    public static boolean hasAftermath(int circle) {
        return aftermathTicks(circle) > 0;
    }

    /**
     * The nominal cast-phase length for a circle, in ticks: the ladder ceiling from
     * {@link SpellBudgets#windUpTicks(int)}. Individual spells may cast shorter (their
     * yml is the contract); this is the base figure the spectacle arc is measured
     * against. It is spectacle sizing only: the framework wind-up uses the
     * actual wand/mastery-adjusted delay from {@code Spell.calculateFinalCastTime},
     * never this ceiling, so a wand discount is real.
     */
    public static int castTicks(int circle) {
        return SpellBudgets.windUpTicks(circle);
    }

    /** Nominal whole-spectacle length: cast phase plus aftermath. Circle 9 is 200 ticks. */
    public static int totalSpectacleTicks(int circle) {
        return castTicks(circle) + aftermathTicks(circle);
    }

    public static int particleBudget(int circle) {
        return PARTICLE_BUDGET[clamp(circle)];
    }

    public static int layerCount(int circle) {
        return LAYER_COUNT[clamp(circle)];
    }
}
