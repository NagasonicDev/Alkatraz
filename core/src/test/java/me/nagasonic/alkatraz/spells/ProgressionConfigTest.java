package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec section 11: the {@code mastered_spell_count} progression requirements must stay
 * consistent with the roster they count.
 *
 * <p>The bug this exists for: the circles 6-9 requirements omit the {@code circle:}
 * key, so {@code RequirementFactory.masteredSpellCount} skips its filter and counts
 * the <em>entire</em> roster. Going 38 -> 68 spells with no config change would have
 * dropped circle 9 from 30/38 (79%) to 30/68 (44%) silently.
 *
 * <p>Two things make this survive the roster growing twice:
 * <ul>
 *   <li>The roster size is DISCOVERED from the bundled ymls, not hardcoded.</li>
 *   <li>The expected counts are looked up BY ROSTER SIZE, so the same test passes at
 *       the 38-spell milestone and at the 68-spell one without being edited. Wave 7's
 *       T27 does not need to touch this file.</li>
 * </ul>
 *
 * <p>{@code requirements} is a YAML <em>list of maps</em>, not a nested section, so it
 * is read with {@link YamlConfiguration#getMapList(String)} - the same accessor
 * {@code ProgressionService:190} and {@code RecipeLoader:256} use. Reading it as a
 * {@code ConfigurationSection} returns null for every circle.
 */
class ProgressionConfigTest {

    /**
     * Keyed by the SCOPED circle, valued by the count: circle N+1's requirement is
     * scoped to circle N's spells and asks for exactly N of them. Circles 2-5 are
     * scoped this way, so the 38-to-68 roster growth does not move them.
     */
    private static final Map<Integer, Integer> SCOPED_COUNTS = Map.of(1, 1, 2, 2, 3, 3, 4, 4);

    /**
     * The unscoped circles 6-9 counts, keyed by roster size. 38 is what shipped
     * before the circle 6-9 expansion; 67 and 68 are spec section 11's rescale
     * (21/28/35/53). Any other roster size has no entry, which makes the test fail
     * loudly rather than quietly pass on a half-applied migration.
     *
     * <p>67 and 68 carry the SAME numbers on purpose. These counts are absolute
     * numbers of mastered spells, not percentages, so the ladder does not move when
     * {@code dispel} lands in wave 9 and takes the roster from 67 to 68. Their share
     * of the roster does (78% becomes 77%), which is the correct direction for a
     * capstone gate and needs no yml edit.
     */
    private static final Map<Integer, Map<Integer, Integer>> UNSCOPED_BY_ROSTER = Map.of(
            38, Map.of(6, 12, 7, 16, 8, 20, 9, 30),
            67, Map.of(6, 21, 7, 28, 8, 35, 9, 53),
            68, Map.of(6, 21, 7, 28, 8, 35, 9, 53)
    );

    /** The first number in a requirement's description, e.g. "Requires 30 max-mastered spells". */
    private static final Pattern DESCRIPTION_COUNT = Pattern.compile("Requires (\\d+) ");

    /**
     * progression.yml nests the ladder under {@code circle.levels.<n>.requirements},
     * not under a top-level {@code circles} map. Every path in this class goes through
     * this one accessor so a future rename is a one-line change rather than six.
     */
    private static final String REQUIREMENTS = "circle.levels.%d.requirements";

    private static YamlConfiguration progression;

    @BeforeAll
    static void loadProgression() throws IOException {
        Assumptions.assumeTrue(BundledSpellRoster.rosterIsDiscoverable(),
                "the spells resource directory is not enumerable on this classpath");
        try (InputStream in = ProgressionConfigTest.class.getResourceAsStream("/progression.yml")) {
            assertNotNull(in, "missing bundled resource /progression.yml");
            progression = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static List<Map<?, ?>> requirementsOf(int circle) {
        String path = String.format(REQUIREMENTS, circle);
        List<Map<?, ?>> requirements = progression.getMapList(path);
        assertFalse(requirements.isEmpty(), "circle " + circle + " must declare requirements at " + path);
        return requirements;
    }

    private static boolean isMasteredSpellCount(Map<?, ?> requirement) {
        return "mastered_spell_count".equals(requirement.get("type"));
    }

    private static int intOf(Map<?, ?> requirement, String key) {
        Object value = requirement.get(key);
        assertTrue(value instanceof Number,
                "requirement key '" + key + "' must be a number, was " + value);
        return ((Number) value).intValue();
    }

    private static String stringOf(Map<?, ?> requirement, String key) {
        Object value = requirement.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> requirementDescriptions(int circle) {
        List<String> descriptions = new ArrayList<>();
        for (Map<?, ?> requirement : requirementsOf(circle)) {
            String description = stringOf(requirement, "description");
            if (description != null) descriptions.add(description);
        }
        return descriptions;
    }

    private static int rosterSize() {
        return BundledSpellRoster.spellIds().size();
    }

    @Test
    void theRosterIsASizeThisMapHasAnEntryFor() {
        int size = rosterSize();
        assertTrue(UNSCOPED_BY_ROSTER.containsKey(size),
                "the roster is " + size + " spells, which has no entry in UNSCOPED_BY_ROSTER. "
                        + "Add the rescaled circles 6-9 counts for it, or check that a spell yml was "
                        + "added or removed by accident.");
    }

    @Test
    void scopedCirclesKeepTheirCircleKeyAndTheirCounts() {
        // .entrySet(), not .entry. Map.entry(Set<K,V>) is a Java 19 API and this
        // project compiles at Java 17 - and it returns a Set<Entry>, not a
        // Set<Entry>'s element type, so it would not be iterable here either.
        for (Map.Entry<Integer, Integer> entry : SCOPED_COUNTS.entrySet()) {
            // entry.getKey() is the SCOPED circle; the requirement lives on the next one.
            int scopedCircle = entry.getKey();
            int owningCircle = scopedCircle + 1;
            int found = 0;
            for (Map<?, ?> requirement : requirementsOf(owningCircle)) {
                if (!isMasteredSpellCount(requirement)) continue;
                found++;
                assertEquals(scopedCircle, intOf(requirement, "circle"),
                        "circles 2-5 are scoped so the roster change does not move them; circle "
                                + owningCircle + " must scope to circle " + scopedCircle);
                assertEquals(entry.getValue(), intOf(requirement, "count"),
                        "circle " + owningCircle + " requires " + entry.getValue()
                                + " max-mastered circle-" + scopedCircle + " spell");
            }
            assertEquals(1, found,
                    "circle " + owningCircle + " must declare exactly one mastered_spell_count");
        }
    }

    @Test
    void unscopedHighCirclesHaveNoCircleKey() {
        for (int circle = 6; circle <= 9; circle++) {
            for (Map<?, ?> requirement : requirementsOf(circle)) {
                if (!isMasteredSpellCount(requirement)) continue;
                assertFalse(requirement.containsKey("circle"),
                        "circles 6-9 deliberately omit circle: so the requirement counts the whole "
                                + "roster. If you add the key back, RequirementFactory will filter on "
                                + "it and the threshold means something entirely different.");
            }
        }
    }

    @Test
    void unscopedCountsMatchTheDiscoveredRosterSize() {
        int size = rosterSize();
        Map<Integer, Integer> expected = UNSCOPED_BY_ROSTER.get(size);
        Assumptions.assumeTrue(expected != null,
                "no expectation table for a " + size + "-spell roster; see theRosterIsThirtyEightOrSixtyEight");
        for (int circle = 6; circle <= 9; circle++) {
            assertEquals(expected.get(circle), countFor(circle),
                    "circle " + circle + "'s unscoped mastered_spell_count for a " + size
                            + "-spell roster, per spec section 11");
        }
    }

    @Test
    void unscopedCountsRiseStrictlyWithCircle() {
        int previous = -1;
        for (int circle = 6; circle <= 9; circle++) {
            int count = countFor(circle);
            assertTrue(count > previous,
                    "circle " + circle + " requires " + count + ", which does not exceed circle "
                            + (circle - 1) + "'s " + previous);
            previous = count;
        }
    }

    @Test
    void circleNineStaysAMajorityOfTheRoster() {
        int size = rosterSize();
        double share = (double) countFor(9) / size;
        // 30/38 is 79% today; spec section 11 holds that share at 53/68 = 78% so the
        // endgame wall does not collapse when the roster grows.
        assertTrue(share > 0.70,
                "circle 9 requires " + countFor(9) + " of " + size + " max-mastered spells, which is only "
                        + String.format("%.0f%%", share * 100) + " - spec section 11 holds this near 78%");
        assertTrue(share < 0.90,
                "circle 9 requiring " + String.format("%.0f%%", share * 100) + " of the roster leaves "
                        + "no headroom; the design point is 78%");
    }

    @Test
    void everyMasteredSpellCountNamesItsOwnNumberInTheDescription() {
        // Two copy defects in the shipped file: circle 3's description says "1
        // max-mastered 2nd circle spell" while its count is 2, and circle 9's says "all
        // 30 spells" while its count is 30 of 38.
        //
        // The fix has to satisfy DESCRIPTION_COUNT, which is the literal
        // "Requires (\d+) ". "Requires all 53 spells max-mastered" would NOT match:
        // the regex needs digits immediately after "Requires " and a trailing space,
        // and matcher.find() would return false. Wave 7's T27 writes circle 9 as
        //   description: "Requires 53 max-mastered spells"
        // and circle 3 as
        //   description: "Requires 2 max-mastered 2nd circle spells"
        // Anything with a word between "Requires " and the number fails this assertion.
        for (int circle = 2; circle <= 9; circle++) {
            for (Map<?, ?> requirement : requirementsOf(circle)) {
                if (!isMasteredSpellCount(requirement)) continue;
                int count = intOf(requirement, "count");
                String description = stringOf(requirement, "description");
                assertNotNull(description, "circle " + circle + "'s requirement needs a description");
                Matcher matcher = DESCRIPTION_COUNT.matcher(description);
                assertTrue(matcher.find(),
                        "circle " + circle + "'s description '" + description
                                + "' must name its count so the GUI can show the real threshold");
                assertEquals(count, Integer.parseInt(matcher.group(1)),
                        "circle " + circle + "'s description says " + matcher.group(1)
                                + " but its count is " + count);
            }
        }
    }

    @Test
    void everyCircleRespectsTheFocusCeiling() {
        // Spec section 6.2: today's circle-5 spells require 140-150 focus against a
        // maxFocus of 150, so they are uncastable after a single full-health hit.
        int[] maxFocus = {110, 110, 120, 130, 140, 150, 160, 170, 180, 190};
        for (int circle = 6; circle <= 9; circle++) {
            assertTrue(requirementDescriptions(circle).size() > 0,
                    "circle " + circle + " must declare at least one requirement description");
            double ceiling = 0.85 * maxFocus[circle];
            // The focus cap is a ladder property, not a progression.yml one, so this
            // asserts the ladder agrees with the shipped max_focus column.
            assertTrue(SpellBudgets.focusFor(circle) <= ceiling,
                    "circle " + circle + " focus " + SpellBudgets.focusFor(circle)
                            + " is over the 85% ceiling of " + ceiling);
        }
    }

    private static int countFor(int circle) {
        for (Map<?, ?> requirement : requirementsOf(circle)) {
            if (isMasteredSpellCount(requirement)) {
                return intOf(requirement, "count");
            }
        }
        throw new AssertionError("circle " + circle + " declares no mastered_spell_count requirement");
    }
}