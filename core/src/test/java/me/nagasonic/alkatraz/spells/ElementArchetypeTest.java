package me.nagasonic.alkatraz.spells;

import me.nagasonic.alkatraz.api.Element;
import me.nagasonic.alkatraz.spells.types.SpellRole;
import me.nagasonic.alkatraz.spells.util.SpellBudgets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec section 5.2, enforced mechanically. The allowlist below is the design's
 * <em>forbidden</em> column turned into test data; a DoT field on FIRE or a terrain
 * spell on AIR fails the build instead of quietly breaking the element's identity.
 *
 * <p>The "Owns" column of spec section 5.2 is descriptive and is NOT enforced - a
 * role that is neither owned nor forbidden is permitted but discouraged, which is
 * why EARTH's low-circle earth_throw and earth_spike can be BURST while still
 * satisfying "earth acts on space, never on health directly".
 */
class ElementArchetypeTest {

    /**
     * Spec section 5.2. This table is duplicated from SpellBudgets on purpose: if the
     * test imported the production table it could never catch a change to it.
     */
    private static final Map<Element, Set<SpellRole>> ALLOWLIST = new EnumMap<>(Element.class);

    static {
        ALLOWLIST.put(Element.FIRE, EnumSet.of(SpellRole.BURST, SpellRole.DENIAL, SpellRole.TERRAIN));
        ALLOWLIST.put(Element.WATER, EnumSet.of(SpellRole.SUSTAIN, SpellRole.DENIAL, SpellRole.BURST));
        ALLOWLIST.put(Element.EARTH, EnumSet.of(SpellRole.TERRAIN, SpellRole.DENIAL, SpellRole.BURST));
        ALLOWLIST.put(Element.AIR, EnumSet.of(SpellRole.UTILITY, SpellRole.DENIAL, SpellRole.BURST,
                SpellRole.SUSTAIN));
        ALLOWLIST.put(Element.LIGHT, EnumSet.of(SpellRole.UTILITY, SpellRole.BURST, SpellRole.SUSTAIN,
                SpellRole.DENIAL));
        ALLOWLIST.put(Element.DARK, EnumSet.of(SpellRole.UTILITY, SpellRole.SUSTAIN, SpellRole.BURST));
        ALLOWLIST.put(Element.NONE, EnumSet.of(SpellRole.UTILITY, SpellRole.DENIAL));
    }

    /**
     * Last id of T24's NONE batch in the master's alphabetical CMD allocation, so it
     * cannot land before the wave-5 spells that fill circles 7-9. Used to activate the
     * full 0-9 ladder requirement only once that content exists.
     */
    private static final String WAVE_FIVE_SENTINEL = "arcane_resonance";

    private static List<String> roster;

    @BeforeAll
    static void discoverRoster() {
        Assumptions.assumeTrue(BundledSpellRoster.rosterIsDiscoverable(),
                "the spells resource directory is not enumerable on this classpath");
        roster = BundledSpellRoster.spellIds();
    }

    private static Map<Element, List<String>> byElement() throws IOException {
        Map<Element, List<String>> columns = new EnumMap<>(Element.class);
        for (String id : roster) {
            YamlConfiguration y = BundledSpellRoster.load(id);
            columns.computeIfAbsent(Element.valueOf(y.getString("element")), k -> new ArrayList<>()).add(id);
        }
        return columns;
    }

    @Test
    void allowlistMatchesSpecSectionFiveTwo() {
        for (Element element : Element.values()) {
            if (element == Element.NONE) continue;
            assertEquals(ALLOWLIST.get(element), SpellBudgets.allowedRoles(element),
                    element + "'s allowlist drifted from the spec section 5.2 table");
        }
        assertEquals(EnumSet.of(SpellRole.UTILITY, SpellRole.DENIAL), SpellBudgets.allowedRoles(Element.NONE),
                "NONE owns UTILITY and DENIAL; magic_missile's BURST is grandfathered per-spell, "
                        + "not by widening the table");
    }

    @Test
    void everySpellDeclaresARoleAllowedForItsElement() throws IOException {
        for (String id : roster) {
            YamlConfiguration y = BundledSpellRoster.load(id);
            Element element = Element.valueOf(y.getString("element"));
            SpellRole role = SpellRole.parse(y.getString("role"));
            assertNotNull(role, id + " must declare a role");
            assertTrue(ALLOWLIST.get(element).contains(role)
                            || SpellBudgets.isGrandfatheredBurst(id, element, role),
                    id + " declares " + role + ", which " + element + " may not use");
        }
    }

    @Test
    void fireNeverSustains() throws IOException {
        for (String id : byElement().getOrDefault(Element.FIRE, List.of())) {
            SpellRole role = SpellRole.parse(BundledSpellRoster.load(id).getString("role"));
            assertNotEquals(SpellRole.SUSTAIN, role,
                    id + " is a SUSTAIN fire spell; fire has the highest per-hit damage in the game "
                            + "and the lowest total-per-cast at equal circle - it never ticks");
        }
    }

    @Test
    void waterAirAndLightNeverReshapeLand() throws IOException {
        Map<Element, List<String>> columns = byElement();
        for (Element element : List.of(Element.WATER, Element.AIR, Element.LIGHT)) {
            assertFalse(ALLOWLIST.get(element).contains(SpellRole.TERRAIN),
                    element + " is forbidden from TERRAIN");
            for (String id : columns.getOrDefault(element, List.of())) {
                SpellRole role = SpellRole.parse(BundledSpellRoster.load(id).getString("role"));
                assertNotEquals(SpellRole.TERRAIN, role,
                        id + " is a TERRAIN " + element + " spell; light never reshapes land and "
                                + "water and air move things rather than shaping them");
            }
        }
    }

    @Test
    void earthHasNoConvenienceSpells() throws IOException {
        assertFalse(ALLOWLIST.get(Element.EARTH).contains(SpellRole.UTILITY),
                "EARTH is forbidden from UTILITY - earth has no convenience spells");
        for (String id : byElement().getOrDefault(Element.EARTH, List.of())) {
            SpellRole role = SpellRole.parse(BundledSpellRoster.load(id).getString("role"));
            assertNotEquals(SpellRole.UTILITY, role,
                    id + " is an EARTH utility spell; earth acts on space, never on convenience");
        }
    }

    @Test
    void darkHasNoDefensiveRole() throws IOException {
        assertFalse(ALLOWLIST.get(Element.DARK).contains(SpellRole.DENIAL),
                "DARK is the only element with no defensive option at any circle - its power is "
                        + "taken, never given. Summon UTILITY is owned and stays allowed.");
        assertTrue(ALLOWLIST.get(Element.DARK).contains(SpellRole.UTILITY),
                "summon UTILITY IS owned by DARK; the table must allow it");
        for (String id : byElement().getOrDefault(Element.DARK, List.of())) {
            assertNotEquals(SpellRole.DENIAL, SpellRole.parse(BundledSpellRoster.load(id).getString("role")),
                    id + " is a DARK DENIAL spell; dark never shields");
        }
    }

    @Test
    void noneDealsNoDamageExceptMagicMissile() throws IOException {
        for (String id : byElement().getOrDefault(Element.NONE, List.of())) {
            YamlConfiguration y = BundledSpellRoster.load(id);
            SpellRole role = SpellRole.parse(y.getString("role"));
            assertTrue(SpellRole.UTILITY == role || SpellRole.DENIAL == role
                            || SpellBudgets.isGrandfatheredBurst(id, Element.NONE, role),
                    id + " is a NONE " + role + "; none manipulates the caster, never the target");
        }
    }

    @Test
    void everyElementColumnIsPopulated() throws IOException {
        Map<Element, List<String>> columns = byElement();
        for (Element element : Element.values()) {
            assertFalse(columns.getOrDefault(element, List.of()).isEmpty(),
                    element + " has no spell in the roster - the brief is to fill every element's gaps");
        }
    }

    /**
     * Spec section 1.2: the ladder must be filled at 6, 7, 8, 9 plus the circle-3 AIR
     * and circle-1 LIGHT gaps.
     *
     * <p>Two clauses, because the roster's top circle changes twice. Always: the
     * circles that are populated must be CONTIGUOUS from 0 to the highest one present,
     * so a ladder can never develop a hole. Then, once wave 5 has landed (detected by
     * the {@code arcane_resonance} sentinel rather than by a flag), the full 0-9
     * requirement activates and a missing circle 7-9 spell fails the build. Without
     * the sentinel this clause would fail at the wave-4 gate on a roster that
     * legitimately stops at circle 6.
     */
    @Test
    void theRosterCoversEveryCircleFromZeroToNine() throws IOException {
        Map<Integer, Integer> perCircle = new LinkedHashMap<>();
        for (String id : roster) {
            int circle = BundledSpellRoster.load(id).getInt("level");
            perCircle.merge(circle, 1, Integer::sum);
        }
        int highest = perCircle.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int circle = 0; circle <= highest; circle++) {
            assertTrue(perCircle.getOrDefault(circle, 0) > 0,
                    "circle " + circle + " has no spell, yet circle " + highest + " does; "
                            + "the ladder must be contiguous from circle 0 upward");
        }
        if (!BundledSpellRoster.has(WAVE_FIVE_SENTINEL)) return;
        for (int circle = 0; circle <= 9; circle++) {
            assertTrue(perCircle.getOrDefault(circle, 0) > 0,
                    "circle " + circle + " has no spell; spec section 1.2 requires the ladder filled "
                            + "at 6, 7, 8, 9 plus the circle-3 AIR gap and the circle-1 LIGHT gap");
        }
    }

    private static void assertNotEquals(Object unexpected, Object actual, String message) {
        assertFalse(unexpected.equals(actual), message);
    }
}