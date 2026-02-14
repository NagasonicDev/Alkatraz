package me.nagasonic.alkatraz.spells.configuration.requirement.implementation;

import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirement;
import org.bukkit.entity.Player;

public class OptionValueRequirement implements ValueRequirement {
    private final String optionId;
    private final String requiredValueId;
    private final String description;

    public OptionValueRequirement(String optionId, String requiredValueId) {
        this.optionId = optionId;
        this.requiredValueId = requiredValueId;
        this.description = "Requires " + optionId + " to be " + requiredValueId;
    }

    public OptionValueRequirement(String optionId, String requiredValueId, String description) {
        this.optionId = optionId;
        this.requiredValueId = requiredValueId;
        this.description = description;
    }

    @Override
    public boolean isMet(Player player) {
        MagicProfile profile = ProfileManager.getProfile(player, MagicProfile.class);
        String selectedValue = profile.getSpellOption(optionId);
        return requiredValueId.equals(selectedValue);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
