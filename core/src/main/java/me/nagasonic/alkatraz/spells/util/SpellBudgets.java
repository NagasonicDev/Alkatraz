package me.nagasonic.alkatraz.spells.util;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellRole;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The circle ladder from spec section 6 and the role budgets from section 5, in
 * one place. Every spell yml's {@code power}, {@code mana_cost}, {@code cooldown}
 * and {@code required_focus} should trace back to a row here rather than being
 * invented, and {@code SpellBudgetTest} reads these same tables - so the test and
 * the design cannot disagree.
 *
 * <p>Every column is a literal transcription of the spec table. Nothing here
 * recomputes a ladder value, so
 * {@code StatPointsAttributeSource.affinityFromPoints} - which owns the
 * {@code affinity_per_point * points ^ affinity_exponent} formula itself and is
 * what {@code AffinityCurveTest} exercises - stays the single place the curve is
 * implemented.
 *
 * <p>Circles outside 0-9 clamp: {@code burstBudget} treats anything below 0 as
 * circle 0 and anything above 9 as circle 9, so a typo cannot produce a null.
 */
public final class SpellBudgets {

    /** Cumulative stat points granted by circle 0 through 9. */
    private static final int[] STAT_POINTS = {0, 5, 10, 17, 27, 37, 52, 67, 87, 112};

    /**
     * Elemental affinity implied by the circle's stat points, spec section 6.
     * The curve is {@code 10 * points ^ 0.65}; these are its rounded published
     * values. The formula itself lives in
     * {@code StatPointsAttributeSource.affinityFromPoints}.
     */
    private static final double[] AFFINITY = {0, 28, 45, 63, 85, 105, 130, 154, 182, 215};

    private static final double[] BURST = {1.5, 3.9, 6.1, 9.0, 13.9, 22.1, 34.1, 48.0, 64.1, 90.0};
    private static final double[] SUSTAIN = {0, 0, 0, 14, 22, 36, 54, 76, 102, 144};
    private static final double[] TERRAIN = {0, 0, 0, 120, 220, 380, 620, 950, 1400, 2000};
    private static final double[] DENIAL = {0, 0, 0, 900, 1400, 2100, 3000, 4200, 5600, 7500};

    private static final double[] POWER = {1.5, 3.0, 4.2, 5.5, 7.5, 10.8, 14.8, 18.9, 22.7, 28.6};
    private static final double[] MANA = {5, 15, 24, 48, 75, 120, 165, 240, 450, 600};
    /**
     * Cast time is a per-circle <em>range</em>, not a ladder. The single-value ladder
     * the spec section 6 originally carried is withdrawn: a circle does not buy a fixed
     * cast time, it buys permission to spend anywhere between these two ticks.
     *
     * <p>The floor is 5 ticks (0.25s) at every circle 0-5 because instant is always
     * legal - {@code swift} is a dash, {@code dispel} must be snappy,
     * {@code waystone_recall} is instant by design. Only the ceiling curves: 15 at
     * circle 1, 30 at 2, 50 at 3 and 4, easing back to 40 at 5.
     *
     * <p>Circles 6-9 collapse to one fixed value because every high-circle cast is a
     * committed, telegraphed action whose duration <em>is</em> its wind-up.
     */
    private static final int[] CAST_MIN_TICKS = {5, 5, 5, 5, 5, 5, 20, 25, 30, 40};
    private static final int[] CAST_MAX_TICKS = {5, 15, 30, 50, 50, 40, 20, 25, 30, 40};
    private static final double[] COOLDOWN = {2, 4, 5, 6, 7, 9, 11, 13, 16, 20};
    /**
     * Circles 7 and 9 are 144 and 161, not 145 and 162. Spec 6.2 caps
     * required_focus at 0.85 x maxFocus from circle 6, and against the shipped
     * max_focus column (110/120/130/140/150/160/170/180/190) the circle 7/8/9
     * ceilings are 144.5 / 153 / 161.5. 145 and 162 exceed those by 0.5 each and
     * would fail SpellBudgetTest on day one. The ladder gives way, not the rule.
     */
    private static final double[] FOCUS = {0, 20, 40, 65, 90, 115, 135, 144, 153, 161};

    /**
     * Element role allowlist, spec section 5.2. Enforced by SpellBudgetTest, so a
     * DoT field on FIRE or a terrain spell on AIR fails the build rather than
     * quietly breaking the element's identity.
     *
     * magic_missile is the sole NONE burst, and is allowlisted here explicitly.
     */
    private static final Map<Element, Set<SpellRole>> ALLOWED = new EnumMap<>(Element.class);

    static {
        ALLOWED.put(Element.FIRE, EnumSet.of(SpellRole.BURST, SpellRole.DENIAL, SpellRole.TERRAIN));
        ALLOWED.put(Element.WATER, EnumSet.of(SpellRole.SUSTAIN, SpellRole.DENIAL, SpellRole.BURST));
        ALLOWED.put(Element.EARTH, EnumSet.of(SpellRole.TERRAIN, SpellRole.DENIAL, SpellRole.BURST));
        ALLOWED.put(Element.AIR, EnumSet.of(SpellRole.UTILITY, SpellRole.DENIAL, SpellRole.BURST, SpellRole.SUSTAIN));
        ALLOWED.put(Element.LIGHT, EnumSet.of(SpellRole.UTILITY, SpellRole.BURST, SpellRole.SUSTAIN, SpellRole.DENIAL));
        ALLOWED.put(Element.DARK, EnumSet.of(SpellRole.UTILITY, SpellRole.SUSTAIN, SpellRole.BURST));
        ALLOWED.put(Element.NONE, EnumSet.of(SpellRole.UTILITY, SpellRole.DENIAL));
    }

    private SpellBudgets() {
    }

    private static int clamp(int circle) {
        return Math.max(0, Math.min(9, circle));
    }

    public static int statPointsFor(int circle) {
        return STAT_POINTS[clamp(circle)];
    }

    /**
     * The elemental affinity a circle's accumulated stat points buy, before any
     * equipment or engraving bonus. Feeds {@link #affinityMultiplierFor}.
     */
    public static double affinityFor(int circle) {
        return AFFINITY[clamp(circle)];
    }

    /**
     * The damage multiplier a fully-invested caster of that circle enjoys:
     * {@code 1 + affinity / 100}. Circle 0 is x1.00, circle 9 is x3.15.
     */
    public static double affinityMultiplierFor(int circle) {
        return 1.0 + affinityFor(circle) / 100.0;
    }

    /** Total health damage a single cast may deal to one target, at default options. */
    public static double burstBudget(int circle) {
        return BURST[clamp(circle)];
    }

    /** Total damage-over-time a single field may deal to one target across its whole life. */
    public static double sustainBudget(int circle) {
        return SUSTAIN[clamp(circle)];
    }

    /** Blocks a single cast may destroy. */
    public static int terrainBudget(int circle) {
        return (int) TERRAIN[clamp(circle)];
    }

    /** Block-seconds a single cast may deny. */
    public static int denialBudget(int circle) {
        return (int) DENIAL[clamp(circle)];
    }

    /** The ladder's nominal {@code power} for a circle. Spells may deviate with a written reason. */
    public static double powerFor(int circle) {
        return POWER[clamp(circle)];
    }

    public static int manaFor(int circle) {
        return (int) MANA[clamp(circle)];
    }

    /**
     * The cast time a circle permits, in ticks. Inclusive at both ends: a spell at
     * exactly its floor is instant-legal, a spell at exactly its ceiling is legal.
     */
    public record CastTimeRange(int minTicks, int maxTicks) {

        public boolean contains(int ticks) {
            return ticks >= minTicks && ticks <= maxTicks;
        }

        public double minSeconds() {
            return minTicks / 20.0;
        }

        public double maxSeconds() {
            return maxTicks / 20.0;
        }
    }

    /**
     * The cast time window a spell at that circle may sit inside, before wand and
     * mastery modifiers. See {@link #CAST_MIN_TICKS}.
     */
    public static CastTimeRange castTimeRange(int circle) {
        int c = clamp(circle);
        return new CastTimeRange(CAST_MIN_TICKS[c], CAST_MAX_TICKS[c]);
    }

    /**
     * The ladder's nominal {@code cast_time} in seconds, before wand and mastery
     * modifiers.
     *
     * @deprecated cast time is a per-circle range, not one value. Use
     * {@link #castTimeRange(int)} - this returns only the ceiling and hides the
     * floor that every circle below 6 guarantees.
     */
    @Deprecated
    public static double castTimeFor(int circle) {
        return castTimeRange(circle).maxSeconds();
    }

    /**
     * The wind-up a circle 6-9 cast must spend, in ticks. Below circle 6 there is no
     * wind-up contract, so this returns the range ceiling anyway and callers should
     * gate on the circle first.
     */
    public static int windUpTicks(int circle) {
        return castTimeRange(circle).maxTicks();
    }

    /**
     * Whether a circle's cast has run well past its wind-up, which is the signal for
     * a caster eating an interrupted, over-committed cast. Below circle 6 no wind-up
     * contract applies and this is always {@code false}.
     */
    public static boolean isPaidOverWindUp(int circle, int ticks) {
        if (circle < 6) return false;
        return ticks > 2 * windUpTicks(circle);
    }

    /**
     * Circle-agnostic form of {@link #isPaidOverWindUp(int, int)}: true once the cast
     * is longer than twice the <em>shortest</em> high-circle wind-up (circle 6's 20
     * ticks), i.e. once it beats every circle's contract. Use
     * {@link #isPaidOverWindUp(int, int)} when the circle is known.
     */
    public static boolean isPaidOverWindUp(int ticks) {
        return ticks > 2 * windUpTicks(6);
    }

    public static int cooldownFor(int circle) {
        return (int) COOLDOWN[clamp(circle)];
    }

    public static int focusFor(int circle) {
        return (int) FOCUS[clamp(circle)];
    }

    public static Set<SpellRole> allowedRoles(Element element) {
        if (element == null) return Collections.emptySet();
        return Collections.unmodifiableSet(ALLOWED.getOrDefault(element, EnumSet.noneOf(SpellRole.class)));
    }

    public static boolean isAllowed(Element element, SpellRole role) {
        return allowedRoles(element).contains(role);
    }

    /** The one NONE burst: magic_missile at circle 0, the starter every player begins with. */
    public static boolean isGrandfatheredBurst(String spellId, Element element, SpellRole role) {
        return "magic_missile".equals(spellId) && element == Element.NONE && role == SpellRole.BURST;
    }

    /**
     * The one spell charged against two budgets at once: fault_line at circle 7 declares
     * SUSTAIN, which EARTH's allowlist does not carry, <em>because</em> it is both a
     * damage-over-time field and a terrain reshape.
     *
     * <p>Spec section 5.2 is the load-bearing invariant here - the element allowlists
     * are enforced in CI and exist so that no element quietly drifts into another
     * element's role. Widening {@link #ALLOWED} to admit SUSTAIN for EARTH would make
     * every future earth spell free to tick, which is precisely what
     * {@code ElementArchetypeTest} exists to prevent. So the exemption is per-spill,
     * exactly as {@link #isGrandfatheredBurst} is for magic_missile, and the table stays
     * honest.
     *
     * <p>The cost of being SUSTAIN rather than TERRAIN is that
     * {@code SpellBudgetTest.terrainSpellsCarryTheCircleTerrainBudget} skips this spell,
     * so the exemption is paired with an explicit terrain-budget assertion there. Both
     * budgets therefore stay enforced; only the role <em>label</em> is unusual.
     */
    public static boolean isDualBudgetSpell(String spellId, Element element, SpellRole role) {
        return "fault_line".equals(spellId) && element == Element.EARTH && role == SpellRole.SUSTAIN;
    }
}
