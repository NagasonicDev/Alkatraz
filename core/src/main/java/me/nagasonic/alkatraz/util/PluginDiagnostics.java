package me.nagasonic.alkatraz.util;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Additive runtime/config diagnostics helper used by {@code /alkatraz diagnostics}.
 */
public final class PluginDiagnostics {

    private PluginDiagnostics() {
    }

    public static List<String> run() {
        List<String> lines = new ArrayList<>();
        lines.add("&7Plugin version: &f" + Alkatraz.getInstance().getDescription().getVersion());
        lines.add("&7Server version: &f" + Bukkit.getVersion());
        lines.add("&7NMS adapter: &f" + (Alkatraz.getNms() == null ? "none" : Alkatraz.getNms().getClass().getSimpleName()));
        lines.add("&7Loaded config files: &f" + ConfigManager.getConfigs().size());

        int spells = SpellRegistry.getAllSpellsByID().size();
        lines.add("&7Spells registered: &f" + spells);

        Set<String> duplicateCodes = new HashSet<>();
        Set<String> seenCodes = new HashSet<>();
        List<String> spellCodes = new ArrayList<>();
        for (Spell spell : SpellRegistry.getAllSpellsByID().values()) {
            String code = spell.getCode();
            if (code == null || code.isBlank()) continue;
            if (!seenCodes.add(code)) duplicateCodes.add(code);
            spellCodes.add(code);
        }
        List<String> prefixErrors = new ArrayList<>();
        List<String> sortedCodes = new ArrayList<>(spellCodes);
        java.util.Collections.sort(sortedCodes);
        for (int i = 0; i < sortedCodes.size(); i++) {
            for (int j = i + 1; j < sortedCodes.size(); j++) {
                if (!sortedCodes.get(j).startsWith(sortedCodes.get(i))) break;
                if (!sortedCodes.get(i).equals(sortedCodes.get(j))) {
                    prefixErrors.add(sortedCodes.get(i) + " -> " + sortedCodes.get(j));
                }
            }
        }
        prefixErrors = new ArrayList<>(new HashSet<>(prefixErrors));
        lines.add("&7Spell codes: unique=" + seenCodes.size() + ", duplicates=" + duplicateCodes.size()
                + ", prefix conflicts=" + prefixErrors.size());
        if (!duplicateCodes.isEmpty()) lines.add("&eDuplicate spell codes: " + String.join(", ", duplicateCodes));
        if (!prefixErrors.isEmpty()) lines.add("&eSpell code prefix conflicts: " + String.join("; ", prefixErrors));

        int configWarnings = 0;
        for (String name : ConfigManager.getConfigs().keySet()) {
            try {
                org.bukkit.configuration.file.YamlConfiguration cfg = ConfigManager.getConfig(name).get();
                if (cfg == null || cfg.getKeys(false).isEmpty()) {
                    configWarnings++;
                    lines.add("&eConfig &f" + name + "&e has no readable top-level keys");
                }
            } catch (Exception e) {
                configWarnings++;
                lines.add("&cConfig &f" + name + "&c failed to read: " + e.getMessage());
            }
        }
        lines.add("&7Config read warnings: &f" + configWarnings);

        try {
            lines.add("&7online players: &f" + Bukkit.getOnlinePlayers().size());
        } catch (Exception ignored) {
            // Defensive: no server state available.
        }
        return lines;
    }
}