package me.nagasonic.alkatraz.spells.configuration.requirement.implementation;

import me.nagasonic.alkatraz.spells.configuration.requirement.ValueRequirement;
import org.bukkit.entity.Player;

public class PermissionRequirement implements ValueRequirement {
    private final String permission;
    private final String description;

    public PermissionRequirement(String permission) {
        this.permission = permission;
        this.description = "Requires permission: " + permission;
    }

    public PermissionRequirement(String permission, String description) {
        this.permission = permission;
        this.description = description;
    }

    @Override
    public boolean isMet(Player player) {
        return player.hasPermission(permission);
    }

    @Override
    public String getDescription() {
        return description;
    }
}
