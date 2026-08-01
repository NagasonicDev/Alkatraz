package me.nagasonic.alkatraz.items.magic.recipe.adapter;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.Ingredient;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.List;

public class ShapelessCraftingAdapter implements CraftingTypeAdapter {

    @Override
    public RecipeType type() {
        return RecipeType.SHAPELESS;
    }

    @Override
    public void registerNative(AlkatrazRecipe recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            Alkatraz.logWarning("Cannot register shapeless recipe " + recipe.getKey() + ": no ingredients");
            return;
        }
        ItemStack result = recipe.getResult().clone();
        result.setAmount(recipe.getResultAmount());
        ShapelessRecipe bukkitRecipe = new ShapelessRecipe(recipe.getKey(), result);
        for (Ingredient ingredient : ingredients) {
            bukkitRecipe.addIngredient(ingredient.toChoice());
        }
        Bukkit.addRecipe(bukkitRecipe);
    }

    @Override
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof Keyed keyed)) return;
        AlkatrazRecipe recipe = RecipeRegistry.get(keyed.getKey());
        if (recipe == null) return;
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        if (!RecipeGate.canCraft(player, recipe)) {
            event.getInventory().setResult(null);
        }
    }
}
