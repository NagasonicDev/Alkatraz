package me.nagasonic.alkatraz.lang;

import me.nagasonic.alkatraz.util.ColorFormat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LangManager {
    private final Map<String, String> messages = new HashMap<>();
    private final String language;

    public LangManager(String language) {
        this.language = language;
        // Always load bundled english as base first
        loadBundledLanguage("english");
        // Then load the configured language (filesystem overrides bundled)
        if (!"english".equals(language)) {
            loadLanguage(language);
        }
    }

    private void loadLanguage(String langName) {
        File langDir = new File("plugins/Alkatraz/lang");
        File langFile = new File(langDir, langName + ".lang");
        if (!langFile.exists()) {
            return;
        }
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
            messages.put(key, value);
        }
    }

    public String get(String key, Object... placeholders) {
        String template = messages.get(key);
        if (template == null) {
            // Fallback: return the raw key so missing translations are visible
            return key;
        }
        String result = template;
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String placeholder = "%" + placeholders[i] + "%";
            String replacement = String.valueOf(placeholders[i + 1]);
            result = result.replace(placeholder, replacement);
        }
        return ColorFormat.format(result);
    }

    public String getRaw(String key, Object... placeholders) {
        String template = messages.get(key);
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
