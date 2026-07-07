package me.nagasonic.alkatraz.items.magic.condition.implementation;

import me.nagasonic.alkatraz.api.magic.condition.Condition;
import me.nagasonic.alkatraz.api.magic.trigger.TriggerContext;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.SpellRegistry;

import java.util.Map;

public final class HasDiscoveredSpellCondition implements Condition {
    private final String spellId;

    public HasDiscoveredSpellCondition(String spellId) {
        this.spellId = spellId;
    }

    @Override
    public boolean test(TriggerContext context) {
        return context.playerActor().map(player -> {
            var spell = SpellRegistry.getSpellFromName(spellId);
            if (spell == null) return false;
            MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
            return profile.hasDiscoveredSpell(spell);
        }).orElse(false);
    }

    public static Condition fromConfig(Map<String, Object> config) {
        return new HasDiscoveredSpellCondition(String.valueOf(config.getOrDefault("spell_id", "")));
    }
}
