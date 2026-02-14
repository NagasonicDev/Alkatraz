package me.nagasonic.alkatraz.spells.configuration.impact.implementation;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpact;
import org.bukkit.entity.Player;

public class StatModifierImpact implements ValueImpact {
    public enum ModifierType {
        ADD,        // Adds a flat amount
        MULTIPLY,   // Multiplies by a percentage (1.5 = 150%)
        SET         // Sets to a specific value
    }

    private final Spell spell;
    private final String statName;
    private final double value;
    private final ModifierType type;
    private final String description;

    public StatModifierImpact(Spell spell, String statName, double value, ModifierType type) {
        this.spell = spell;
        this.statName = statName;
        this.value = value;
        this.type = type;
        this.description = generateDescription();
    }

    public StatModifierImpact(Spell spell, String statName, double value, ModifierType type, String description) {
        this.spell = spell;
        this.statName = statName;
        this.value = value;
        this.type = type;
        this.description = description;
    }

    @Override
    public void apply(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        MagicProfile.SpellModifier mod = new MagicProfile.SpellModifier(spell, statName, value);
        String modifierKey = "spell_modifier_" + statName;
        String typeKey = "spell_modifier_type_" + statName;

        // Store the modifier value and type
        profile.setSpellModifier(mod);
        profile.setSpellModifierType(mod, type.name());
    }

    @Override
    public void unapply(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        MagicProfile.SpellModifier mod = new MagicProfile.SpellModifier(spell, statName, value);
        String modifierKey = "spell_modifier_" + statName;
        String typeKey = "spell_modifier_type_" + statName;

        // Remove the modifier
        profile.removeSpellModifier(mod);
    }

    @Override
    public String getDescription() {
        return description;
    }

    private String generateDescription() {
        return switch (type) {
            case ADD -> (value > 0 ? "+" : "") + value + " " + statName;
            case MULTIPLY -> {
                int percent = (int) ((value - 1.0) * 100);
                yield (percent > 0 ? "+" : "") + percent + "% " + statName;
            }
            case SET -> statName + " set to " + value;
        };
    }

    public String getStatName() {
        return statName;
    }

    public double getValue() {
        return value;
    }

    public ModifierType getType() {
        return type;
    }
}
