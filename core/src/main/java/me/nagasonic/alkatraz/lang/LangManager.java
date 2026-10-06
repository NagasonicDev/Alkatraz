package me.nagasonic.alkatraz.lang;

import me.nagasonic.alkatraz.util.ColorFormat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LangManager {
    private final Map<String, String> messages = new HashMap<>();
    private final Map<String, String> rawMessages = new HashMap<>();
    private final String language;

    public LangManager(String language, File dataFolder) {
        this.language = language;
        // Always load bundled english as base first
        loadBundledLanguage("english");
        // Then the on-disk english override, so the extracted file is actually read.
        // Without this layer english is the one language a server owner cannot edit:
        // LangManager used to skip loadLanguage for english entirely.
        loadLanguage("english", dataFolder);
        // Then the configured language (filesystem overrides filesystem overrides bundled)
        if (!"english".equals(language)) {
            loadLanguage(language, dataFolder);
        }
    }

    private void loadLanguage(String langName, File dataFolder) {
        File langFile = new File(new File(dataFolder, "lang"), langName + ".lang");
        if (!langFile.exists()) {
            return;
        }
        // Explicit UTF-8, matching loadBundledLanguage. FileReader would silently use the
        // platform default charset, and a lang file is user-authored text.
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(langFile), StandardCharsets.UTF_8))) {
            loadFromReader(reader);
        } catch (IOException e) {
            System.err.println("[Alkatraz] Failed to load lang file: " + langFile.getName());
            e.printStackTrace();
        }
    }

    private void loadBundledLanguage(String langName) {
        InputStream is = getClass().getClassLoader().getResourceAsStream("lang/" + langName + ".lang");
        if (is == null) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            loadFromReader(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadFromReader(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            messages.put(key, ColorFormat.format(value));
            rawMessages.put(key, value);
        }
    }

    public String get(String key) {
        String template = messages.get(key);
        if (template == null) {
            return key;
        }
        return template;
    }

    public String get(String key, String placeholderName, Object placeholderValue) {
        return get(key, new Object[]{placeholderName, placeholderValue});
    }

    public String get(String key, Object... placeholders) {
        String template = messages.get(key);
        if (template == null) {
            // Fallback: return the raw key so missing translations are visible
            return key;
        }
        String result = template;
        boolean injectedCodes = false;
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String placeholder = "%" + placeholders[i] + "%";
            String replacement = String.valueOf(placeholders[i + 1]);
            if (!injectedCodes && (replacement.indexOf('&') >= 0 || replacement.indexOf('#') >= 0)) {
                injectedCodes = true;
            }
            result = result.replace(placeholder, replacement);
        }
        // Templates are pre-formatted at load time. Only a placeholder value that
        // itself carries colour codes still needs formatting; ColorFormat.format is
        // idempotent, so re-applying it here matches the previous behaviour exactly.
        if (injectedCodes) {
            result = ColorFormat.format(result);
        }
        return result;
    }

    public String getRaw(String key, Object... placeholders) {
        String template = rawMessages.get(key);
        if (template == null) {
            return key;
        }
        String result = template;
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String placeholder = "%" + placeholders[i] + "%";
            String replacement = String.valueOf(placeholders[i + 1]);
            result = result.replace(placeholder, replacement);
        }
        return result;
    }

    public String getLanguage() {
        return language;
    }
}
