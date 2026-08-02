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
    COMMAND_SPAWN_MOB("alkatraz.command.spawnmob"),
    COMMAND_EQUIPMENT("alkatraz.command.equipment"),
    COMMAND_CONVERT("alkatraz.command.convert"),
    COMMAND_PROFILE("alkatraz.command.profile"),
    COMMAND_EDITOR("alkatraz.command.editor"),
    RECIPE_BOOK("alkatraz.recipebook"),
    COMMAND_RECIPE_UNLOCK("alkatraz.recipe.unlock"),
    COMMAND_RECIPE_LOCK("alkatraz.recipe.lock"),
    COMMAND_RECIPE_RELOAD("alkatraz.recipe.reload"),
    COMMAND_RECIPE_GIVE("alkatraz.recipe.give"),
    COMMAND_RECIPE_CHECK("alkatraz.recipe.check"),
    RECIPES_VIEW("alkatraz.recipes.view"),
    RECIPES_EDIT("alkatraz.recipes.edit"),
    RECIPES_DELETE("alkatraz.recipes.delete"),
    RECIPES_CREATE("alkatraz.recipes.create"),
    ALL_SPELLS("alkatraz.allspells"),
    NO_COOLDOWN("alkatraz.nocooldown");


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
