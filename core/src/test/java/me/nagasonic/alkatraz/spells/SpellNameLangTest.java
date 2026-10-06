package me.nagasonic.alkatraz.spells;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A spell's name and description moved out of spells/&lt;id&gt;.yml and into
 * english.lang on 2026-10-06. The move exists because six spells had their BOOK's
 * flavour title pasted into display_name - "Scroll of the First Ray" on light_bolt,
 * "Litany of the Ascendant" on apotheosis - so /spells, the mastery bar and the wand
 * lore showed the book title instead of the spell name. Nothing failed, because
 * display_name was free text in a YAML file that no test read.
 *
 * <p>These two tests pin the invariants that make the bug unrepeatable. Every roster id
 * must resolve a non-empty name and description, and the set of prefixes declaring
 * them must EQUAL the roster's prefixes - set equality, not a per-spell loop, because
 * that catches a missing key and an orphaned key in the same assertion. That is exactly
 * how "fissure" (a Java class name) and "skeletal_knights" (an un-de-underscored
 * prefix) survived in SpellLangTest.
 *
 * <p>Zero Bukkit mocks: english.lang is parsed with the same rules LangManager uses
 * (trim, skip blank and '#', split on the first '=') and the roster comes from
 * BundledSpellRoster, both copied from SpellLangTest.
 */
class SpellNameLangTest {

    /** Only the key shape this test governs, so spells.mana_cost and spells.cast.* are ignored. */
    private static final Pattern NAME_KEY = Pattern.compile("^spells\\.([a-z0-9_]+)\\.(name|description)$");

    private static List<String> roster;
    private static Map<String, String> messages;

    @BeforeAll
    static void loadLangAndRoster() {
        Assumptions.assumeTrue(BundledSpellRoster.rosterIsDiscoverable(),
                "the spells resource directory is not enumerable on this classpath");
        roster = BundledSpellRoster.spellIds();
        messages = BundledLang.parse();
    }

    private static String prefixOf(String spellId) {
        return spellId.replace("_", "");
    }

    private static String resolve(String key) {
        return BundledLang.resolve(messages, key);
    }

    @Test
    void everySpellResolvesANameAndADescription() {
        for (String id : roster) {
            String prefix = prefixOf(id);
            for (String suffix : List.of("name", "description")) {
                String key = "spells." + prefix + "." + suffix;
                assertNotNull(resolve(key),
                        key + " is missing or empty in english.lang, so " + id + " renders the literal "
                                + "lang key (or nothing at all) in the /spells GUI, the spellbook lore, "
                                + "the hotbar item name and the mastery boss bar");
            }
        }
    }

    @Test
    void thereAreNoOrphanedSpellNamePrefixes() {
        Set<String> declared = new TreeSet<>();
        Set<String> expected = new TreeSet<>();
        for (String id : roster) expected.add(prefixOf(id));

        for (String key : messages.keySet()) {
            Matcher matcher = NAME_KEY.matcher(key);
            if (matcher.find()) declared.add(matcher.group(1));
        }

        Set<String> orphans = new LinkedHashSet<>(declared);
        orphans.removeAll(expected);
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(declared);

        assertTrue(orphans.isEmpty(),
                "english.lang declares spells.<prefix>.name/.description for prefixes that match no "
                        + "bundled spell id: " + String.join(", ", orphans) + ". A prefix is the id with "
                        + "underscores removed; a class name or an underscored id here means those keys "
                        + "are read by nothing");
        assertTrue(missing.isEmpty(),
                "bundled spells with no spells.<prefix>.name/.description keys at all: "
                        + String.join(", ", missing));
    }

    @Test
    void theRosterIsSixtyEight() {
        assertEquals(68, roster.size(),
                "a different count means a spell yml was added or removed without updating "
                        + "SpellRegistry.SPELL_IDS");
    }

    @Test
    void noSpellYmlStillDeclaresANameOrDescription() throws IOException {
        for (String id : roster) {
            YamlConfiguration spell = BundledSpellRoster.load(id);
            assertTrue(!spell.isSet("display_name"),
                    "spells/" + id + ".yml still declares display_name, but the spell's name moved to "
                            + "spells." + prefixOf(id) + ".name in english.lang. Two sources for one string "
                            + "is how six spells ended up showing their book title as their name");
            assertTrue(!spell.isSet("description"),
                    "spells/" + id + ".yml still declares description, but the description moved to "
                            + "spells." + prefixOf(id) + ".description in english.lang");
        }
    }
}