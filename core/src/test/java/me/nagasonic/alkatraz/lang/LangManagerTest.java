package me.nagasonic.alkatraz.lang;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * LangManager resolves every player-facing string, so its layering order is load-bearing:
 * bundled english is the base, an on-disk english.lang edits that base, and a configured
 * non-english file edits both. Without the on-disk english layer the bundled file is the
 * only readable copy and no server owner can translate anything without hand-writing a
 * file the loader never consults.
 *
 * <p>Zero Bukkit mocks: LangManager takes a File, so a JUnit @TempDir is the whole
 * fixture. Colour codes are pre-formatted at load time, so expected values use the
 * section sign.
 */
class LangManagerTest {

    /** Bundled english, read from the jar. Every base value must survive when the file is absent. */
    @Test
    void bundledEnglishIsTheBaseWhenNoOnDiskFileExists(@TempDir Path dataFolder) {
        LangManager lang = new LangManager("english", dataFolder.toFile());

        assertEquals("\u00A7eScroll of Apollo \u00A7oII", lang.get("spells.fireblast.book_name"));
    }

    /** The on-disk english.lang must win over the bundled copy - that is the whole point of extracting it. */
    @Test
    void onDiskEnglishOverridesTheBundledCopy(@TempDir Path dataFolder) throws IOException {
        writeLang(dataFolder, "english", "spells.fireblast.book_name = &4Red Book of Fire\n");

        LangManager lang = new LangManager("english", dataFolder.toFile());

        assertEquals("\u00A74Red Book of Fire", lang.get("spells.fireblast.book_name"));
        // Untouched keys must still resolve, or the on-disk file silently blanks half the plugin.
        assertEquals("\u00A7eScroll of the Apollo \u00A7oI", lang.get("spells.fireball.book_name"));
    }

    /** A partial translation keeps English for every key it omits. */
    @Test
    void aPartialTranslationFallsBackToEnglish(@TempDir Path dataFolder) throws IOException {
        writeLang(dataFolder, "german", "spells.fireblast.book_name = &4Feuerball\n");

        LangManager lang = new LangManager("german", dataFolder.toFile());

        assertEquals("\u00A74Feuerball", lang.get("spells.fireblast.book_name"));
        assertEquals("\u00A7eScroll of the Apollo \u00A7oI", lang.get("spells.fireball.book_name"));
    }

    private static void writeLang(Path dataFolder, String name, String contents) throws IOException {
        Path dir = dataFolder.resolve("lang");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name + ".lang"), contents, StandardCharsets.UTF_8);
    }
}