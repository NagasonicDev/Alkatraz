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
 * blaze could hand over {@code inferno_rite}, a 600-mana circle-9 capstone.
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
     * WATER after wave 5: exactly one spell at every circle 1-9.
     * {@code undertow} is the circle-6 addition and {@code tide_of_the_deep} the
     * circle-9 capstone.
     */
    private static final Map<String, Integer> WATER = roster(
            "water_sphere", 1,
            "water_pulse", 2,
            "geyser", 3,
            "whirlpool", 4,
            "tsunami", 5,
            "undertow", 6,
            "everdrain", 7,
            "thousand_waves", 8,
            "tide_of_the_deep", 9);

    /** FIRE after wave 5: exactly one spell at every circle 1-9 ({@code meteor_shower} promoted 5 to 6). */
    private static final Map<String, Integer> FIRE = roster(
            "fireball", 1,
            "fire_blast", 2,
            "fire_wall", 3,
            "flaming_volley", 4,
            "flame_lance", 5,
            "meteor_shower", 6,
            "inferno_pilgrimage", 7,
            "sunfire_barrage", 8,
            "inferno_rite", 9);

    /** AIR after wave 5: ONE spell at circle 1 and THREE at circle 2. */
    private static final Map<String, Integer> AIR = roster(
            "air_burst", 1,
            "air_blades", 2,
            "swift", 2,
            "wind_vortex", 2,
            "updraft", 3,
            "wind_barrier", 4,
            "tornado", 5,
            "thunderclap", 6,
            "cyclone_nest", 7,
            "stormcaller", 8,
            "skywrath", 9);

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
            for (int cap = 1; cap <= 9; cap++) {
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
    void capThreeRemovesTheFireCapstoneFromABlaze() {
        Map<String, Double> book = bookCappedAt(FIRE, 3);
        assertFalse(book.containsKey("inferno_rite"),
                "circle 9 must be unreachable from a blaze");
        assertFalse(book.containsKey("meteor_shower"), "circle 6 must be unreachable from a blaze");
        assertFalse(book.containsKey("flame_lance"), "circle 5 must be unreachable from a blaze");

        assertEquals(3, book.size(), "a cap-3 FIRE book holds exactly fireball, fire_blast and fire_wall");
        assertEquals(10000.0, book.get("fireball"), 1e-12, "fireball keeps the whole circle-1 weight");
        assertEquals(5000.0, book.get("fire_blast"), 1e-12, "fire_blast keeps the whole circle-2 weight");
        assertEquals(2500.0, book.get("fire_wall"), 1e-12, "fire_wall keeps the whole circle-3 weight");
        assertEquals(17500.0, totalOf(book), 1e-9, "10000 + 5000 + 2500, one spell per circle so nothing is split");
        assertEquals(2500.0 / 17500.0, book.get("fire_wall") / totalOf(book), 1e-12,
                "circle 3 is 2500/17500 = 14.2857% of a cap-3 FIRE book");
    }

    @Test
    void capSevenOnTheGuardianReachesEverdrainButNotTheCircleNineCapstone() {
        Map<String, Double> book = bookCappedAt(WATER, 7);
        assertTrue(book.containsKey("everdrain"), "circle 7 is reachable at cap 7");
        assertFalse(book.containsKey("tide_of_the_deep"), "circle 9 is not");
        assertFalse(book.containsKey("thousand_waves"), "circle 8 is not");

        double total = totalOf(book);
        assertEquals(20025.0, total, 1e-9,
                "10000+5000+2500+1250+750+375+150, one WATER spell per circle so nothing is split");
        assertEquals(150.0 / 20025.0, book.get("everdrain") / total, 1e-12,
                "circle 7 is 150/20025 = 0.7491% of a cap-7 WATER book");
        assertEquals(375.0 / 20025.0, book.get("undertow") / total, 1e-12,
                "circle 6 is 375/20025 = 1.8727%, and circle 6 is where the elder guardian's own "
                        + "undertow specific drop sits, so the element book cannot outrank it");
    }

    @Test
    void uniformColumnsAreUnchangedByTheCapAtNine() {
        Map<String, Double> book = bookCappedAt(FIRE, 9);
        assertEquals(9, book.size(), "FIRE holds one spell at each circle 1-9 after wave 5");
        assertEquals(30.0, book.get("inferno_rite"), 1e-12,
                "with one spell per circle the per-spell weight IS the circle weight, so a cap-9 book "
                        + "is bit-identical to the old behaviour");
    }

    @Test
    void airCircleTwoBucketOfThreeNoLongerDominatesTheBook() {
        Map<String, Double> book = bookCappedAt(AIR, 9);
        double total = totalOf(book);
        double circle2 = book.get("air_blades") + book.get("swift") + book.get("wind_vortex");

        assertEquals(20130.0, total, 1e-9,
                "10000+5000+2500+1250+750+375+150+75+30: the ladder, unchanged by AIR's busy circle 2");
        assertEquals(5000.0 / 20130.0, circle2 / total, 1e-12,
                "circle 2 holds three AIR spells, so under the OLD per-spell weighting it took "
                        + "15000 of a 30130 budget = 49.784% of an AIR book - more likely than circle 1. "
                        + "After the fix it is 5000/20130 = 24.838%.");
        assertEquals(10000.0 / 20130.0, book.get("air_burst") / total, 1e-12,
                "circle 1 is 10000/20130 = 49.677%, the most common AIR circle again");
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
