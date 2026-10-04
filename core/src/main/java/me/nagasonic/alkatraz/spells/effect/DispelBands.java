package me.nagasonic.alkatraz.spells.effect;

import java.util.UUID;

/**
 * The spec 4.5.3 contest, expressed as arithmetic with no Bukkit, no profile
 * and no server, so it is unit-testable on its own.
 *
 * <pre>
 * dispelBands = casterCircle + masteryBonus        masteryBonus in {0, 1}
 * effectBands = effect.sourceCircle                (stamped)
 * effectBands = 1 + potionAmplifier                (vanilla fallback)
 * success     = dispelBands >= effectBands        hard fail otherwise
 * </pre>
 *
 * <p><b>The rule in one sentence: a dispel reaches exactly as high as the
 * circle that cast it, plus one mastery band.</b> Mastery is the deciding factor
 * because it is the thing the player controls rather than the thing they were
 * assigned. Because the contest is {@code >=} and not {@code >}, the caster wins
 * ties: at zero mastery a circle-3 dispel (3 bands) already clears circles 1-3
 * and amplifier-0/1/2 potions, and the mastery band (4 bands) is what lifts it to
 * circles 1-4 and amplifier-0/2/3. It is <b>not</b> a gate on your own circle —
 * that is the one place where spec §4.5.3's prose gloss and its formula disagree,
 * and the formula wins; see {@code DispelBandsTest.everyTieSucceedsBecauseTheContestIsGreaterOrEqual}.
 *
 * <p><b>Duration is deliberately absent from the vanilla fallback.</b> Reading it
 * would mean a freshly-drunk speed potion is easier to dispel than the same
 * potion thirty seconds later, which reads as a bug. The exclusion is
 * structural — {@link #potionBands(int)} has no duration parameter, so the
 * compiler is what enforces it.
 *
 * <p><b>One exemption: self-cast.</b> A dispel whose target is the caster does
 * not contest anything; every effect they are carrying goes regardless of band.
 * A circle-1 player must be able to cleanse their own debuffs. The exemption
 * keys on the <em>target being the caster</em>, not on who applied the effect.
 */
public final class DispelBands {

    /** The largest mastery bonus the contract allows. Never more than one band. */
    public static final int MAX_MASTERY_BONUS = 1;

    private DispelBands() {
    }

    /**
     * The caster's bands.
     *
     * <p>The mastery bonus is clamped to {@code {0, 1}} rather than trusted, so
     * that a custom {@code dispel_options.yml} adding a second +1 tier cannot
     * silently make a dispel able to outrank its own circle's cap.
     *
     * @param casterCircle the caster's circle level
     * @param masteryBonus the bonus granted by the caster's mastery tier; clamped to {0, 1}
     * @return the number of bands the caster can contest with
     */
    public static double dispelBands(int casterCircle, int masteryBonus) {
        int bonus = Math.max(0, Math.min(MAX_MASTERY_BONUS, masteryBonus));
        return casterCircle + bonus;
    }

    /**
     * Effect bands for a stamped Alkatraz effect: the circle of the caster who
     * applied it.
     *
     * @param sourceCircle the circle stamped by {@code EffectApplicator}
     * @return the bands the effect contests at
     */
    public static int stampedBands(int sourceCircle) {
        return sourceCircle;
    }

    /**
     * Effect bands for a vanilla potion the registry never saw: one plus its
     * amplifier.
     *
     * <p>There is no duration parameter, by design. See the class javadoc.
     *
     * @param potionAmplifier the potion's amplifier, where 0 is level I
     * @return the bands the potion contests at
     */
    public static int potionBands(int potionAmplifier) {
        return 1 + potionAmplifier;
    }

    /**
     * @param casterId the caster's uuid
     * @param targetId the resolved target's uuid
     * @return true if the dispel landed on the caster, which contests nothing
     */
    public static boolean isSelfCast(UUID casterId, UUID targetId) {
        return casterId != null && casterId.equals(targetId);
    }

    /**
     * The contest itself. A self-cast never contests; otherwise the caster wins
     * on a tie.
     *
     * @param selfCast    true if the target is the caster
     * @param dispelBands the caster's bands, from {@link #dispelBands(int, int)}
     * @param effectBands the effect's bands, from {@link #stampedBands(int)} or {@link #potionBands(int)}
     * @return true if the effect is stripped
     */
    public static boolean wins(boolean selfCast, double dispelBands, int effectBands) {
        return selfCast || dispelBands >= effectBands;
    }
}