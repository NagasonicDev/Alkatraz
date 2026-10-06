package me.nagasonic.alkatraz.spells;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * english.lang is a flat key = value format with no line continuations, so a multi-line
 * spell description is one value whose lines are separated by the literal two characters
 * backslash-n. Spell.loadCommonConfig splits on it, and this test pins that split.
 *
 * <p>The escape count is the whole difficulty and the reason this is tested at all: the
 * Java source needs "\\\\n" so the regex the compiler sees is \\n, which matches a
 * literal backslash followed by n. Writing "\\n" instead produces a regex that matches
 * a real newline, which no lang file contains, so every spell would collapse to a single
 * lore line and nothing would look broken.
 *
 * <p>Zero Bukkit mocks: splitLangDescription is a pure static.
 */
class SpellLangDescriptionTest {

    @Test
    void aSingleLineDescriptionStaysOneLine() {
        assertEquals(List.of("&7A small ball of fire."), Spell.splitLangDescription("&7A small ball of fire."));
    }

    @Test
    void escapedNewlinesSplitIntoLoreLines() {
        List<String> lines = Spell.splitLangDescription("&7A small ball of fire that can be shot\\n&7at an enemy.");
        assertEquals(2, lines.size(), "the escaped newline must produce one lore line each");
        assertEquals("&7A small ball of fire that can be shot", lines.get(0));
        assertEquals("&7at an enemy.", lines.get(1));
    }

    @Test
    void anAbsentDescriptionYieldsNoLinesRatherThanNull() {
        // Spell.loadCommonConfig calls this when lang returns the key back, which is how
        // LangManager signals a miss. A null here would NPE the lore loops in SpellsMenu
        // and Spellbook.
        assertTrue(Spell.splitLangDescription(null).isEmpty());
        assertTrue(Spell.splitLangDescription("").isEmpty());
    }
}