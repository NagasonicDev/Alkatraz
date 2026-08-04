package me.nagasonic.alkatraz.items.magic.recipe.adapter;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingRecipe;

import java.util.Set;

public class SmithingCraftingAdapter implements CraftingTypeAdapter {

    @Override
    public RecipeType type() {
        return RecipeType.SMITHING;
    }

    @Override
    public void registerNative(AlkatrazRecipe recipe) {
        if (recipe.getBase() == null || recipe.getAddition() == null) {
            Alkatraz.logWarning("Cannot register smithing recipe " + recipe.getKey() + ": missing base/addition");
            return;
        }
        ItemStack result = recipe.getResult().clone();
        result.setAmount(recipe.getResultAmount());
        RecipeChoice base = new RecipeChoice.ExactChoice(recipe.getBase());
        RecipeChoice addition = new RecipeChoice.ExactChoice(recipe.getAddition());
        Bukkit.addRecipe(new SmithingRecipe(recipe.getKey(), result, base, addition));
    }

    @Override
    public void onPrepare(PrepareItemCraftEvent event) {
    }

    @Override
    public void onSmith(PrepareSmithingEvent event) {
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        ItemStack result = event.getResult();
        if (result == null) return;
        Set<NamespacedKey> keys = RecipeRegistry.getByOutputMaterial(result.getType());
        if (keys.isEmpty()) return;
        for (NamespacedKey key : keys) {
            AlkatrazRecipe recipe = RecipeRegistry.get(key);
            if (recipe == null || recipe.getType() != RecipeType.SMITHING) continue;
            if (!result.isSimilar(recipe.getResult())) continue;
            if (!RecipeGate.canCraft(player, recipe)) {
                event.setResult(null);
            }
            return;
        }
    }
}
