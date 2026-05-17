package me.nagasonic.alkatraz.spells.configuration.requirement;

import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.requirement.implementation.*;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for all ValueRequirement types.
 *
 * Every requirement implementation registers itself here with a string key.
 * The SpellOptionLoader calls {@link me.nagasonic.alkatraz.spells.configuration.SpellOptionLoader#parseRequirement(Spell, ConfigurationSection, String, String)} )} with that key and a config
 * section; no switch/if-chains on type strings live anywhere else.
 *
 * Built-in types registered at class-load time:
 *  - number_stat        → NumberStatRequirement
 *  - boolean_stat       → BooleanStatRequirement
 *  - permission         → PermissionRequirement
 *  - option_value       → OptionValueRequirement
 *  - composite          → CompositeRequirement
 */
public final class ValueRequirementFactory {

    @FunctionalInterface
    public interface Builder {
        /**
         * @param spell   The parent spell (may be needed for contextual requirements).
         * @param section The YAML section describing this requirement.
         * @return A fully constructed ValueRequirement.
         */
        ValueRequirement build(Spell spell, ConfigurationSection section);
    }

    private static final Map<String, Builder> REGISTRY = new HashMap<>();

    // ── Built-in registrations ────────────────────────────────────────────────
    static {
        /*
         * number_stat
         * -----------
         * stat:        <string>  — profile stat key
         * minimum:     <number>  — minimum value required
         * description: <string>  — optional display text
         *
         * Example:
         *   type: number_stat
         *   stat: mastery_dark_tendrils
         *   minimum: 50
         *   description: "Requires 50 Dark Tendrils mastery"
         */
        register("number_stat", (spell, s) -> {
            String stat        = s.getString("stat", "");
            double minimum     = s.getDouble("minimum", 0);
            String description = s.getString("description", "Requires " + stat + " >= " + minimum);
            return new NumberStatRequirement<>(stat, minimum, description);
        });

        /*
         * boolean_stat
         * ------------
         * stat:        <string>   — profile stat key
         * requires:    <boolean>  — required value (true/false)
         * description: <string>   — optional display text
         *
         * Example:
         *   type: boolean_stat
         *   stat: has_dark_affinity
         *   requires: true
         */
        register("boolean_stat", (spell, s) -> {
            String stat        = s.getString("stat", "");
            boolean requires   = s.getBoolean("requires", true);
            String description = s.getString("description", "Requires " + stat + " = " + requires);
            return new BooleanStatRequirement(stat, requires, description);
        });

        /*
         * permission
         * ----------
         * permission:  <string>  — Bukkit permission node
         * description: <string>  — optional display text
         *
         * Example:
         *   type: permission
         *   permission: alkatraz.vip
         */
        register("permission", (spell, s) -> {
            String perm        = s.getString("permission", "");
            String description = s.getString("description", "Requires permission: " + perm);
            return new PermissionRequirement(perm, description);
        });

        /*
         * option_value
         * ------------
         * option:      <string>  — full option key (e.g. "dark_tendrils.speed")
         * value:       <string>  — required selected value id
         * description: <string>  — optional display text
         *
         * Example:
         *   type: option_value
         *   option: dark_tendrils.speed
         *   value: fast
         */
        register("option_value", (spell, s) -> {
            String optionId        = s.getString("option", "");
            String requiredValueId = s.getString("value", "");
            String description     = s.getString("description",
                    "Requires " + optionId + " to be " + requiredValueId);
            return new OptionValueRequirement(optionId, requiredValueId, description);
        });

        /*
         * composite
         * ---------
         * description:  <string>  — display text for the whole group
         * requirements: <list>    — nested requirement entries (same format as top-level)
         *
         * Example:
         *   type: composite
         *   description: "Requires mastery 100 AND Circle Level 4"
         *   requirements:
         *     - type: number_stat
         *       stat: mastery_dark_tendrils
         *       minimum: 100
         *     - type: number_stat
         *       stat: circleLevel
         *       minimum: 4
         */
        register("composite", (spell, s) -> {
            String description = s.getString("description", "Multiple requirements");
            java.util.List<?> nested = s.getList("requirements");
            if (nested == null || nested.isEmpty()) {
                return new CompositeRequirement(description);
            }

            ValueRequirement[] children = nested.stream()
                    .filter(o -> o instanceof java.util.Map<?, ?>)
                    .map(o -> {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> m = (java.util.Map<String, Object>) o;
                        ConfigurationSection child = s.createSection("_composite_tmp_" + o.hashCode());
                        m.forEach((k, v) -> child.set(k, v));
                        String childType = child.getString("type", "");
                        Builder builder = REGISTRY.get(childType);
                        if (builder == null) return null;
                        return builder.build(spell, child);
                    })
                    .filter(java.util.Objects::nonNull)
                    .toArray(ValueRequirement[]::new);

            return new CompositeRequirement(description, children);
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Registers a new requirement type. Call this from your plugin's onEnable
     * (or any static initialiser) before spell options are loaded.
     *
     * @param type    Case-insensitive type key used in YAML.
     * @param builder Factory lambda that constructs the requirement.
     */
    public static void register(String type, Builder builder) {
        REGISTRY.put(type.toLowerCase(), builder);
    }

    /**
     * Creates a ValueRequirement from a YAML section.
     *
     * @param type    The 'type' field value from YAML.
     * @param spell   Parent spell.
     * @param section The full config section for this requirement entry.
     * @return Constructed ValueRequirement.
     * @throws IllegalArgumentException if the type is not registered.
     */
    public static ValueRequirement create(String type, Spell spell, ConfigurationSection section) {
        Builder builder = REGISTRY.get(type.toLowerCase());
        if (builder == null) {
            throw new IllegalArgumentException(
                    "Unknown requirement type '" + type + "'. " +
                    "Registered types: " + REGISTRY.keySet());
        }
        return builder.build(spell, section);
    }

    /** Returns true if a builder is registered for the given type string. */
    public static boolean isRegistered(String type) {
        return REGISTRY.containsKey(type.toLowerCase());
    }

    private ValueRequirementFactory() {}
}
