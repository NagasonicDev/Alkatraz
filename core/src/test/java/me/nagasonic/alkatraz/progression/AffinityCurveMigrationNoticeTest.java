package me.nagasonic.alkatraz.progression;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The affinity curve changed from 10 x points to 10 x points^0.65, which silently
 * lowered every existing player's per-element affinity on next login: a 100-affinity
 * fire mage becomes 45. That is a player-visible stat change, so it gets a one-time
 * notice - and a notice that misreports the numbers is worse than none.
 *
 * <p>Pure arithmetic over plain data, so zero Bukkit mocks.
 */
class AffinityCurveMigrationNoticeTest {

    private static final double PER_POINT = 10.0;
    private static final double EXPONENT = 0.65;

    private static Map<Element, Integer> points(Element element, int invested) {
        Map<Element, Integer> map = new EnumMap<>(Element.class);
        for (Element e : Element.values()) {
            if (e == Element.NONE) continue;
            map.put(e, 0);
        }
        map.put(element, invested);
        return map;
    }

    private static List<AffinityCurveMigrationNotice.Change> changes(Map<Element, Integer> map) {
        return AffinityCurveMigrationNotice.computeChanges(map, PER_POINT, EXPONENT);
    }

    @Test
    void tenFirePointsDropsFromOneHundredToFortyFive() {
        List<AffinityCurveMigrationNotice.Change> result = changes(points(Element.FIRE, 10));
        assertEquals(1, result.size(), "only the invested element changes");
        AffinityCurveMigrationNotice.Change fire = result.get(0);
        assertEquals("Fire", fire.elementName(), "the notice names the element for the player");
        assertEquals(100, fire.oldValue(), "10 points x 10 per point under the old linear curve");
        assertEquals(45, fire.newValue(), "10 x 10^0.65 = 44.67, rounded to 45");
    }

    @Test
    void theSpecLadderIsReproducedAtEveryCircle() {
        // AffinityCurveTest already pins the exact curve; this asserts the NOTICE's
        // rounding matches it, so the message never contradicts the stats menu.
        int[] points = {5, 10, 17, 27, 37, 52, 67, 87, 112};
        int[] expected = {28, 45, 63, 85, 105, 130, 154, 182, 215};
        for (int i = 0; i < points.length; i++) {
            List<AffinityCurveMigrationNotice.Change> result = changes(points(Element.WATER, points[i]));
            assertEquals(1, result.size(), points[i] + " water points produce one line");
            assertEquals(expected[i], result.get(0).newValue(),
                    "circle " + (i + 1) + " is " + points[i] + " points; the notice must print the same "
                            + "number AffinityCurveTest and StatsMenu show");
        }
    }

    @Test
    void onePointIsUnchangedAndIsNotReported() {
        // 1 x 10 = 10 and 10 x 1^0.65 = 10. Reporting "10 -> 10" would be noise.
        assertTrue(changes(points(Element.EARTH, 1)).isEmpty(),
                "a single point is worth the same under both curves, so it is not a change worth reporting");
    }

    @Test
    void aPlayerWithNothingInvestedGetsNothing() {
        assertTrue(changes(points(Element.FIRE, 0)).isEmpty(),
                "zero invested stat points in every element means no notice at all");
    }

    @Test
    void onlyInvestedElementsAreListed() {
        Map<Element, Integer> map = points(Element.FIRE, 20);
        map.put(Element.DARK, 8);
        map.put(Element.AIR, 0);
        List<AffinityCurveMigrationNotice.Change> result = changes(map);
        assertEquals(2, result.size(), "only the two invested elements appear");
        assertEquals(List.of("Fire", "Dark"),
                result.stream().map(AffinityCurveMigrationNotice.Change::elementName).toList(),
                "listed in Element declaration order so the message is stable between logins");
    }

    @Test
    void theNoneElementIsNeverListed() {
        Map<Element, Integer> map = points(Element.FIRE, 5);
        map.put(Element.NONE, 99);
        for (AffinityCurveMigrationNotice.Change change : changes(map)) {
            assertTrue(!"None".equals(change.elementName()),
                    "NONE is not an affinity element - MagicProfile.getAffinity maps it to magicAffinity, "
                            + "which this curve does not touch");
        }
    }

    @Test
    void aLinearExponentReproducesTheOldCurveExactly() {
        List<AffinityCurveMigrationNotice.Change> result =
                AffinityCurveMigrationNotice.computeChanges(points(Element.LIGHT, 30), PER_POINT, 1.0);
        assertTrue(result.isEmpty(),
                "with affinity_exponent set back to 1.0 nothing changes and the notice stays silent - "
                        + "the documented escape hatch must not spam players");
    }

    @Test
    void theSeenFlagIsNotATransientFieldName() {
        assertEquals("affinityCurveNoticeSeen", AffinityCurveMigrationNotice.SEEN_FLAG,
                "the flag lives in the profile's bool map, alongside FirstJoinTutorial's tutorialSeen, "
                        + "and is persisted on logout - not in any transient field");
    }

    @Test
    void theSeenFlagIsARegisteredBoolStatOnEveryNewProfile() {
        // Profile.getBool throws IllegalArgumentException for a stat that was never
        // registered, which crashed PlayerJoinEvent with "Stat affinityCurveNoticeSeen
        // not found". A profile persisted before this flag existed has no saved copy to
        // self-register on load (ProfilePersistence only registers what it reads), so the
        // stat has to be declared in MagicProfile's initializer like tutorialSeen is.
        MagicProfile profile = new MagicProfile(UUID.randomUUID());
        assertTrue(profile.isBool(AffinityCurveMigrationNotice.SEEN_FLAG),
                "the notice reads this flag on every join, so a fresh profile must already know it");
        assertFalse(profile.getBool(AffinityCurveMigrationNotice.SEEN_FLAG),
                "defaults to false so the check runs exactly once per player");
        profile.setBool(AffinityCurveMigrationNotice.SEEN_FLAG, true);
        assertTrue(profile.getBool(AffinityCurveMigrationNotice.SEEN_FLAG),
                "the notice sets the flag before it has anything to say, so it must be writable");
    }
}
