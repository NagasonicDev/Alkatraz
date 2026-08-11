package me.nagasonic.alkatraz.gui.implementation.recipe;

import me.nagasonic.alkatraz.api.dom.Permission;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeCategory;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockManager;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;

public final class RecipesPermissions {

    private RecipesPermissions() {}

    public static boolean canView(Permissible p) {
        return Permission.hasPermission(p, Permission.RECIPES_VIEW);
    }

    public static boolean canSee(Player p, AlkatrazRecipe recipe) {
        if (!recipe.isHiddenWhenLocked()) return true;
        if (Permission.hasPermission(p, Permission.RECIPES_VIEW_LOCKED)) return true;
        if (canEdit(p, RecipeCategory.of(recipe.getType()))) return true;
        return UnlockManager.isUnlocked(p, recipe.getKey().toString());
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
