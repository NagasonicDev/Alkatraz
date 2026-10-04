package me.nagasonic.alkatraz.spells;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec section 10.1: every bundled spell's casting code is five characters from
 * {R, L, S}, and no two spells share one.
 *
 * <p>A collision is not cosmetic - {@code WandComponentHandler:128} reads five inputs
 * and dispatches on them, so two spells with the same code means one of them becomes
 * uncastable through the wand.
 *
 * <p>Forward-looking, and asserted here because it is the reason future spells need a
 * fourth input letter: after the 29 additions, all 27 possible three-character
 * prefixes are claimed.
 *
 * <p>There is deliberately no whole-roster Hamming-separation assertion. Spec section
 * 10.1 records that the {@code >= 2} separation rule is dead - once 67 codes are
 * assigned every one of the 243 five-character codes sits within Hamming distance 1 of
 * at least one of them, and 33 of the 68 carry two or more Hamming-1 neighbours. The
 * only separation claim this suite can honestly make is the pair-level one in
 * {@link #theHighestRiskPairStillDiffersInTwoPositions()}, which pins the specific
 * documented cost rather than a property the roster no longer has.
 */
class SpellCodeRegistryTest {

    private static final Pattern VALID_CODE = Pattern.compile("^[RLS]{5}$");
    private static final int CODE_LENGTH = 5;

    private static List<String> roster;

    @BeforeAll
    static void discoverRoster() {
        Assumptions.assumeTrue(BundledSpellRoster.rosterIsDiscoverable(),
                "the spells resource directory is not enumerable on this classpath");
        roster = BundledSpellRoster.spellIds();
    }

    @Test
    void everySpellHasAFiveCharacterCode() throws IOException {
        for (String id : roster) {
            String code = BundledSpellRoster.load(id).getString("code");
            assertNotNull(code, id + " must declare a code:");
            assertEquals(CODE_LENGTH, code.length(),
                    id + "'s code '" + code + "' must be exactly " + CODE_LENGTH
                            + " characters - WandComponentHandler:128 hardcodes the length");
        }
    }

    @Test
    void everyCodeIsBuiltOnlyFromRightLeftAndSwap() throws IOException {
        for (String id : roster) {
            String code = BundledSpellRoster.load(id).getString("code");
            assertTrue(VALID_CODE.matcher(code).matches(),
                    id + "'s code '" + code + "' contains a character outside {R, L, S}");
        }
    }

    @Test
    void noTwoSpellsShareACode() throws IOException {
        Map<String, String> owner = new HashMap<>();
        List<String> collisions = new ArrayList<>();
        for (String id : roster) {
            String code = BundledSpellRoster.load(id).getString("code");
            String previous = owner.putIfAbsent(code, id);
            if (previous != null) {
                collisions.add(previous + " and " + id + " both use '" + code + "'");
            }
        }
        assertTrue(collisions.isEmpty(),
                "a duplicate code makes one spell uncastable through the wand: "
                        + String.join("; ", collisions));
    }

    @Test
    void theCodeSpaceIsLargeEnoughForTheRoster() {
        // 3^5 = 243 distinct codes exist. The 68-spell roster uses 68 of them, and
        // every three-character prefix is claimed - which is why spec section 10.1
        // flags a code_len field or a fourth input letter before the roster grows again.
        assertEquals(243, (int) Math.pow(3, CODE_LENGTH), "the code space is 3^5");
        assertTrue(roster.size() < 243,
                "the roster of " + roster.size() + " must stay inside the 243-code space");
    }

    @Test
    void everyThreeCharacterPrefixIsClaimed() throws IOException {
        Map<String, Integer> prefixCounts = new HashMap<>();
        for (String id : roster) {
            String code = BundledSpellRoster.load(id).getString("code");
            if (code.length() < 3) continue;
            prefixCounts.merge(code.substring(0, 3), 1, Integer::sum);
        }
        assertEquals(27, prefixCounts.size(),
                "there are exactly 27 three-character prefixes; the roster claims "
                        + prefixCounts.size() + " of them. Once all 27 are claimed any new spell must "
                        + "share its first three inputs with an existing one - consider a fourth "
                        + "input letter or a code_len field before that happens.");
    }

    @Test
    void theHighestRiskPairStillDiffersInTwoPositions() throws IOException {
        // Spec section 10.1 names light_bolt LLLRR as the closest call: three identical
        // leading inputs against fireball's LLLLL. It is inherent, because LLLLL was
        // already taken. light_bolt arrives in wave 5 (T22), so until it is bundled this
        // assertion cannot be evaluated - it skips rather than passing vacuously.
        Assumptions.assumeTrue(BundledSpellRoster.has("light_bolt"),
                "light_bolt is not bundled yet - it arrives in wave 5 (T22)");
        Assumptions.assumeTrue(BundledSpellRoster.has("fireball"), "fireball is not bundled");
        String lightBolt = BundledSpellRoster.load("light_bolt").getString("code");
        String fireball = BundledSpellRoster.load("fireball").getString("code");
        assertEquals("LLLRR", lightBolt, "light_bolt's assigned code");
        assertEquals("LLLLL", fireball, "fireball's pre-existing code");
        assertEquals(2, hamming(lightBolt, fireball),
                "they differ in the last two positions only, which is the documented cost of "
                        + "LLLLL having been taken first");
    }

    private static int hamming(String a, String b) {
        assertEquals(a.length(), b.length(), "Hamming distance needs equal lengths");
        int distance = 0;
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i)) distance++;
        }
        return distance;
    }
}