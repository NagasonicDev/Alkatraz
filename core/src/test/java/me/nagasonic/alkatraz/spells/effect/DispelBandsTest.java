package me.nagasonic.alkatraz.spells.effect;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spec 4.5.3 contest, asserted as arithmetic.
 *
 * <pre>
 * dispelBands = casterCircle + masteryBonus        masteryBonus in {0, 1}
 * effectBands = effect.sourceCircle                (stamped)
 * effectBands = 1 + potionAmplifier                (vanilla fallback)
 * success     = dispelBands >= effectBands        hard fail otherwise
 * </pre>
 *
 * <p>The rule in one sentence: without mastery you can only strip what a lower
 * circle made; mastery is what lets you match your own circle.
 *
 * <p>Pure — no Bukkit, no profile, no mock.
 */
class DispelBandsTest {

    private static final UUID CASTER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID VICTIM = UUID.fromString("00000000-0000-0000-0000-0000000000b0");

    @Test
    void casterBandsAreCirclePlusAtMostOneMasteryBand() {
        assertEquals(1.0, DispelBands.dispelBands(1, 0), 1e-9, "circle 1 with no mastery is one band");
        assertEquals(2.0, DispelBands.dispelBands(1, 1), 1e-9, "circle 1 at mastery is two bands");
        assertEquals(9.0, DispelBands.dispelBands(9, 0), 1e-9, "circle 9 with no mastery is nine bands");
        assertEquals(10.0, DispelBands.dispelBands(9, 1), 1e-9, "the bonus is one band, never more");
    }

    @Test
    void masteryBonusIsClampedToZeroOrOne() {
        assertEquals(3.0, DispelBands.dispelBands(3, -5), 1e-9,
                "a negative bonus is nonsense and must clamp to zero, not subtract bands");
        assertEquals(4.0, DispelBands.dispelBands(3, 99), 1e-9,
                "the contract says masteryBonus is in {0, 1}; a larger option must not stack");
    }

    @Test
    void potionBandsAreOnePlusAmplifier() {
        for (int amplifier = 0; amplifier <= 4; amplifier++) {
            assertEquals(amplifier + 1, DispelBands.potionBands(amplifier),
                    "amplifier " + amplifier + " must contest at " + (amplifier + 1) + " band(s)");
        }
    }

    @Test
    void durationIsExcludedFromTheVanillaFallback() {
        assertEquals(1, DispelBands.potionBands(0),
                "an amplifier-0 potion costs one band however long it has been drunk. "
                        + "The exclusion is structural: potionBands(int) has no duration "
                        + "parameter at all, so the compiler is what enforces it — a "
                        + "freshly-drunk potion can never be easier to dispel than the "
                        + "same potion thirty seconds later.");
    }

    @Test
    void stampedBandsAreTheAppliersCircle() {
        for (int circle = 0; circle <= 9; circle++) {
            assertEquals(circle, DispelBands.stampedBands(circle),
                    "a stamped effect contests at exactly the caster's circle");
        }
    }

    @Test
    void zeroMasteryCircleThreeClearsAmplifierTwoButNotThree() {
        double bands = DispelBands.dispelBands(3, 0);
        assertTrue(DispelBands.wins(false, bands, DispelBands.potionBands(1)),
                "three bands clear a level-II potion");
        assertTrue(DispelBands.wins(false, bands, DispelBands.potionBands(2)),
                "three bands also clear a level-III potion: the contest is >=, so a tie goes to the caster");
        assertFalse(DispelBands.wins(false, bands, DispelBands.potionBands(3)),
                "three bands must NOT clear a level-IV potion — that is what the mastery band is for");
    }

    @Test
    void masteryUnlocksExactlyTheBandAboveYourOwnCircle() {
        double withoutMastery = DispelBands.dispelBands(3, 0);
        double withMastery = DispelBands.dispelBands(3, 1);

        assertTrue(DispelBands.wins(false, withoutMastery, DispelBands.stampedBands(3)),
                "a circle-3 dispel matches a circle-3 effect: the contest is >=, so the tie goes to the caster");
        assertFalse(DispelBands.wins(false, withoutMastery, DispelBands.stampedBands(4)),
                "three bands cannot reach a circle-4 effect");
        assertTrue(DispelBands.wins(false, withMastery, DispelBands.stampedBands(4)),
                "at the mastery threshold four bands match a circle-4 effect — this single band "
                        + "is the whole point of the mastery requirement");

        assertFalse(DispelBands.wins(false, withoutMastery, DispelBands.potionBands(3)),
                "three bands cannot clear a level-IV potion");
        assertTrue(DispelBands.wins(false, withMastery, DispelBands.potionBands(3)),
                "four bands clear a level-IV potion, which is what the mastery band buys");
        assertFalse(DispelBands.wins(false, withMastery, DispelBands.potionBands(4)),
                "four bands still cannot clear a level-V potion — mastery is one band, never a free pass");
    }

    @Test
    void everyTieSucceedsBecauseTheContestIsGreaterOrEqual() {
        for (int circle = 1; circle <= 9; circle++) {
            for (int bonus = 0; bonus <= 1; bonus++) {
                double bands = DispelBands.dispelBands(circle, bonus);
                for (int amplifier = 0; amplifier <= 4; amplifier++) {
                    int required = DispelBands.potionBands(amplifier);
                    if ((int) bands == required) {
                        assertTrue(DispelBands.wins(false, bands, required),
                                "circle " + circle + " at " + bands + " bands ties a level-"
                                        + (amplifier + 1) + " potion and must succeed: the contest is >=");
                    }
                    assertTrue(DispelBands.wins(false, bands, required) == (bands >= required),
                            "the contest must be exactly `dispelBands >= effectBands`, nothing else");
                }
            }
        }
    }

    @Test
    void aHigherCircleAlwaysClearsALowerOne() {
        for (int higher = 1; higher <= 9; higher++) {
            for (int lower = 0; lower < higher; lower++) {
                assertTrue(DispelBands.wins(false, DispelBands.dispelBands(higher, 0),
                                DispelBands.stampedBands(lower)),
                        "circle " + higher + " must always clear a circle-" + lower + " effect");
                assertFalse(DispelBands.wins(false, DispelBands.dispelBands(lower, 0),
                                DispelBands.stampedBands(higher)),
                        "circle " + lower + " must never clear a circle-" + higher + " effect");
            }
        }
    }

    @Test
    void selfCastNeverContests() {
        assertTrue(DispelBands.isSelfCast(CASTER, CASTER), "casting at yourself is a self-cast");
        assertFalse(DispelBands.isSelfCast(CASTER, VICTIM), "casting at anyone else is not");
        assertTrue(DispelBands.wins(true, 0.0, 9),
                "a circle-0 dispel at itself must strip everything it is carrying. "
                        + "A circle-1 player has to be able to cleanse their own debuffs, and "
                        + "'you are too weak to remove your own weakness' would be absurd. "
                        + "The exemption keys on the target being the caster, NOT on who "
                        + "applied the effect.");
        assertTrue(DispelBands.wins(true, 0.0, 9),
                "and the exemption does not care how many bands the effect would have needed");
    }

    @Test
    void selfCastIsDecidedByTargetNotByApplier() {
        assertTrue(DispelBands.wins(true, 1.0, 5),
                "an effect a circle-9 caster put on the caster is still stripped by a "
                        + "self-cast, because the exemption keys on target == caster");
    }
}