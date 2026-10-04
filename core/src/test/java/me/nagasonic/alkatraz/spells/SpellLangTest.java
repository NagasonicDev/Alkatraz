package me.nagasonic.alkatraz.spells;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lang prefix is a HARDCODED LITERAL in each spell's Java class. Nothing derives
 * it, and LangManager.get returns the raw key on a miss (LangManager.java:64-70), so
 * a typo is invisible until a player holds the item and sees the literal text
 * "spells.foo.book_name" where the title should be.
 *
 * <p>This test pins the convention instead: the lang prefix of a spell is its id with
 * underscores removed. Two prefixes broke that convention before this test existed -
 * earthsplitter used "fissure" (the class name) and skeletal_knights kept its
 * underscore - and both were unfixable by a human reviewer because nothing failed.
 *
 * <p>Set equality, not a per-spell loop, is the load-bearing assertion: it catches a
 * missing key and an ORPHANED key in the same check, which is what "fissure" and
 * "skeletal_knights" were.
 *
 * <p>Zero Bukkit mocks: english.lang is parsed with the same rules LangManager uses
 * (trim, skip blank and '#', split on the first '='), and the roster comes from the
 * same BundledSpellRoster helper the wave-4 tests use.
 */
class SpellLangTest {

    /**
     * Spells permitted to have no book_name. Exactly one, and it is not a convention
     * gap: MagicMissile.getSpellBook() returns null (MagicMissile.java:242-244) and
     * the starter spell is granted directly rather than as a scroll.
     */
    private static final Set<String> NO_SPELLBOOK = Set.of("magic_missile");

    /** The suffixes every spell's getSpellBook() reads. Every new circle-5+ block has exactly these two. */
    private static final List<String> BOOK_SUFFIXES = List.of("book_name", "lore1");

    /** The only key shape SpellLangTest governs. Suffix-scoped so spells.mana_cost etc. are ignored. */
    private static final Pattern BOOK_KEY = Pattern.compile("^spells\\.([a-z0-9_]+)\\.(book_name|lore[0-9])$");

    private static List<String> roster;
    private static Map<String, String> messages;

    @BeforeAll
    static void loadLangAndRoster() throws IOException {
        Assumptions.assumeTrue(BundledSpellRoster.rosterIsDiscoverable(),
                "the spells resource directory is not enumerable on this classpath");
        roster = BundledSpellRoster.spellIds();
        messages = parseLang();
    }

    /** Mirrors LangManager.loadFromReader: trim, skip blank and '#', split on the first '='. */
    private static Map<String, String> parseLang() throws IOException {
        Map<String, String> parsed = new LinkedHashMap<>();
        try (InputStream in = SpellLangTest.class.getResourceAsStream("/lang/english.lang")) {
            assertNotNull(in, "missing bundled resource /lang/english.lang");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    parsed.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
        }
        return parsed;
    }

    private static String prefixOf(String spellId) {
        return spellId.replace("_", "");
    }

    private static Set<String> expectedBookedSpellIds() {
        Set<String> ids = new LinkedHashSet<>(roster);
        ids.removeAll(NO_SPELLBOOK);
        return ids;
    }

    private static String resolve(String key) {
        String value = messages.get(key);
        // LangManager returns the key itself on a miss, so a resolved value that equals
        // its own key is indistinguishable from a miss for the player.
        return value == null || value.equals(key) ? null : value;
    }

    @Test
    void theRosterIsSixtySeven() {
        assertEquals(67, roster.size(),
                "wave 7 registers 67 spells; dispel is held back for wave 9 (T31). A different count "
                        + "means a spell yml was added or removed without updating SpellRegistry.SPELL_IDS");
    }

    @Test
    void everyBookedSpellResolvesItsBookNameAndLore() {
        for (String id : expectedBookedSpellIds()) {
            String prefix = prefixOf(id);
            for (String suffix : BOOK_SUFFIXES) {
                String key = "spells." + prefix + "." + suffix;
                assertNotNull(resolve(key),
                        key + " is missing from english.lang, or its value is the key itself - "
                                + "LangManager.get returns the raw key on a miss, so " + id
                                + "'s book renders the literal text '" + key + "' to the player");
            }
        }
    }

    @Test
    void thereAreNoOrphanedSpellBookPrefixes() {
        Set<String> declared = new TreeSet<>();
        Set<String> expected = new TreeSet<>();
        for (String id : expectedBookedSpellIds()) expected.add(prefixOf(id));

        for (String key : messages.keySet()) {
            Matcher matcher = BOOK_KEY.matcher(key);
            if (matcher.find()) declared.add(matcher.group(1));
        }

        Set<String> orphans = new LinkedHashSet<>(declared);
        orphans.removeAll(expected);
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(declared);

        assertTrue(orphans.isEmpty(),
                "english.lang declares book keys for prefixes that match no bundled spell id: "
                        + String.join(", ", orphans) + ". 'fissure' (the Fissure class name, not the "
                        + "earthsplitter id) and 'skeletal_knights' (an un-de-underscored prefix) were "
                        + "both caught by this assertion");
        assertTrue(missing.isEmpty(),
                "bundled spells with no book_name/lore keys at all: " + String.join(", ", missing)
                        + " - their items render the raw lang key");
    }

    @Test
    void noSpellPrefixContainsAnUnderscore() {
        for (String id : expectedBookedSpellIds()) {
            String prefix = prefixOf(id);
            assertFalse(prefix.contains("_"),
                    "the lang prefix must be the de-underscored id, but '" + prefix
                            + "' still contains one - 19 multi-word ids de-underscore and this one does not");
        }
    }

    @Test
    void theMagicMissileExemptionIsExactlyOneSpell() {
        assertEquals(Set.of("magic_missile"), NO_SPELLBOOK,
                "the no-spellbook exemption list is the escape hatch that makes missing keys legal. "
                        + "It must contain only magic_missile, whose getSpellBook() returns null");
    }

    @Test
    void theEarthSplitterPrefixFollowsItsId() {
        assertNotNull(resolve("spells.earthsplitter.book_name"),
                "earthsplitter's book must key off its id, not off the Fissure class name");
        assertFalse(messages.containsKey("spells.fissure.book_name"),
                "spells.fissure.* must be gone - it is the one prefix in the file that no spell id derives");
    }
}