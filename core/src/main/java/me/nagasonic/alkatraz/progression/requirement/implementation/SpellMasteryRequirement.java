package me.nagasonic.alkatraz.progression.requirement.implementation;

import me.nagasonic.alkatraz.progression.requirement.ProgressionRequirement;
import me.nagasonic.alkatraz.progression.requirement.RequirementContext;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.SpellRegistry;

import java.util.Map;

public final class SpellMasteryRequirement implements ProgressionRequirement {

    private final String spellId;
    private final int mastery;

    public SpellMasteryRequirement(String spellId, int mastery) {
        this.spellId = spellId;
        this.mastery = mastery;
    }

    public static ProgressionRequirement fromConfig(Map<String, Object> config) {
        String spell = String.valueOf(config.get("spell"));
        int mastery = readInt(config, "mastery", 0);
        return new SpellMasteryRequirement(spell, mastery);
    }

    @Override
    public boolean isMet(RequirementContext context) {
        Spell spell = SpellRegistry.getSpell(spellId);
        return spell != null && context.getProfile().getSpellMastery(spell) >= mastery;
    }

    @Override
    public String describe() {
        return "Spell Mastery " + spellId + " " + mastery;
    }

    private static int readInt(Map<String, Object> config, String key, int fallback) {
        Object value = config.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value != null) return Integer.parseInt(String.valueOf(value));
        return fallback;
    }
}
