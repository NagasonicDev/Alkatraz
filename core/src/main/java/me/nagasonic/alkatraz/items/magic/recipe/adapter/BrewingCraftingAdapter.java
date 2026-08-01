package me.nagasonic.alkatraz.items.magic.recipe.adapter;

import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

public class BrewingCraftingAdapter implements CraftingTypeAdapter {

    @Override
    public RecipeType type() {
        return RecipeType.BREWING;
    }

    @Override
    public void registerNative(AlkatrazRecipe recipe) {
        // No native Bukkit brewing recipe registry exists; brewing recipes are applied
        // via BrewEvent interception.
    }

    @Override
    public void onPrepare(PrepareItemCraftEvent event) {
    }

    @Override
    public void onBrew(BrewEvent event) {
        BrewerInventory contents = event.getContents();
        ItemStack ingredient = contents.getIngredient();
        if (ingredient == null) return;
        List<ItemStack> results = event.getResults();
        for (int i = 0; i < results.size(); i++) {
            ItemStack input = contents.getItem(i);
            if (input == null || input.getType().isAir()) continue;
            AlkatrazRecipe recipe = findRecipe(input, ingredient);
            if (recipe == null) continue;
            if (!RecipeGate.canCraft(null, recipe)) {
                event.setCancelled(true);
                return;
            }
            results.set(i, recipe.getResult().clone());
        }
    }

    private AlkatrazRecipe findRecipe(ItemStack input, ItemStack ingredient) {
        Set<NamespacedKey> keys = RecipeRegistry.getByStation(RecipeType.BREWING);
        if (keys.isEmpty()) return null;
        for (NamespacedKey key : keys) {
            AlkatrazRecipe recipe = RecipeRegistry.get(key);
            if (recipe == null) continue;
            if (recipe.getInput() != null && recipe.getAddition() != null
                    && input.isSimilar(recipe.getInput())
                    && ingredient.isSimilar(recipe.getAddition())) {
                return recipe;
            }
        }
        return null;
    }
}
