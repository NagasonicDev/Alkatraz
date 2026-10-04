package me.nagasonic.alkatraz.spells.spellbooks;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The load-bearing test of this wave.
 *
 * <p>{@code SpellbookFactory} applied {@code spellbook.circle_weights.circle_N} to
 * EVERY spell at circle N, so a bucket totalled {@code count(N) x w(N)} and a
 * circle's share of a random book was a function of how many spells happened to
 * sit at that circle. With circle 2 holding eleven spells and circle 1 holding
 * five, a {@code (1,2)} book came out 52.4% circle 2 and 47.6% circle 1 - an
 * inverted ladder produced purely by roster size.
 *
 * <p>Every assertion here is arithmetic over plain data: no Bukkit, no
 * {@code Spell} construction, no registry. That is possible because the weighting
 * was extracted into {@link SpellbookFactory#circleWeightsFor}.
 */
class SpellbookWeightTest {

    /**
     * The shipped {@code spellbook.circle_weights} ladder. Exposed package-wise so
     * {@code ElementSpellBookCapTest} shares one source of truth. Mirrors
     * {@code SpellbookConfig.getCircleWeight}'s switch, whose default is 0.
     */
    static int shippedWeight(int circle) {
        int[] weights = {0, 10000, 5000, 2500, 1250, 750, 375, 150, 75, 30};
        return circle < 1 || circle > 9 ? 0 : weights[circle];
    }

    /**
     * A bucket map built from {@code (circle, spellCount)} pairs. Each pair gets its
     * own count, so a test can state "circle 1 holds five, circle 2 holds eleven"
     * instead of accidentally reusing one number for both.
     */
    private static Map<Integer, List<String>> bucketsOf(int... circlesAndCounts) {
        Map<Integer, List<String>> buckets = new LinkedHashMap<>();
        for (int i = 0; i < circlesAndCounts.length; i += 2) {
            int circle = circlesAndCounts[i];
            int spellCount = circlesAndCounts[i + 1];
            List<String> ids = new ArrayList<>();
            for (int s = 0; s < spellCount; s++) {
                ids.add("c" + circle + "_" + s);
            }
            buckets.put(circle, ids);
        }
        return buckets;
    }

    private static double totalOf(Map<String, Double> weights) {
        double total = 0;
        for (double weight : weights.values()) {
            total += weight;
        }
        return total;
    }

    private static double shareOfCircle(Map<String, Double> weights, int circle) {
        double inCircle = 0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (entry.getKey().startsWith("c" + circle + "_")) {
                inCircle += entry.getValue();
            }
        }
        return inCircle / totalOf(weights);
    }

    @Test
    void circleShareIsInvariantToBucketSize() {
        // Circle 9 against circle 1, with circle 9 holding ONE spell in one book and
        // SEVEN in the other, and circle 1 holding one spell in both.
        Map<String, Double> thin = SpellbookFactory.circleWeightsFor(
                bucketsOf(9, 1, 1, 1), SpellbookWeightTest::shippedWeight);
        Map<String, Double> fat = SpellbookFactory.circleWeightsFor(
                bucketsOf(9, 7, 1, 1), SpellbookWeightTest::shippedWeight);

        assertEquals(shareOfCircle(thin, 9), shareOfCircle(fat, 9), 1e-12,
                "a 1-spell circle-9 bucket and a 7-spell circle-9 bucket must yield the SAME share of "
                        + "their book, because the share may depend only on w(9) / (w(1) + w(9))");

        // And the value itself, so a change to the ladder shows up here rather than
        // only as an equality that happens to still hold.
        assertEquals(30.0 / 10030.0, shareOfCircle(thin, 9), 1e-12,
                "circle 9's share is 30 / (10000 + 30) = 0.2991%");
        assertEquals(10000.0 / 10030.0, shareOfCircle(thin, 1), 1e-12,
                "circle 1's share is 10000 / 10030 = 99.7009%");
    }

    @Test
    void perSpellWeightingWouldHaveInvertedTheLadder() {
        // The OLD arithmetic, reproduced: bucket total = count(c) x w(c).
        // Circle 1 holds 5 spells and circle 2 holds 11.
        double oldCircle1 = 5.0 * 10000 / (5.0 * 10000 + 11.0 * 5000);
        assertEquals(0.476190, oldCircle1, 1e-6,
                "the old arithmetic really did make circle 2 (52.38%) more likely than circle 1 (47.62%), "
                        + "which is the inversion this task removes");

        Map<String, Double> fixed = SpellbookFactory.circleWeightsFor(
                bucketsOf(2, 11, 1, 5), SpellbookWeightTest::shippedWeight);
        assertEquals(16, fixed.size(), "a (1,2) book carries all 5 circle-1 and 11 circle-2 spells");
        assertEquals(10000.0 / 15000.0, shareOfCircle(fixed, 1), 1e-12,
                "after the fix a (1,2) book is 10000/15000 = 66.67% circle 1");
        assertEquals(5000.0 / 15000.0, shareOfCircle(fixed, 2), 1e-12,
                "and 5000/15000 = 33.33% circle 2, which is the monotonic ladder the config comment claims");
    }

    @Test
    void everyBucketTotalsExactlyItsConfiguredCircleWeight() {
        Map<Integer, List<String>> buckets = new LinkedHashMap<>();
        buckets.put(6, List.of("a", "b", "c", "d", "e", "f", "g"));
        buckets.put(9, List.of("h", "i", "j", "k", "l", "m", "n"));
        Map<String, Double> weights = SpellbookFactory.circleWeightsFor(buckets, SpellbookWeightTest::shippedWeight);

        for (Map.Entry<Integer, List<String>> entry : buckets.entrySet()) {
            int circle = entry.getKey();
            double sum = 0;
            for (String id : entry.getValue()) {
                sum += weights.get(id);
            }
            assertEquals(shippedWeight(circle), sum, 1e-9,
                    "circle " + circle + "'s bucket must total exactly w(" + circle + ") = "
                            + shippedWeight(circle) + ", which is what makes the share invariant");
        }
        assertEquals(375.0 / 7.0, weights.get("a"), 1e-12,
                "each of circle 6's seven spells carries 375/7 = 53.5714...");
        assertEquals(30.0 / 7.0, weights.get("h"), 1e-12,
                "each of circle 9's seven spells carries 30/7 = 4.2857...");
    }

    @Test
    void uniformBucketsAreANoOpSoTheExistingLadderNeedsNoRetuning() {
        // Every band where all circles hold the same count is mathematically
        // unchanged by the fix. This is why config.yml needs NO edit: the ladder
        // was always right, it was just being applied per spell instead of per circle.
        Map<String, Double> weights = SpellbookFactory.circleWeightsFor(
                bucketsOf(6, 7, 7, 7), SpellbookWeightTest::shippedWeight);

        double circle6 = 0;
        double circle7 = 0;
        for (int s = 0; s < 7; s++) {
            circle6 += weights.get("c6_" + s);
            circle7 += weights.get("c7_" + s);
        }
        assertEquals(375.0, circle6, 1e-9,
                "a uniform circle-6 bucket of 7 still totals w(6) = 375 as a whole bucket");
        assertEquals(150.0, circle7, 1e-9,
                "a uniform circle-7 bucket of 7 still totals w(7) = 150 as a whole bucket");
        assertEquals(375.0 / 7.0, weights.get("c6_0"), 1e-12,
                "the 375 is split across seven spells, 53.5714... each");
        assertEquals(150.0 / 7.0, weights.get("c7_0"), 1e-12,
                "the 150 is split across seven spells, 21.4285... each");
        assertEquals(375.0 / 525.0, shareOfCircle(weights, 6), 1e-12,
                "the end city's (6,7,8,9) band reads 375/525 = 71.4286% before circle 8 is added");
    }

    @Test
    void zeroWeightCirclesAreSkippedRatherThanThrowing() {
        // SpellbookConfig.getCircleWeight returns 0 outside 1-9 (default -> 0) and
        // RandomSpellbook.addSpell used to throw IllegalArgumentException on weight <= 0.
        // magic_missile is circle 0, so this combination killed plugin enable.
        Map<Integer, List<String>> buckets = new LinkedHashMap<>();
        buckets.put(0, List.of("magic_missile"));
        buckets.put(1, List.of("fireball"));

        Map<String, Double> weights = SpellbookFactory.circleWeightsFor(buckets, SpellbookWeightTest::shippedWeight);

        assertFalse(weights.containsKey("magic_missile"),
                "a circle with weight 0 contributes no spell, rather than throwing out of registerAll()");
        assertEquals(10000.0, weights.get("fireball"), 1e-12, "the circle-1 spell is unaffected");
    }

    @Test
    void bucketByCircleKeepsOnlyRequestedCirclesAndSortsDeterministically() {
        Map<String, Integer> roster = new LinkedHashMap<>();
        roster.put("zulu", 1);
        roster.put("alpha", 2);
        roster.put("mike", 1);
        roster.put("not_requested", 9);

        Map<Integer, List<String>> buckets = SpellbookFactory.bucketByCircle(roster, 1, 2);

        assertEquals(List.of("mike", "zulu"), buckets.get(1),
                "bucket contents are sorted so two builds of the same book are byte-identical, which is "
                        + "what lets RandomSpellbook's spell_<i>/weight_<i> NBT indices be compared");
        assertEquals(List.of("alpha"), buckets.get(2), "a requested circle holds only its own spells");
        assertTrue(buckets.get(1).stream().noneMatch("not_requested"::equals),
                "circle 9 was not requested, so its spell is left out entirely");
    }

    @Test
    void deadCircleRangeFallsBackInsteadOfBuildingAnEmptyBook() {
        // What ender_dragon and wither shipped before this wave:
        // createRandomSpellBook(7,8,9) against a roster with nothing at circles 7-9.
        Map<String, Integer> preWave5 = Map.of(
                "earth_throw", 1,
                "fireball", 1,
                "wave", 2,
                "geyser", 3,
                "blink", 4,
                "tsunami", 5,
                "skeletal_knights", 6,
                "warp", 6
        );

        int[] fallback = SpellbookFactory.fallbackCircles(preWave5, 7, 8, 9);

        assertEquals(1, fallback.length, "the fallback collapses to a single circle");
        assertEquals(6, fallback[0],
                "the scan runs DOWN from the highest requested circle, so (7,8,9) degrades to circle 6 - "
                        + "a usable book - rather than to a spellbook_type=random, spell_count=0 item that "
                        + "prints spellbook.empty on right-click and is never consumed");
    }

    @Test
    void fallbackScansDownwardAndSettlesOnCircleOne() {
        Map<String, Integer> onlyLowCircles = Map.of("fireball", 1, "wave", 2);
        assertEquals(2, SpellbookFactory.fallbackCircles(onlyLowCircles, 6, 7, 8, 9)[0],
                "the highest populated circle at or below the request wins");
        assertEquals(2, SpellbookFactory.fallbackCircles(onlyLowCircles, 4, 5)[0],
                "with nothing between the ceiling and 2, circle 2 is used");

        Map<String, Integer> nothingButCircleOne = Map.of("fireball", 1);
        assertEquals(1, SpellbookFactory.fallbackCircles(nothingButCircleOne, 7, 8, 9)[0],
                "circle 1 is the documented final fallback");
    }

    @Test
    void fishingBandInvertsCircleTwoUnderTheOldArithmetic() {
        // The worst case in the shipped config: fishing is (1,2,3,4,5,6) and the
        // wave-5 roster's counts there are 5/11/8/7/7/7 - circle 2 holds eleven
        // spells and circle 1 holds five, circle 3 holds eight (updraft, and dispel
        // does not arrive until wave 9), circles 4-6 hold seven each.
        int[] circles = {1, 2, 3, 4, 5, 6};
        double[] counts = {5, 11, 8, 7, 7, 7};
        double oldTotal = 0;
        double newTotal = 0;
        double oldCircle1 = 0;
        double oldCircle2 = 0;
        for (int i = 0; i < circles.length; i++) {
            oldTotal += counts[i] * shippedWeight(circles[i]);
            newTotal += shippedWeight(circles[i]);
            if (circles[i] == 1) oldCircle1 += counts[i] * shippedWeight(circles[i]);
            if (circles[i] == 2) oldCircle2 += counts[i] * shippedWeight(circles[i]);
        }

        // 5*10000 + 11*5000 + 8*2500 + 7*1250 + 7*750 + 7*375 = 141625
        assertEquals(141625.0, oldTotal, 1e-9,
                "the old bucket total is the sum of count(c) x w(c), which is what made the share a "
                        + "function of roster size");
        assertEquals(50000.0 / 141625.0, oldCircle1 / oldTotal, 1e-12,
                "today a fishing book is 50000/141625 = 35.3045% circle 1");
        assertEquals(55000.0 / 141625.0, oldCircle2 / oldTotal, 1e-12,
                "and 55000/141625 = 38.8350% circle 2 - the most common fishing outcome is a circle-2 spell, "
                        + "because circle 2 holds eleven spells and circle 1 holds five");

        Map<Integer, List<String>> buckets = new LinkedHashMap<>();
        for (int i = 0; i < circles.length; i++) {
            List<String> ids = new ArrayList<>();
            for (int s = 0; s < counts[i]; s++) {
                ids.add("c" + circles[i] + "_" + s);
            }
            buckets.put(circles[i], ids);
        }
        Map<String, Double> weights = SpellbookFactory.circleWeightsFor(buckets, SpellbookWeightTest::shippedWeight);

        assertEquals(19875.0, newTotal, 1e-9,
                "the fixed bucket total depends only on the ladder: 10000+5000+2500+1250+750+375");
        assertEquals(10000.0 / newTotal, shareOfCircle(weights, 1), 1e-12,
                "after the fix circle 1 is 10000/19875 = 50.3145% of a fishing book, which is monotonic again");
        assertEquals(5000.0 / newTotal, shareOfCircle(weights, 2), 1e-12,
                "and circle 2 is 5000/19875 = 25.1572%");
        assertTrue(shareOfCircle(weights, 1) > shareOfCircle(weights, 2),
                "the ladder must be strictly decreasing in circle");
    }
}
