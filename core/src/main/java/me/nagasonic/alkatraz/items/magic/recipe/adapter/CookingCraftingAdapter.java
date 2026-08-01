package me.nagasonic.alkatraz.items.magic.recipe.adapter;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmokingRecipe;

import java.util.Locale;

public class CookingCraftingAdapter implements CraftingTypeAdapter {

    @Override
    public RecipeType type() {
        return RecipeType.FURNACE;
    }

    @Override
    public void onPrepare(PrepareItemCraftEvent event) {
    }

    @Override
    public void registerNative(AlkatrazRecipe recipe) {
        if (recipe.getInput() == null) {
            Alkatraz.logWarning("Cannot register cooking recipe " + recipe.getKey() + ": no input");
            return;
        }
        if (!RecipeGate.canCraft(null, recipe)) {
            Alkatraz.logWarning("Cannot register cooking recipe " + recipe.getKey()
                    + ": gated recipes (requirements/permissions) do not smelt on this API "
                    + "(FurnaceStartSmeltEvent is not cancellable in spigot-api 1.19)");
            return;
        }
        ItemStack result = recipe.getResult().clone();
        result.setAmount(recipe.getResultAmount());
        RecipeChoice input = new RecipeChoice.ExactChoice(recipe.getInput());
        float experience = (float) recipe.getExperience();
        int cookingTime = recipe.getCookingTime() > 0 ? recipe.getCookingTime() : 200;
        switch (recipe.getType()) {
            case BLAST_FURNACE ->
                    Bukkit.addRecipe(new BlastingRecipe(recipe.getKey(), result, input, experience, cookingTime));
            case SMOKER ->
                    Bukkit.addRecipe(new SmokingRecipe(recipe.getKey(), result, input, experience, cookingTime));
            case CAMPFIRE ->
                    Bukkit.addRecipe(new CampfireRecipe(recipe.getKey(), result, input, experience, cookingTime));
            default ->
                    Bukkit.addRecipe(new FurnaceRecipe(recipe.getKey(), result, input, experience, cookingTime));
        }
        Alkatraz.logInfo("Registered " + recipe.getType().name().toLowerCase(Locale.ROOT)
                + " recipe " + recipe.getKey());
    }

    @Override
    public void onFurnaceStart(FurnaceStartSmeltEvent event) {
        AlkatrazRecipe recipe = RecipeRegistry.get(event.getRecipe().getKey());
        if (recipe == null) return;
        if (!RecipeGate.canCraft(null, recipe)) {
            Alkatraz.logWarning("Cooking recipe " + recipe.getKey()
                    + " is gated (requirements/permissions) and cannot be cancelled on this API "
                    + "(FurnaceStartSmeltEvent is not cancellable in spigot-api 1.19)");
        }
    }
}
