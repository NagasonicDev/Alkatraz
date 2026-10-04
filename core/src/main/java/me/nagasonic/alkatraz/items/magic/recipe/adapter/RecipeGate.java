package me.nagasonic.alkatraz.items.magic.recipe.adapter;

import me.nagasonic.alkatraz.api.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.configuration.requirement.RequirementFactory;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RecipeGate {

    /**
     * Permission requirements are immutable value objects, so one instance per
     * permission string is reused instead of rebuilding a HashMap, a YAML section
     * and a fresh requirement on every unmet-permission craft check.
     */
    private static final Map<String, Requirement> PERMISSION_CACHE = new ConcurrentHashMap<>();

    private RecipeGate() {}

    public static boolean canCraft(Player player, AlkatrazRecipe recipe) {
        List<Requirement> requirements = recipe.getRequirements();
        List<String> permissions = recipe.getPermissions();
        if (requirements.isEmpty() && permissions.isEmpty()) {
            return true;
        }
        if (player == null) {
            return false;
        }
        if (!requirements.isEmpty() && !UnlockManager.isUnlocked(player, recipe.getKey().toString())) {
            return false;
        }
        for (Requirement req : requirements) {
            if (!req.isMet(player)) return false;
        }
        for (String permission : permissions) {
            if (!player.hasPermission(permission)) return false;
        }
        return true;
    }

    public static List<Requirement> getUnmet(Player player, AlkatrazRecipe recipe) {
        if (recipe.getRequirements().isEmpty() && recipe.getPermissions().isEmpty()) {
            return List.of();
        }
        List<Requirement> unmet = new ArrayList<>();
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
        return PERMISSION_CACHE.computeIfAbsent(permission, key -> {
            Map<String, Object> map = new HashMap<>();
            map.put("type", "permission");
            map.put("permission", key);
            return RequirementFactory.create(null, RequirementFactory.toSection(map));
        });
    }
}
