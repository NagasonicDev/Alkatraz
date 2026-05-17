package me.nagasonic.alkatraz.spells.configuration;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpactFactory;
import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirement;
import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirementFactory;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

/**
 * Loads SpellOptions, OptionValues, ValueRequirements, and ValueImpacts
 * entirely from a YAML configuration file.
 *
 * Expected file location (inside the plugin data folder):
 *   spells/options/<spell_id>_options.yml
 *
 * All requirement and impact types are resolved through their respective
 * factory registries — no hard-coded type switches live here.
 */
public class SpellOptionLoader {

    /**
     * Reads the options file for the given spell and registers every
     * SpellOption / OptionValue found within it onto the spell.
     *
     * @param spell      The spell instance that options will be added to.
     * @param spellId    The spell's config ID (e.g. "dark_tendrils").
     */
    public static void loadOptions(Spell spell, String spellId) {
        String relativePath = "spells/" + spellId + "_options.yml";
        File file = new File(Alkatraz.getInstance().getDataFolder(), relativePath);

        if (!file.exists()) {
            // Not every spell needs an options file — this is fine.
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection optionsSection = config.getConfigurationSection("options");

        if (optionsSection == null) {
            Alkatraz.getInstance().getLogger().warning(
                    "[SpellOptionLoader] No 'options' section found in " + relativePath);
            return;
        }

        for (String optionKey : optionsSection.getKeys(false)) {
            ConfigurationSection optionSection = optionsSection.getConfigurationSection(optionKey);
            if (optionSection == null) continue;

            SpellOption option = parseOption(spell, optionKey, optionSection, relativePath);
            if (option != null) {
                spell.addOption(option);
            }
        }
    }

    // =========================================================================
    // Option parsing
    // =========================================================================

    private static SpellOption parseOption(Spell spell, String optionKey,
                                           ConfigurationSection section,
                                           String filePath) {
        String description = section.getString("description", "");
        String iconStr     = section.getString("icon", "BARRIER");
        int    defIndex    = section.getInt("default_index", 0);

        Material icon = parseMaterial(iconStr, filePath, optionKey + ".icon");
        SpellOption option = new SpellOption(spell, optionKey, description, icon, defIndex);

        ConfigurationSection valuesSection = section.getConfigurationSection("values");
        if (valuesSection == null) {
            Alkatraz.getInstance().getLogger().warning(
                    "[SpellOptionLoader] Option '" + optionKey + "' has no 'values' in " + filePath);
            return option;
        }

        for (String valueKey : valuesSection.getKeys(false)) {
            ConfigurationSection valueSection = valuesSection.getConfigurationSection(valueKey);
            if (valueSection == null) continue;

            OptionValue<?> value = parseValue(spell, valueKey, valueSection, filePath);
            if (value != null) {
                option.addValue(value);
            }
        }

        return option;
    }

    // =========================================================================
    // Value parsing
    // =========================================================================

    /**
     * Parses an OptionValue from a config section.
     *
     * The raw YAML value under 'value' is always stored as the appropriate
     * Java type (Integer, Double, Boolean, String) based on the 'value_type'
     * key. If 'value_type' is omitted the raw Object from YAML is used as-is.
     */
    private static OptionValue<?> parseValue(Spell spell, String valueKey,
                                             ConfigurationSection section,
                                             String filePath) {
        String displayName  = section.getString("display_name", valueKey);
        String description  = section.getString("description", "");
        String iconStr      = section.getString("icon", "BARRIER");
        String valueTypeStr = section.getString("value_type", "auto");

        Material icon = parseMaterial(iconStr, filePath, valueKey + ".icon");

        // Resolve the typed value
        Object rawValue = section.get("value");
        Object typedValue = coerceValue(rawValue, valueTypeStr, filePath, valueKey);

        @SuppressWarnings({"unchecked", "rawtypes"})
        OptionValue<?> optionValue = new OptionValue(valueKey, displayName, description, icon, typedValue);

        // Requirements
        List<?> requirementsList = section.getList("requirements");
        if (requirementsList != null) {
            for (Object reqObj : requirementsList) {
                ConfigurationSection reqSection;
                if (reqObj instanceof ConfigurationSection cs) {
                    reqSection = cs;
                } else if (reqObj instanceof java.util.Map<?, ?> reqMap) {
                    reqSection = mapToSection(reqMap, section, "_req_tmp");
                } else {
                    continue;
                }
                ValueRequirement req = parseRequirement(spell, reqSection, filePath, valueKey);
                if (req != null) optionValue.addRequirement(req);
            }
        }

        // Impacts
        List<?> impactsList = section.getList("impacts");
        if (impactsList != null) {
            for (Object impObj : impactsList) {
                ConfigurationSection impSection;
                if (impObj instanceof ConfigurationSection cs) {
                    impSection = cs;
                } else if (impObj instanceof java.util.Map<?, ?> impMap) {
                    impSection = mapToSection(impMap, section, "_imp_tmp");
                } else {
                    continue;
                }
                ValueImpact impact = parseImpact(spell, impSection, filePath, valueKey);
                if (impact != null) optionValue.addImpact(impact);
            }
        }

        return optionValue;
    }

    // =========================================================================
    // Requirement / Impact delegation
    // =========================================================================

    private static ValueRequirement parseRequirement(Spell spell,
                                                     ConfigurationSection section,
                                                     String filePath,
                                                     String contextKey) {
        String type = section.getString("type");
        if (type == null) {
            warn(filePath, contextKey, "Requirement entry is missing 'type'");
            return null;
        }

        try {
            return ValueRequirementFactory.create(type, spell, section);
        } catch (Exception e) {
            Alkatraz.getInstance().getLogger().log(Level.WARNING,
                    "[SpellOptionLoader] Failed to build requirement '" + type
                            + "' in " + filePath + " (" + contextKey + ")", e);
            return null;
        }
    }

    private static ValueImpact parseImpact(Spell spell,
                                           ConfigurationSection section,
                                           String filePath,
                                           String contextKey) {
        String type = section.getString("type");
        if (type == null) {
            warn(filePath, contextKey, "Impact entry is missing 'type'");
            return null;
        }

        try {
            return ValueImpactFactory.create(type, spell, section);
        } catch (Exception e) {
            Alkatraz.getInstance().getLogger().log(Level.WARNING,
                    "[SpellOptionLoader] Failed to build impact '" + type
                            + "' in " + filePath + " (" + contextKey + ")", e);
            return null;
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Coerces a raw YAML value to the desired Java type.
     * Supported value_type strings: int, integer, double, float, boolean, string, auto.
     */
    private static Object coerceValue(Object raw, String valueType, String file, String key) {
        if (raw == null) return null;
        return switch (valueType.toLowerCase()) {
            case "int", "integer" -> ((Number) raw).intValue();
            case "double"         -> ((Number) raw).doubleValue();
            case "float"          -> ((Number) raw).floatValue();
            case "boolean"        -> Boolean.parseBoolean(raw.toString());
            case "string"         -> raw.toString();
            default               -> raw; // auto — let YAML's native type stand
        };
    }

    private static Material parseMaterial(String name, String file, String key) {
        Material m = Material.matchMaterial(name.toUpperCase());
        if (m == null) {
            warn(file, key, "Unknown material '" + name + "', falling back to BARRIER");
            return Material.BARRIER;
        }
        return m;
    }

    /**
     * Bukkit's YAML list-of-maps returns LinkedHashMap entries, not
     * ConfigurationSections. This helper wraps such a map back into a
     * MemorySection so the factory methods always receive a uniform type.
     */
    @SuppressWarnings({"unchecked", "deprecation"})
    private static ConfigurationSection mapToSection(java.util.Map<?, ?> map,
                                                     ConfigurationSection parent,
                                                     String tmpKey) {
        ConfigurationSection tmp = parent.createSection(tmpKey);
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            tmp.set(entry.getKey().toString(), entry.getValue());
        }
        return tmp;
    }

    private static void warn(String file, String key, String message) {
        Alkatraz.getInstance().getLogger().warning(
                "[SpellOptionLoader] " + message + " [" + file + " > " + key + "]");
    }
}
