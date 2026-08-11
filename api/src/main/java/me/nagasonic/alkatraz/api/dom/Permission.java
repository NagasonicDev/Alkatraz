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
    /** Permission to use the /editor command. */
    COMMAND_EDITOR("alkatraz.command.editor"),
    /** Permission to use the recipe book. */
    RECIPE_BOOK("alkatraz.recipebook"),
    /** Permission to use the /recipe unlock command. */
    COMMAND_RECIPE_UNLOCK("alkatraz.recipe.unlock"),
    /** Permission to use the /recipe lock command. */
    COMMAND_RECIPE_LOCK("alkatraz.recipe.lock"),
    /** Permission to use the /recipe reload command. */
    COMMAND_RECIPE_RELOAD("alkatraz.recipe.reload"),
    /** Permission to use the /recipe give command. */
    COMMAND_RECIPE_GIVE("alkatraz.recipe.give"),
    /** Permission to use the /recipe check command. */
    COMMAND_RECIPE_CHECK("alkatraz.recipe.check"),
    /** Permission to view recipes. */
    RECIPES_VIEW("alkatraz.recipes.view"),
    /** Permission to view locked recipes. */
    RECIPES_VIEW_LOCKED("alkatraz.recipes.view.locked"),
    /** Permission to edit recipes. */
    RECIPES_EDIT("alkatraz.recipes.edit"),
    /** Permission to delete recipes. */
    RECIPES_DELETE("alkatraz.recipes.delete"),
    /** Permission to create recipes. */
    RECIPES_CREATE("alkatraz.recipes.create"),
    /** Permission to access all spells. */
    ALL_SPELLS("alkatraz.allspells"),
    /** Permission to cast spells without a cooldown. */
    NO_COOLDOWN("alkatraz.nocooldown");


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
