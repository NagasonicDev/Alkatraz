package me.nagasonic.alkatraz.spells.configuration.impact.implementation;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.Spell;
import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpact;
import org.bukkit.entity.Player;

public class ManaCostImpact implements ValueImpact {
    private final int costChange;
    private final Spell parent;
    private final MagicProfile.SpellModifier spellModifier;

    public ManaCostImpact(Spell spell, int costChange) {
        this.costChange = costChange;
        this.parent = spell;
        this.spellModifier = new MagicProfile.SpellModifier(parent, "mana_cost", costChange);
    }

    @Override
    public void apply(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        profile.setSpellModifier(spellModifier);
    }

    @Override
    public void unapply(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        profile.removeSpellModifier(spellModifier);
    }

    @Override
    public String getDescription() {
        return (costChange > 0 ? "+" : "") + costChange + " mana cost";
    }
}
