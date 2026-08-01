package me.nagasonic.alkatraz.items.magic.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.items.magic.MagicItemService;
import me.nagasonic.alkatraz.items.magic.recipe.adapter.CraftingEventRouter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RecipeRegistry {

    private static final Map<NamespacedKey, AlkatrazRecipe> BY_KEY = new HashMap<>();
    private static final Map<Material, Set<NamespacedKey>> BY_OUTPUT_MATERIAL = new HashMap<>();
    private static final Map<RecipeType, Set<NamespacedKey>> BY_STATION = new HashMap<>();
    private static final Map<Material, Set<NamespacedKey>> BY_INGREDIENT = new HashMap<>();

    private RecipeRegistry() {}

    public static void register(AlkatrazRecipe recipe) {
        NamespacedKey key = recipe.getKey();
        if (BY_KEY.containsKey(key)) {
            Alkatraz.logWarning("Duplicate recipe key overwritten: " + key);
        }
        BY_KEY.put(key, recipe);
        BY_OUTPUT_MATERIAL.computeIfAbsent(recipe.getResult().getType(), k -> new HashSet<>()).add(key);
        BY_STATION.computeIfAbsent(recipe.getType(), k -> new HashSet<>()).add(key);
        indexIngredients(key, recipe);
    }

    public static void unregisterAll() {
        for (NamespacedKey key : BY_KEY.keySet()) {
            Bukkit.removeRecipe(key);
        }
        BY_KEY.clear();
        BY_OUTPUT_MATERIAL.clear();
        BY_STATION.clear();
        BY_INGREDIENT.clear();
    }

    public static AlkatrazRecipe get(NamespacedKey key) {
        return BY_KEY.get(key);
    }

    public static Collection<AlkatrazRecipe> getAll() {
        return BY_KEY.values();
    }

    public static Set<NamespacedKey> getAllKeys() {
        return new HashSet<>(BY_KEY.keySet());
    }

    public static List<Requirement> getRequirements(NamespacedKey key) {
        AlkatrazRecipe recipe = BY_KEY.get(key);
        if (recipe == null) return List.of();
        return recipe.getRequirements();
    }

    public static Set<NamespacedKey> getByOutputMaterial(Material material) {
        return BY_OUTPUT_MATERIAL.getOrDefault(material, Set.of());
    }

    public static Set<NamespacedKey> getByStation(RecipeType type) {
        return BY_STATION.getOrDefault(type, Set.of());
    }

    public static Set<NamespacedKey> getByIngredient(Material material) {
        return BY_INGREDIENT.getOrDefault(material, Set.of());
    }

    public static int registerNativeRecipes() {
        int count = 0;
        for (AlkatrazRecipe recipe : BY_KEY.values()) {
            if (CraftingEventRouter.getAdapter(recipe.getType()) == null) continue;
            if (recipe.isOverrideVanilla()) {
                Bukkit.removeRecipe(recipe.getKey());
            } else if (Bukkit.getRecipe(recipe.getKey()) != null) {
                Alkatraz.logWarning("Recipe key " + recipe.getKey() + " conflicts with an existing Bukkit recipe; set override_vanilla: true to replace it");
            }
            CraftingEventRouter.registerNative(recipe);
            count++;
        }
        return count;
    }

    public static void reload() {
        unregisterAll();
        MagicItemService.loadYamlDefinitions("magic/recipes", (path, config) -> {
            AlkatrazRecipe recipe = RecipeLoader.load(config);
            if (recipe != null) register(recipe);
        });
        registerNativeRecipes();
    }

    private static void indexIngredients(NamespacedKey key, AlkatrazRecipe recipe) {
        if (recipe.getIngredientMap() != null) {
            for (Ingredient ingredient : recipe.getIngredientMap().values()) {
                indexIngredient(key, ingredient);
            }
        }
        if (recipe.getIngredients() != null) {
            for (Ingredient ingredient : recipe.getIngredients()) {
                indexIngredient(key, ingredient);
            }
        }
        if (recipe.getInput() != null) {
            BY_INGREDIENT.computeIfAbsent(recipe.getInput().getType(), k -> new HashSet<>()).add(key);
        }
        if (recipe.getBase() != null) {
            BY_INGREDIENT.computeIfAbsent(recipe.getBase().getType(), k -> new HashSet<>()).add(key);
        }
        if (recipe.getAddition() != null) {
            BY_INGREDIENT.computeIfAbsent(recipe.getAddition().getType(), k -> new HashSet<>()).add(key);
        }
    }

    private static void indexIngredient(NamespacedKey key, Ingredient ingredient) {
        RecipeChoice choice = ingredient.toChoice();
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            for (Material material : materialChoice.getChoices()) {
                BY_INGREDIENT.computeIfAbsent(material, k -> new HashSet<>()).add(key);
            }
        }
    }
}
