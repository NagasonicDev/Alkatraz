package me.nagasonic.alkatraz.items.magic.attribute;

import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * affinity = 10 * points^0.65. The exponent is what keeps `power` load-bearing:
 * a linear curve reaches x12.20 at circle 9, which would leave the spell yml
 * `power` values spanning only 2.7 to 7.4 - below today's earthsplitter value of 9.
 *
 * <p>Every assertion goes through {@link StatPointsAttributeSource#affinityFromPoints},
 * the shipped implementation, so this file cannot pass while the plugin disagrees.
 */
class AffinityCurveTest {

    private static final double PER_POINT = 10.0;
    private static final double EXPONENT = 0.65;

    private static double affinity(int points) {
        return StatPointsAttributeSource.affinityFromPoints(points, PER_POINT, EXPONENT);
    }

    @Test
    void zeroPointsIsZeroAffinity() {
        assertEquals(0.0, affinity(0), 1e-9, "no investment means no affinity");
    }

    @Test
    void matchesTheSpecLadder() {
        int[] points = {0, 5, 10, 17, 27, 37, 52, 67, 87, 112};
        double[] expected = {0, 28, 45, 63, 85, 105, 130, 154, 182, 215};
        for (int i = 0; i < points.length; i++) {
            assertEquals(expected[i], affinity(points[i]), 1.0,
                    "circle " + i + " (" + points[i] + " points) should land near " + expected[i]);
        }
    }

    @Test
    void multiplierSpansTheIntendedRange() {
        double low = 1.0 + affinity(5) / 100.0;    // circle 1
        double high = 1.0 + affinity(112) / 100.0; // circle 9
        assertEquals(1.28, low, 0.02, "circle 1 is a x1.28 multiplier (5 points, 10 x 5^0.65 = 28.1)");
        assertEquals(3.15, high, 0.02, "circle 9 is about a x3.15 multiplier");
    }

    @Test
    void curveIsStrictlyMonotonic() {
        double previous = -1.0;
        for (int points = 0; points <= 130; points++) {
            double value = affinity(points);
            assertTrue(value > previous, "affinity must strictly increase from " + points + " points");
            previous = value;
        }
    }

    @Test
    void returnsAreStrictlyDiminishing() {
        double firstGain = affinity(1) - affinity(0);
        double laterGain = affinity(101) - affinity(100);
        assertTrue(laterGain < firstGain,
                "the 101st point must be worth less than the 1st, or the curve is not diminishing");
    }

    @Test
    void exponentOfOneRestoresLinear() {
        assertEquals(1000.0, StatPointsAttributeSource.affinityFromPoints(100, PER_POINT, 1.0), 1e-9,
                "an exponent of 1.0 must reproduce the old linear behaviour as a documented escape hatch");
    }

    @Test
    void negativePointsClampToZeroRatherThanNaN() {
        assertEquals(0.0, affinity(-1), 1e-9, "a negative point count cannot mean negative affinity");
        assertEquals(0.0, affinity(-112), 1e-9, "a corrupted save cannot mean negative affinity");
        assertEquals(0.0, StatPointsAttributeSource.affinityFromPoints(0, PER_POINT, -0.65), 1e-9,
                "a negative exponent must not turn Math.pow(0, negative) into Infinity");
        assertEquals(0.0, StatPointsAttributeSource.resistanceFromPoints(0, PER_POINT, -0.65), 1e-9,
                "resistance shares the same guard");
    }

    /**
     * Spec section 6 and section 15: spot-check the ladder end to end.
     *
     * <p>A naked circle-0 Magic Missile deals 1.5 and a naked circle-9 capstone spends
     * the whole 90.0 BURST budget, so the naked spread is x60. Fully geared with max
     * options the same cast reaches roughly 129, an x86 spread, of which affinity
     * supplies x3.15 and gear roughly x1.43.
     *
     * <p>The geared figure is asserted as a BAND rather than a point, because the
     * residual few percent is each spell's own max-option damage term and is therefore
     * not a ladder constant.
     */
    @Test
    void theLadderSpreadHoldsEndToEnd() {
        double nakedZero = SpellBudgets.burstBudget(0);
        double nakedNine = SpellBudgets.burstBudget(9);
        assertEquals(1.5, nakedZero, 1e-9, "a naked circle-0 magic missile deals 1.5");
        assertEquals(90.0, nakedNine, 1e-9, "a naked circle-9 capstone spends the 90.0 BURST budget");
        assertEquals(60.0, nakedNine / nakedZero, 1e-9, "the naked spread is x60");

        double affinityNine = affinity(112);                    // 215 -> x3.15
        double gear = 1.43;                                     // astral wand 20 + grimoire 15
        // + 4 jewellery + 4pc set = 43
        double geared = SpellBudgets.powerFor(9) * (1 + affinityNine / 100.0) * gear;
        assertTrue(geared >= 120.0 && geared <= 145.0,
                "a fully geared circle-9 capstone with max options lands near the design's ~129; "
                        + "powerFor(9) x affinity x gear is " + geared);
        assertTrue(geared / nakedZero > 80.0,
                "which is an x80+ spread from the naked circle-0 cast, the design's ~x86 including "
                        + "the per-spell max-option term");
    }

    @Test
    void everyCirclesPowerSitsInsideItsOwnBurstBudget() {
        for (int circle = 0; circle <= 9; circle++) {
            assertTrue(SpellBudgets.powerFor(circle) <= SpellBudgets.burstBudget(circle),
                    "circle " + circle + " power " + SpellBudgets.powerFor(circle)
                            + " must sit inside its BURST budget of " + SpellBudgets.burstBudget(circle));
        }
    }

    @Test
    void manaIsSixPercentOfMaxManaFromCircleTwoUp() {
        // Spec section 6.1: max_mana is 200/400/800/1250/2000/2750/4000/7500/10000 for
        // circles ONE through NINE (circle 0 declares no max_mana at all), so 6% lands on
        // 12/24/48/75/120/165/240/450/600. The array therefore holds circles 1-9 and is
        // indexed at circle - 1. Circle 1 is rounded up to 15 to match the shipped
        // fireball and circle 0's 5 mana is the starter's own value, so the ladder only
        // has to hold from circle 2 up - hence both the loop bound and the offset.
        int[] maxMana = {200, 400, 800, 1250, 2000, 2750, 4000, 7500, 10000};
        for (int circle = 2; circle <= 9; circle++) {
            int shippedManaCap = maxMana[circle - 1];
            double sixPercent = shippedManaCap * 0.06;
            int shipped = SpellBudgets.manaFor(circle);
            assertTrue(Math.abs(shipped - sixPercent) <= 0.12 * sixPercent + 1.0,
                    "circle " + circle + " mana " + shipped + " is within 12% of 6% of "
                            + shippedManaCap + " (" + sixPercent + ")");
        }
    }

    @Test
    void castTimeCooldownAndFocusAllRiseWithCircle() {
        int[] cooldown = {2, 4, 5, 6, 7, 9, 11, 13, 16, 20};
        for (int circle = 1; circle <= 9; circle++) {
            assertEquals(cooldown[circle], SpellBudgets.cooldownFor(circle),
                    "circle " + circle + " cooldown");
            assertTrue(SpellBudgets.cooldownFor(circle) > SpellBudgets.cooldownFor(circle - 1),
                    "cooldown must rise at circle " + circle);
            assertTrue(SpellBudgets.focusFor(circle) > SpellBudgets.focusFor(circle - 1),
                    "focus must rise at circle " + circle);
            assertTrue(SpellBudgets.manaFor(circle) > SpellBudgets.manaFor(circle - 1),
                    "mana must rise at circle " + circle);
        }
    }
}
