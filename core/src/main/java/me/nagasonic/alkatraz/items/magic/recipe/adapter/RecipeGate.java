package me.nagasonic.alkatraz.items.magic.recipe.adapter;

import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.configuration.requirement.RequirementFactory;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RecipeGate {

    private RecipeGate() {}

    public static boolean canCraft(Player player, AlkatrazRecipe recipe) {
        return getUnmet(player, recipe).isEmpty();
    }

    public static List<Requirement> getUnmet(Player player, AlkatrazRecipe recipe) {
        List<Requirement> unmet = new ArrayList<>();
        if (recipe.getRequirements().isEmpty() && recipe.getPermissions().isEmpty()) {
            return unmet;
        }
        if (player == null) {
            unmet.addAll(recipe.getRequirements());
            for (String permission : recipe.getPermissions()) {
                unmet.add(permissionRequirement(permission));
            }
            return unmet;
        }
        if (!recipe.getRequirements().isEmpty()
                && !UnlockManager.isUnlocked(player, recipe.getKey().toString())) {
            unmet.addAll(recipe.getRequirements());
            return unmet;
        }
        for (Requirement req : recipe.getRequirements()) {
            if (!req.isMet(player)) unmet.add(req);
        }
        for (String permission : recipe.getPermissions()) {
            if (!player.hasPermission(permission)) {
                unmet.add(permissionRequirement(permission));
            }
        }
        return unmet;
    }

    private static Requirement permissionRequirement(String permission) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", "permission");
        map.put("permission", permission);
        return RequirementFactory.create(null, RequirementFactory.toSection(map));
    }
}
