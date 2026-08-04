package me.nagasonic.alkatraz.api.dom;

import org.bukkit.permissions.Permissible;

/**
 * Enum of all permission nodes used by the Alkatraz plugin.
 * Each constant holds the corresponding Bukkit permission string.
 */
public enum Permission {

    /** Permission to cast spells on other players. */
    COMMAND_SPELLS_OTHER("alkatraz.command.spells.other"),
    /** Permission to use the /give command. */
    COMMAND_GIVE("alkatraz.command.give"),
    /** Permission to use the /mastery command. */
    COMMAND_MASTERY("alkatraz.command.mastery"),
    /** Permission to use the /experience command. */
    COMMAND_EXPERIENCE("alkatraz.command.experience"),
    /** Permission to use the /circle command. */
    COMMAND_CIRCLE("alkatraz.command.cirlce"),
    /** Permission to use the /discoverspell command. */
    COMMAND_DISCOVER("alkatraz.command.discoverspell"),
    /** Permission to use the /undiscoverspell command. */
    COMMAND_UNDISCOVER("alkatraz.command.undiscoverspell"),
    /** Permission to view stats of other players. */
    COMMAND_STATS_OTHER("alkatraz.command.stats.other"),
    /** Permission to use the /reload command. */
    COMMAND_RELOAD("alkatraz.command.reload"),
    /** Permission to use the /summon command. */
    COMMAND_SUMMON("alkatraz.command.summon"),
    /** Permission to use the /convert command. */
    COMMAND_CONVERT("alkatraz.command.convert"),
    /** Permission to use the /equipment command. */
    COMMAND_EQUIPMENT("alkatraz.command.equipment"),
    /** Permission to use the /profile command. */
    COMMAND_PROFILE("alkatraz.command.profile"),
    /** Permission to access all spells. */
    ALL_SPELLS("alkatraz.allspells");


    private final String permission;

    Permission(String permission){
        this.permission = permission;
    }

    /**
     * Returns the Bukkit permission string for this permission node.
     *
     * @return the permission string
     */
    public String getPermissionString(){
        return permission;
    }

    /**
     * Checks whether a {@link Permissible} has the given permission.
     *
     * @param p    the permissible to check
     * @param perm the permission to check for
     * @return {@code true} if the permissible has the permission, {@code false} otherwise
     */
    public static boolean hasPermission(Permissible p, Permission perm){
        return p.hasPermission(perm.getPermissionString());
    }
}
