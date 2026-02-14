package me.nagasonic.alkatraz.spells.configuration.requirement.implementation;

import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirement;
import org.bukkit.entity.Player;

public class CompositeRequirement implements ValueRequirement {
    private final ValueRequirement[] requirements;
    private final String description;

    public CompositeRequirement(String description, ValueRequirement... requirements) {
        this.requirements = requirements;
        this.description = description;
    }

    @Override
    public boolean isMet(Player player) {
        for (ValueRequirement req : requirements) {
            if (!req.isMet(player)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
