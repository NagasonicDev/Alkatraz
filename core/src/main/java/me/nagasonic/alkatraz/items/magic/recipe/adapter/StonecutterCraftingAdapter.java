package me.nagasonic.alkatraz.items.magic.recipe.adapter;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.Set;

public class StonecutterCraftingAdapter implements CraftingTypeAdapter {

    @Override
    public RecipeType type() {
        return RecipeType.STONECUTTER;
    }

    @Override
    public void registerNative(AlkatrazRecipe recipe) {
        if (recipe.getInput() == null) {
            Alkatraz.logWarning("Cannot register stonecutter recipe " + recipe.getKey() + ": no input");
            return;
        }
        ItemStack result = recipe.getResult().clone();
        result.setAmount(recipe.getResultAmount());
        RecipeChoice input = new RecipeChoice.ExactChoice(recipe.getInput());
        Bukkit.addRecipe(new StonecuttingRecipe(recipe.getKey(), result, input));
    }

    @Override
    public void onPrepare(PrepareItemCraftEvent event) {
    }

    @Override
    public void onStonecutterClick(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        Set<NamespacedKey> keys = RecipeRegistry.getByOutputMaterial(result.getType());
        if (keys.isEmpty()) return;
        for (NamespacedKey key : keys) {
            AlkatrazRecipe recipe = RecipeRegistry.get(key);
            if (recipe == null || recipe.getType() != RecipeType.STONECUTTER) continue;
            if (!result.isSimilar(recipe.getResult())) continue;
            if (!RecipeGate.canCraft(player, recipe)) {
                event.setCancelled(true);
            }
            return;
        }
    }
}
