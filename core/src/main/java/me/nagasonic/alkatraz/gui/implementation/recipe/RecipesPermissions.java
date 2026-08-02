package me.nagasonic.alkatraz.gui.implementation.recipe;

import me.nagasonic.alkatraz.dom.Permission;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeCategory;
import org.bukkit.permissions.Permissible;

public final class RecipesPermissions {

    private RecipesPermissions() {}

    public static boolean canView(Permissible p) {
        return Permission.hasPermission(p, Permission.RECIPES_VIEW);
    }

    public static boolean canEdit(Permissible p, RecipeCategory category) {
        return Permission.hasPermission(p, Permission.RECIPES_EDIT)
                || p.hasPermission("alkatraz.recipes.edit." + category.getId().toLowerCase());
    }

    public static boolean canDelete(Permissible p) {
        return Permission.hasPermission(p, Permission.RECIPES_DELETE);
    }

    public static boolean canCreate(Permissible p) {
        return Permission.hasPermission(p, Permission.RECIPES_CREATE);
    }
}
