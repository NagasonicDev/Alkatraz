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
import org.bukkit.inventory.ShapedRecipe;

import java.util.Map;

public class ShapedCraftingAdapter implements CraftingTypeAdapter {

    @Override
    public RecipeType type() {
        return RecipeType.SHAPED;
    }

    @Override
    public void registerNative(AlkatrazRecipe recipe) {
        String[] shape = recipe.getShape();
        if (shape == null || shape.length == 0) {
            Alkatraz.logWarning("Cannot register shaped recipe " + recipe.getKey() + ": empty shape");
            return;
        }
        Map<Character, Ingredient> ingredients = recipe.getIngredientMap();
        if (ingredients == null || ingredients.isEmpty()) {
            Alkatraz.logWarning("Cannot register shaped recipe " + recipe.getKey() + ": no ingredients");
            return;
        }
        ItemStack result = recipe.getResult().clone();
        result.setAmount(recipe.getResultAmount());
        ShapedRecipe bukkitRecipe = new ShapedRecipe(recipe.getKey(), result);
        bukkitRecipe.shape(shape);
        for (Map.Entry<Character, Ingredient> entry : ingredients.entrySet()) {
            bukkitRecipe.setIngredient(entry.getKey(), entry.getValue().toChoice());
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
