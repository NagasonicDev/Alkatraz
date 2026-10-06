package me.nagasonic.alkatraz.spells;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Reads the bundled english.lang the way LangManager does, for tests that need to assert on
 * a lang value without a running plugin: trim, skip blank and '#', split on the first '='.
 *
 * <p>Deliberately does NOT apply ColorFormat, so tests compare the raw authored text
 * ('&6Fireball') rather than the formatted text a running server would show.
 *
 * <p>Public because the spell-implementation tests in
 * me.nagasonic.alkatraz.spells.implementation assert on lang values too.
 */
public final class BundledLang {

    private BundledLang() {
    }

    public static Map<String, String> parse() {
        Map<String, String> parsed = new LinkedHashMap<>();
        try (InputStream in = BundledLang.class.getResourceAsStream("/lang/english.lang")) {
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
        } catch (IOException e) {
            throw new IllegalStateException("could not read /lang/english.lang", e);
        }
        return parsed;
    }

    /** A value equal to its own key is a miss - LangManager returns the key on a miss. */
    public static String resolve(Map<String, String> messages, String key) {
        String value = messages.get(key);
        return value == null || value.isEmpty() || value.equals(key) ? null : value;
    }
}