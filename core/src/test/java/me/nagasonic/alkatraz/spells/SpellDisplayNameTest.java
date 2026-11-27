package me.nagasonic.alkatraz.spells;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A spell's NAME is the name of the SPELL. It is a separate key from
 * spells.&lt;prefix&gt;.book_name, which is the name of the physical book item the spell is
 * learned from - "Record of Brunhild" for firewall, "Scroll of Apollo" for fireblast.
 * Both live in the same per-spell block of english.lang and feed different code paths:
 * the name is read once in Spell.loadCommonConfig and reaches the /spells GUI, the
 * mastery bar and the wand lore, while book_name is read per spell from each
 * getSpellBook().
 *
 * <p>Some spells had the book's flavour title pasted into their name, so the GUI showed
 * "Scroll of the First Ray" where the spell is called light_bolt. Both now live in
 * english.lang, which is what makes the collision visible in one file instead of two.
 * This test pins the convention: the name is the id spelled as words, compared with
 * whitespace removed on both sides, so "Air Blades" and air_blades agree.
 *
 * <p>Colour codes are stripped before the comparison because the colour is free choice -
 * earth_spike carries a bare #A0522D where the other spells carry an ampersand code - and
 * only the words are under test here.
 *
 * <p>Zero Bukkit mocks: the lang file is parsed with LangManager's own rules and the
 * roster comes from BundledSpellRoster, the same helpers SpellLangTest uses.
 */
class SpellDisplayNameTest {

    private static final Pattern AMPERSAND_COLOR = Pattern.compile("&[0-9a-fk-orA-FK-ORxX]");
    private static final Pattern HEX_COLOR = Pattern.compile("&?#[0-9a-fA-F]{6}");

    // The six elemental circle-9 capstones keep their ids stable but carry curated display
    // names, so the id-as-words rule below does not apply to them. world_gate is not one.
    private static final Set<String> CURATED_NAMES = Set.of(
            "atlasunbound", "covenantofthefirstdawn", "empyreanpassage", "hollowkingscourt",
            "leviathanstestament", "starfallcataclysm");

    private static List<String> roster;
    private static Map<String, String> messages;

    @BeforeAll
    static void loadLangAndRoster() {
        Assumptions.assumeTrue(BundledSpellRoster.rosterIsDiscoverable(),
                "the spells resource directory is not enumerable on this classpath");
        roster = BundledSpellRoster.spellIds();
        messages = BundledLang.parse();
    }

    /** The id as one unspaced lowercase word: air_blades -> airblades. */
    private static String nameOf(String id) {
        return id.replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static String stripColor(String text) {
        String stripped = HEX_COLOR.matcher(text).replaceAll("");
        return AMPERSAND_COLOR.matcher(stripped).replaceAll("");
    }

    /** Strip colour and whitespace, so only the letters of the name are compared. */
    private static String comparable(String text) {
        return stripColor(text).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    @Test
    void everySpellNamesItselfAfterItsId() {
        for (String id : roster) {
            String key = "spells." + id.replace("_", "") + ".name";
            String name = messages.get(key);
            assertNotNull(name,
                    key + " is missing from english.lang, so Spell.loadCommonConfig leaves the spell "
                            + "nameless in the /spells GUI");
            if (CURATED_NAMES.contains(id.replace("_", ""))) {
                continue;
            }
            assertEquals(nameOf(id), comparable(name),
                    key + " calls itself '" + name + "'. A spell's name is the SPELL's name and must read "
                            + "as the id; the book's own title is spells." + id.replace("_", "") + ".book_name "
                            + "and is a different key. light_bolt carried its book_name here, "
                            + "and some spells carried their Java class name");
        }
    }

    /** The capstone exception above is pinned so it cannot hide an accidental rename. */
    @Test
    void circleNineCapstonesUseTheirCuratedNames() {
        Map<String, String> expected = Map.of(
                "atlasunbound", "&2Primordial Bastion",
                "covenantofthefirstdawn", "&eEmpyrean Ascendance",
                "empyreanpassage", "&fCelestial Expanse",
                "hollowkingscourt", "&5Umbral Oblivion",
                "leviathanstestament", "&9Abyssal Dominion",
                "starfallcataclysm", "&cInfernal Sovereignty");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), messages.get("spells." + entry.getKey() + ".name"));
        }
    }
}