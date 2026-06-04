package me.nagasonic.alkatraz.spells.modifier;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Applies a {@link MagicProfile.SpellModifier} while an effect is active (players only).
 */
public class StatAppliedModifier extends AppliedModifier {

    private final String statId;
    private final double value;

    public StatAppliedModifier(String statId, double value, String description) {
        super(description);
        this.statId = statId;
        this.value = value;
    }

    @Override
    public void apply(LivingEntity entity, Spell source) {
        if (!(entity instanceof Player player)) return;
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        profile.setSpellModifier(new MagicProfile.SpellModifier(source, statId, value));
    }

    @Override
    public void remove(LivingEntity entity, Spell source) {
        if (!(entity instanceof Player player)) return;
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        if (profile.getSpellModifiers(source, statId).isEmpty()) return;
        profile.getSpellModifiers(source, statId).forEach(profile::removeSpellModifier);
    }
}
