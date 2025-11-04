package me.nagasonic.alkatraz.dom;

import org.bukkit.permissions.Permissible;

public enum Permission {

    COMMAND_SPELLS_OTHER("alkatraz.command.spells.other"),
    COMMAND_GIVE("alkatraz.command.give"),
    COMMAND_MASTERY("alkatraz.command.mastery"),
    COMMAND_EXPERIENCE("alkatraz.command.experience"),
    COMMAND_CIRCLE("alkatraz.command.cirlce"),
    COMMAND_DISCOVER("alkatraz.command.discoverspell"),
    COMMAND_UNDISCOVER("alkatraz.command.undiscoverspell"),
    COMMAND_STATS_OTHER("alkatraz.command.stats.other"),
    COMMAND_RELOAD("alkatraz.command.reload"),
    ALL_SPELLS("alkatraz.allspells");


    private final String permission;

    Permission(String permission){
        this.permission = permission;
    }

    public String getPermissionString(){
        return permission;
    }

    public static boolean hasPermission(Permissible p, Permission perm){
        return p.hasPermission(perm.getPermissionString());
    }
}
