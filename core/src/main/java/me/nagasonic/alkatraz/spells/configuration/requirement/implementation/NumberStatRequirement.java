package me.nagasonic.alkatraz.spells.configuration.requirement.implementation;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirement;
import org.bukkit.entity.Player;

public class NumberStatRequirement<T extends Number> implements ValueRequirement{
    private final String statName;
    private final T minimumValue;
    private final String description;

    public NumberStatRequirement(String statName, T minimumValue) {
        this.statName = statName;
        this.minimumValue = minimumValue;
        this.description = "Requires " + statName + " " + minimumValue;
    }

    public NumberStatRequirement(String statName, T minimumValue, String description) {
        this.statName = statName;
        this.minimumValue = minimumValue;
        this.description = description;
    }

    @Override
    public boolean isMet(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        if (profile.isDouble(statName)) {
            return profile.getDouble(statName) >= minimumValue.doubleValue();
        }else if (profile.isInt(statName)) {
            return profile.getInt(statName) >= minimumValue.intValue();
        }else if (profile.isFloat(statName)) {
            return profile.getFloat(statName) >= minimumValue.floatValue();
        }
        return false;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
