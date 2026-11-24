package me.nagasonic.alkatraz.spells.spellbooks;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A random ELEMENT spellbook has to be circle-bounded.
 *
 * <p>{@code createRandomElementSpellBook(Element)} took any spell of that element
 * at any circle, and there are 25 call sites. Adding circle-7/8/9 spells silently
 * injected them into nether bridge, bastion, desert pyramid, ocean ruins,
 * stronghold, woodland mansion, fishing, piglin bartering and eight mob types - so a
 * blaze could hand over a 600-mana circle-9 capstone.
 *
 * <p>Everything here is arithmetic over plain data via the pure
 * {@code SpellbookFactory.bucketByCircle} / {@code circleWeightsFor} pair, so no
 * registry, no {@code Spell} objects and no Bukkit are involved.
 *
 * <p>Each column below is transcribed from the {@code level:} key of
 * {@code core/src/main/resources/spells/<id>.yml} on the 67-spell wave-5 roster.
 */
class ElementSpellBookCapTest {

    /**
     * WATER's surviving ladder: one spell at each of circles 1-4.
     */
    private static final Map<String, Integer> WATER = roster(
            "water_sphere", 1,
            "water_pulse", 2,
            "geyser", 3,
            "whirlpool", 4);

    /** FIRE's surviving ladder: one spell at each of circles 1-4. */
    private static final Map<String, Integer> FIRE = roster(
            "fireball", 1,
            "fire_blast", 2,
            "fire_wall", 3,
            "flaming_volley", 4);

    /** AIR: ONE spell at circle 1, THREE at circle 2, then one each at 3 and 4. */
    private static final Map<String, Integer> AIR = roster(
            "air_burst", 1,
            "air_blades", 2,
            "swift", 2,
            "wind_vortex", 2,
            "updraft", 3,
            "wind_barrier", 4);

    private static Map<String, Integer> roster(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }

    private static Map<String, Double> bookCappedAt(Map<String, Integer> column, int maxCircle) {
        int[] circles = new int[maxCircle];
        for (int c = 1; c <= maxCircle; c++) {
            circles[c - 1] = c;
        }
        Map<Integer, List<String>> buckets = SpellbookFactory.bucketByCircle(column, circles);
        return SpellbookFactory.circleWeightsFor(buckets, SpellbookWeightTest::shippedWeight);
    }

    private static double totalOf(Map<String, Double> weights) {
        double total = 0;
        for (double weight : weights.values()) {
            total += weight;
        }
        return total;
    }

    @Test
    void cappedElementBookNeverContainsASpellAboveTheCap() {
        for (Map<String, Integer> column : List.of(FIRE, WATER, AIR)) {
            int maxCircle = column.values().stream().mapToInt(Integer::intValue).max().orElse(9);
            for (int cap = 1; cap <= maxCircle; cap++) {
                Map<String, Double> weights = bookCappedAt(column, cap);
                assertFalse(weights.isEmpty(),
                        "cap " + cap + " must still produce a book - a cap that empties the book is a "
                                + "regression, because an empty random book prints spellbook.empty on "
                                + "right-click and is never consumed");
                for (String spellId : weights.keySet()) {
                    int circle = column.get(spellId);
                    assertTrue(circle <= cap,
                            "cap " + cap + " leaked " + spellId + " at circle " + circle
                                    + ". Every element book call site in SpellbookLoot needs a cap, or a "
                                    + "mob hands out a 600-mana circle-9 capstone.");
                }
            }
        }
    }

    @Test
    void capThreeRemovesEverythingAboveCircleThreeFromABlaze() {
        Map<String, Double> book = bookCappedAt(FIRE, 3);
        assertFalse(book.containsKey("flaming_volley"),
                "circle 4 must be unreachable from a blaze");

        assertEquals(3, book.size(), "a cap-3 FIRE book holds exactly fireball, fire_blast and fire_wall");
        assertEquals(10000.0, book.get("fireball"), 1e-12, "fireball keeps the whole circle-1 weight");
        assertEquals(5000.0, book.get("fire_blast"), 1e-12, "fire_blast keeps the whole circle-2 weight");
        assertEquals(2500.0, book.get("fire_wall"), 1e-12, "fire_wall keeps the whole circle-3 weight");
        assertEquals(17500.0, totalOf(book), 1e-9, "10000 + 5000 + 2500, one spell per circle so nothing is split");
        assertEquals(2500.0 / 17500.0, book.get("fire_wall") / totalOf(book), 1e-12,
                "circle 3 is 2500/17500 = 14.2857% of a cap-3 FIRE book");
    }

    @Test
    void capThreeOnTheGuardianReachesGeyserButNotWhirlpool() {
        Map<String, Double> book = bookCappedAt(WATER, 3);
        assertTrue(book.containsKey("geyser"), "circle 3 is reachable at cap 3");
        assertFalse(book.containsKey("whirlpool"), "circle 4 is not");

        double total = totalOf(book);
        assertEquals(17500.0, total, 1e-9,
                "10000+5000+2500, one WATER spell per circle so nothing is split");
        assertEquals(5000.0 / 17500.0, book.get("water_pulse") / total, 1e-12,
                "circle 2 is 5000/17500 = 28.5714% of a cap-3 WATER book");
    }

    @Test
    void uniformColumnsAreUnchangedByTheCapAtTheRosterTop() {
        Map<String, Double> book = bookCappedAt(FIRE, 4);
        assertEquals(4, book.size(), "FIRE holds one spell at each of circles 1-4");
        assertEquals(1250.0, book.get("flaming_volley"), 1e-12,
                "with one spell per circle the per-spell weight IS the circle weight, so capping at the "
                        + "roster's top circle is bit-identical to the old behaviour");
    }

    @Test
    void airCircleTwoBucketOfThreeNoLongerDominatesTheBook() {
        Map<String, Double> book = bookCappedAt(AIR, 9);
        double total = totalOf(book);
        double circle2 = book.get("air_blades") + book.get("swift") + book.get("wind_vortex");

        assertEquals(18750.0, total, 1e-9,
                "10000+5000+2500+1250: the ladder, unchanged by AIR's busy circle 2");
        assertEquals(5000.0 / 18750.0, circle2 / total, 1e-12,
                "circle 2 holds three AIR spells, so under the OLD per-spell weighting it took "
                        + "15000 of a 28750 budget = 52.174% of an AIR book - more likely than circle 1. "
                        + "After the fix it is 5000/18750 = 26.667%.");
        assertEquals(10000.0 / 18750.0, book.get("air_burst") / total, 1e-12,
                "circle 1 is 10000/18750 = 53.333%, the most common AIR circle again");
        assertEquals(5000.0 / 3.0, book.get("air_blades"), 1e-12,
                "each of circle 2's three spells carries 5000/3 = 1666.666...");
    }

    @Test
    void aCapOfTwoOnAirIsNoLongerAllCircleTwo() {
        Map<String, Double> book = bookCappedAt(AIR, 2);
        double total = totalOf(book);
        double circle2 = book.get("air_blades") + book.get("swift") + book.get("wind_vortex");

        // Under the old arithmetic, three circle-2 spells at 5000 each consumed the
        // whole 10000+15000 budget on their own relative to circle 1's single 10000.
        assertEquals(15000.0, total, 1e-9, "10000 + 5000: the ladder, with circle 2's 5000 split three ways");
        assertEquals(10000.0 / 15000.0, book.get("air_burst") / total, 1e-12,
                "circle 1 is 10000/15000 = 66.667% of a cap-2 AIR book");
        assertEquals(5000.0 / 15000.0, circle2 / total, 1e-12,
                "circle 2 as a bucket is 5000/15000 = 33.333%, split three ways. Under the old arithmetic "
                        + "each circle-2 spell carried the full 5000 and circle 2 took 15000/25000 = 60%.");
        assertEquals((5000.0 / 3.0) / 15000.0, book.get("air_blades") / total, 1e-12,
                "one of the three circle-2 spells is (5000/3)/15000 = 1/9 = 11.111%");
        assertEquals(1.0, book.get("air_blades") / book.get("wind_vortex"), 1e-12,
                "all three circle-2 spells are now equally likely, which is the point");
    }
}
