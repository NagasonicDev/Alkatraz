package me.nagasonic.alkatraz.spells.configuration;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.gui.Menu;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpactFactory;
import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirement;
import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirementFactory;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * @param spell   The spell instance that options will be added to.
     * @param spellId The spell's config ID (e.g. "dark_tendrils").
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

        Map<String, ConfigurationSection> optionSections = new LinkedHashMap<>();
        Map<String, SpellOption> parsed = new LinkedHashMap<>();

        for (String optionKey : optionsSection.getKeys(false)) {
            ConfigurationSection optionSection =
                    optionsSection.getConfigurationSection(optionKey);
            if (optionSection == null) continue;

            optionSections.put(optionKey, optionSection);
            SpellOption option = parseOption(spell, optionKey, optionSection, relativePath);
            if (option != null) {
                parsed.put(optionKey, option);
            }
        }

        // Second pass: copy values from a pool option when requested.
        for (Map.Entry<String, SpellOption> entry : parsed.entrySet()) {
            ConfigurationSection section = optionSections.get(entry.getKey());
            String inheritFrom = section.getString("inherit_values_from", null);
            if (inheritFrom == null || inheritFrom.isBlank()) continue;

            SpellOption source = parsed.get(inheritFrom);
            if (source == null) {
                warn(relativePath, entry.getKey(),
                        "inherit_values_from references unknown option '" + inheritFrom + "'");
                continue;
            }

            for (OptionValue<?> poolValue : source.getOptionValues()) {
                if (entry.getValue().getValueById(poolValue.getId()).isEmpty()) {
                    entry.getValue().addValue(cloneValue(poolValue));
                }
            }
        }

        parsed.values().forEach(spell::addOption);
    }

    // =========================================================================
    // Option parsing
    // =========================================================================

    private static SpellOption parseOption(Spell spell,
                                           String optionKey,
                                           ConfigurationSection section,
                                           String filePath) {
        String description = section.getString("description", "");
        String displayName = section.getString("display_name", description);
        String iconStr     = section.getString("icon", "BARRIER");
        int    defIndex    = section.getInt("default_index", 0);

        Material icon = parseMaterial(iconStr, filePath, optionKey + ".icon");
        SpellOption option = new SpellOption(spell, optionKey, description, icon, defIndex);
        option.setDisplayName(displayName);

        // ----- Role / slot metadata (for pooled slot groups) -----
        String roleStr = section.getString("option_role", "normal");
        option.setRole(parseOptionRole(roleStr, filePath, optionKey));
        option.setSlotIndex(section.getInt("slot_index", 0));
        option.setMenuSlots(section.getInt("menu_slots", 0));

        // ----- Custom-menu fields -----
        boolean useCustomMenu = section.getBoolean("use_custom_menu", false);
        String  customMenu    = section.getString("custom_menu", null);
        option.setUseCustomMenu(useCustomMenu);
        if (customMenu != null && !customMenu.isBlank()) {
            option.setCustomMenuClass(customMenu);
        }

        // ----- Grouping / visibility fields -----
        boolean hidden = section.getBoolean("hidden", false);
        String  group  = section.getString("group", null);
        option.setHidden(hidden);
        if (group != null && !group.isBlank()) {
            option.setGroup(group);
        }

        parseRequirementsList(spell, section.getList("requirements"), option, filePath, optionKey);
        parseImpactsList(spell, section.getList("impacts"), option, filePath, optionKey);

        // ----- Values -----
        ConfigurationSection valuesSection = section.getConfigurationSection("values");
        if (valuesSection == null) {
            // An option with use_custom_menu=true may legitimately have no
            // values (the custom menu manages its own data), so only warn
            // when it is a normal option.
            if (!useCustomMenu) {
                Alkatraz.getInstance().getLogger().warning(
                        "[SpellOptionLoader] Option '" + optionKey
                                + "' has no 'values' in " + filePath);
            }
            return option;
        }

        for (String valueKey : valuesSection.getKeys(false)) {
            ConfigurationSection valueSection =
                    valuesSection.getConfigurationSection(valueKey);
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
     * The raw YAML value under {@code value} is always stored as the
     * appropriate Java type (Integer, Double, Boolean, String) based on the
     * {@code value_type} key.  If {@code value_type} is omitted the raw
     * Object from YAML is used as-is.
     */
    private static OptionValue<?> parseValue(Spell spell,
                                             String valueKey,
                                             ConfigurationSection section,
                                             String filePath) {
        String displayName  = section.getString("display_name", valueKey);
        String description  = section.getString("description", "");
        String iconStr      = section.getString("icon", "BARRIER");
        String valueTypeStr = section.getString("value_type", "auto");

        Material icon = parseMaterial(iconStr, filePath, valueKey + ".icon");

        Object rawValue   = section.get("value");
        Object typedValue = coerceValue(rawValue, valueTypeStr, filePath, valueKey);

        @SuppressWarnings({"unchecked", "rawtypes"})
        OptionValue<?> optionValue =
                new OptionValue(valueKey, displayName, description, icon, typedValue);

        parseRequirementsList(spell, section.getList("requirements"), optionValue, filePath, valueKey);
        parseImpactsList(spell, section.getList("impacts"), optionValue, filePath, valueKey);

        return optionValue;
    }

    private static void parseRequirementsList(Spell spell,
                                              List<?> requirementsList,
                                              SpellOption option,
                                              String filePath,
                                              String contextKey) {
        if (requirementsList == null) return;
        YamlConfiguration tmp = new YamlConfiguration();
        for (Object reqObj : requirementsList) {
            ConfigurationSection reqSection = asConfigSection(reqObj, tmp);
            if (reqSection == null) continue;
            ValueRequirement req = parseRequirement(spell, reqSection, filePath, contextKey);
            if (req != null) option.addRequirement(req);
        }
    }

    private static void parseRequirementsList(Spell spell,
                                              List<?> requirementsList,
                                              OptionValue<?> optionValue,
                                              String filePath,
                                              String contextKey) {
        if (requirementsList == null) return;
        YamlConfiguration tmp = new YamlConfiguration();
        for (Object reqObj : requirementsList) {
            ConfigurationSection reqSection = asConfigSection(reqObj, tmp);
            if (reqSection == null) continue;
            ValueRequirement req = parseRequirement(spell, reqSection, filePath, contextKey);
            if (req != null) optionValue.addRequirement(req);
        }
    }

    private static void parseImpactsList(Spell spell,
                                         List<?> impactsList,
                                         SpellOption option,
                                         String filePath,
                                         String contextKey) {
        if (impactsList == null) return;
        YamlConfiguration tmp = new YamlConfiguration();
        for (Object impObj : impactsList) {
            ConfigurationSection impSection = asConfigSection(impObj, tmp);
            if (impSection == null) continue;
            ValueImpact impact = parseImpact(spell, impSection, filePath, contextKey);
            if (impact != null) option.addImpact(impact);
        }
    }

    private static void parseImpactsList(Spell spell,
                                         List<?> impactsList,
                                         OptionValue<?> optionValue,
                                         String filePath,
                                         String contextKey) {
        if (impactsList == null) return;
        YamlConfiguration tmp = new YamlConfiguration();
        for (Object impObj : impactsList) {
            ConfigurationSection impSection = asConfigSection(impObj, tmp);
            if (impSection == null) continue;
            ValueImpact impact = parseImpact(spell, impSection, filePath, contextKey);
            if (impact != null) optionValue.addImpact(impact);
        }
    }

    private static ConfigurationSection asConfigSection(Object obj, YamlConfiguration tmp) {
        if (obj instanceof ConfigurationSection cs) {
            return cs;
        }
        if (obj instanceof java.util.Map<?, ?> map) {
            return mapToSection(map, tmp.createSection("tmp"), "entry");
        }
        return null;
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
     * Supported value_type strings: int, integer, double, float, boolean,
     * string, auto.
     */
    private static Object coerceValue(Object raw, String valueType,
                                      String file, String key) {
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
     * ConfigurationSections.  This helper wraps such a map back into a
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

    private static SpellOption.OptionRole parseOptionRole(String roleStr,
                                                          String filePath,
                                                          String optionKey) {
        return switch (roleStr.toLowerCase()) {
            case "pool" -> SpellOption.OptionRole.POOL;
            case "slot" -> SpellOption.OptionRole.SLOT;
            case "normal" -> SpellOption.OptionRole.NORMAL;
            default -> {
                warn(filePath, optionKey,
                        "Unknown option_role '" + roleStr + "', using NORMAL");
                yield SpellOption.OptionRole.NORMAL;
            }
        };
    }

    /**
     * Shallow-clones an option value so slot options can share pool definitions.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static OptionValue<?> cloneValue(OptionValue<?> source) {
        OptionValue clone = new OptionValue(
                source.getId(),
                source.getDisplayName(),
                source.getDescription(),
                source.getIcon(),
                source.getValue());
        source.getRequirements().forEach(clone::addRequirement);
        source.getImpacts().forEach(clone::addImpact);
        return clone;
    }

    private static void warn(String file, String key, String message) {
        Alkatraz.getInstance().getLogger().warning(
                "[SpellOptionLoader] " + message + " [" + file + " > " + key + "]");
    }
}
