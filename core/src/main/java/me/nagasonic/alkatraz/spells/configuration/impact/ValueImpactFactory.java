package me.nagasonic.alkatraz.spells.configuration.impact;

import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.CastModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.ManaCostImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.StatModifierImpact;
import me.nagasonic.alkatraz.spells.configuration.impact.implementation.TagImpact;
import me.nagasonic.alkatraz.spells.modifier.AppliedModifierFactory;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for all ValueImpact types.
 *
 * Every impact implementation registers itself here with a string key.
 * The SpellOptionLoader calls {@link #create} with that key and a config
 * section; no switch/if-chains on type strings live anywhere else.
 *
 * Built-in types registered at class-load time:
 *  - stat_modifier  → StatModifierImpact  (ADD / MULTIPLY / SET)
 *  - mana_cost      → ManaCostImpact
 *  - tag            → TagImpact
 */
public final class ValueImpactFactory {

    @FunctionalInterface
    public interface Builder {
        /**
         * @param spell   The parent spell.
         * @param section The YAML section describing this impact.
         * @return A fully constructed ValueImpact.
         */
        ValueImpact build(Spell spell, ConfigurationSection section);
    }

    private static final Map<String, Builder> REGISTRY = new HashMap<>();

    // ── Built-in registrations ────────────────────────────────────────────────
    static {
        /*
         * stat_modifier
         * -------------
         * stat:        <string>           — stat name used in getModifiedStat()
         * value:       <double>           — modifier value
         * modifier:    ADD|MULTIPLY|SET   — how the value is applied
         * description: <string>           — optional display text
         *
         * Modifier semantics (mirrors StatModifierImpact.ModifierType):
         *   ADD      — flat addition/subtraction to the base value
         *   MULTIPLY — multiplies base by this factor  (1.3 = +30 %)
         *   SET      — overrides base to exactly this value
         *
         * Examples:
         *   type: stat_modifier
         *   stat: tendril_speed
         *   value: 0.5
         *   modifier: SET
         *
         *   type: stat_modifier
         *   stat: damage
         *   value: 1.3
         *   modifier: MULTIPLY
         */
        register("stat_modifier", (spell, s) -> {
            String stat         = s.getString("stat", "");
            double value        = s.getDouble("value", 0);
            String modifierStr  = s.getString("modifier", "ADD").toUpperCase();
            String description  = s.getString("description", null);

            StatModifierImpact.ModifierType modifierType;
            try {
                modifierType = StatModifierImpact.ModifierType.valueOf(modifierStr);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown modifier type '" + modifierStr +
                        "' for stat_modifier impact. Valid values: ADD, MULTIPLY, SET");
            }

            if (description != null) {
                return new StatModifierImpact(spell, stat, value, modifierType, description);
            }
            return new StatModifierImpact(spell, stat, value, modifierType);
        });

        /*
         * mana_cost
         * ---------
         * change:      <int>   — flat change to mana cost (negative = cheaper)
         *
         * Example:
         *   type: mana_cost
         *   change: -15
         */
        register("mana_cost", (spell, s) -> {
            int change = s.getInt("change", 0);
            return new ManaCostImpact(spell, change);
        });

        /*
         * tag
         * ---
         * tag:         <string>  — tag string added to the player's spell tags
         * description: <string>  — display text shown in GUI
         *
         * Example:
         *   type: tag
         *   tag: piercing
         *   description: "Tendrils pierce through targets"
         */
        register("tag", (spell, s) -> {
            String tag         = s.getString("tag", "");
            String description = s.getString("description", "Adds tag: " + tag);
            return new TagImpact(spell, tag, description);
        });

        /*
         * cast_modifier
         * -------------
         * Declares a modifier applied when the spell is cast (not on option select).
         * kind: attribute | stat
         *
         * attribute kind:
         *   attribute:  GENERIC_ATTACK_DAMAGE (Attribute enum name)
         *   amount:     <double>
         *   operation:  ADD_NUMBER | ADD_SCALAR | MULTIPLY_SCALAR_1
         *
         * stat kind:
         *   stat:   <string>  — MagicProfile spell-modifier id
         *   value:  <double>
         */
        register("cast_modifier", (spell, s) ->
                new CastModifierImpact(AppliedModifierFactory.fromConfig(s)));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Registers a new impact type. Call this from your plugin's onEnable
     * (or any static initialiser) before spell options are loaded.
     *
     * @param type    Case-insensitive type key used in YAML.
     * @param builder Factory lambda that constructs the impact.
     */
    public static void register(String type, Builder builder) {
        REGISTRY.put(type.toLowerCase(), builder);
    }

    /**
     * Creates a ValueImpact from a YAML section.
     *
     * @param type    The 'type' field value from YAML.
     * @param spell   Parent spell.
     * @param section The full config section for this impact entry.
     * @return Constructed ValueImpact.
     * @throws IllegalArgumentException if the type is not registered.
     */
    public static ValueImpact create(String type, Spell spell, ConfigurationSection section) {
        Builder builder = REGISTRY.get(type.toLowerCase());
        if (builder == null) {
            throw new IllegalArgumentException(
                    "Unknown impact type '" + type + "'. " +
                    "Registered types: " + REGISTRY.keySet());
        }
        return builder.build(spell, section);
    }

    /** Returns true if a builder is registered for the given type string. */
    public static boolean isRegistered(String type) {
        return REGISTRY.containsKey(type.toLowerCase());
    }

    private ValueImpactFactory() {}
}
