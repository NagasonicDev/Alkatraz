package me.nagasonic.alkatraz.spells.configuration.requirement.implementation;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirement;
import org.bukkit.entity.Player;

public class BooleanStatRequirement implements ValueRequirement {
    private final String statName;
    private final boolean requires;
    private final String description;

    public BooleanStatRequirement(String statName, boolean requires) {
        this.statName = statName;
        this.requires = requires;
        this.description = "Requires " + statName + " " + requires;
    }

    public BooleanStatRequirement(String statName, boolean requires, String description) {
        this.statName = statName;
        this.requires = requires;
        this.description = description;
    }

    @Override
    public boolean isMet(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        return profile.getBool(statName) == requires;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
