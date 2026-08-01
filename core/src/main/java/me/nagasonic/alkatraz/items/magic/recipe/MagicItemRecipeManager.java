package me.nagasonic.alkatraz.items.magic.recipe;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.definition.ItemDefinition;
import me.nagasonic.alkatraz.api.magic.instance.MagicItemInstance;
import me.nagasonic.alkatraz.api.magic.modifier.EngravingDefinition;
import me.nagasonic.alkatraz.api.magic.registry.MagicItemRegistries;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.items.magic.imbue.ImbueManager;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.List;
import java.util.Locale;

public final class MagicItemRecipeManager {

    private MagicItemRecipeManager() {}

    public static int registerRecipes() {
        return RecipeRegistry.registerNativeRecipes();
    }

    public static int registerImbuingRecipes() {
        NamespacedKey stoneDefKey = MagicKeys.alkatraz("magic_stone");
        ItemDefinition stoneDef = MagicItemRegistries.ITEM_DEFINITIONS.get(stoneDefKey).orElse(null);
        if (stoneDef == null) {
            Alkatraz.logWarning("Cannot register imbuing recipes: magic_stone definition not found");
            return 0;
        }
        ItemStack magicStone = MagicItemStack.create(stoneDef, MagicItemInstance.createDefault(stoneDefKey));

        int count = 0;
        for (Material mat : ImbueManager.getImbuableMaterials()) {
            int stoneCount = ImbueManager.getStoneCount(mat);
            NamespacedKey recipeKey = new NamespacedKey(Alkatraz.getInstance(), "imbue_" + mat.name().toLowerCase(Locale.ROOT));

            if (Bukkit.getRecipe(recipeKey) != null) continue;

            ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, new ItemStack(mat));
            recipe.addIngredient(new RecipeChoice.MaterialChoice(mat));
            for (int i = 0; i < stoneCount; i++) {
                recipe.addIngredient(new RecipeChoice.ExactChoice(magicStone.clone()));
            }
            Bukkit.addRecipe(recipe);
            count++;
        }
        Alkatraz.logInfo("Registered " + count + " imbuing recipes.");
        return count;
    }

    public static void unregisterAll() {
        RecipeRegistry.unregisterAll();
        for (Material mat : ImbueManager.getImbuableMaterials()) {
            NamespacedKey recipeKey = new NamespacedKey(Alkatraz.getInstance(), "imbue_" + mat.name().toLowerCase(Locale.ROOT));
            Bukkit.removeRecipe(recipeKey);
        }
    }

    public static void registerEngravingRecipe(EngravingDefinition definition, ConfigurationSection config) {
        ConfigurationSection recipeSection = config.getConfigurationSection("recipe");
        if (recipeSection == null) return;

        NamespacedKey key = definition.getKey();
        ItemStack result = MagicItemStack.createEngravingItem(definition);
        AlkatrazRecipe recipe = RecipeLoader.load(recipeSection, key, result);
        if (recipe != null) {
            RecipeRegistry.register(recipe);
        }
    }

    public static void registerItemRecipe(ConfigurationSection config) {
        AlkatrazRecipe recipe = RecipeLoader.load(config);
        if (recipe != null) {
            RecipeRegistry.register(recipe);
        }
    }

    public static List<Requirement> getRequirements(NamespacedKey recipeKey) {
        return RecipeRegistry.getRequirements(recipeKey);
    }
}
