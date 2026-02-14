package me.nagasonic.alkatraz.spells.configuration.impact.implementation;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.impact.ValueImpact;
import org.bukkit.entity.Player;

public class TagImpact implements ValueImpact {
    private final String tag;
    private final String description;

    public TagImpact(String tag, String description) {
        this.tag = tag;
        this.description = description;
    }

    @Override
    public void apply(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        profile.addSpellTag(tag);
    }

    @Override
    public void unapply(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        profile.removeSpellTag(tag);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
