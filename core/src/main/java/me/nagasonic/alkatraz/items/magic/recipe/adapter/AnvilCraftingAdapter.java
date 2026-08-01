package me.nagasonic.alkatraz.items.magic.recipe.adapter;

import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public class AnvilCraftingAdapter implements CraftingTypeAdapter {

    @Override
    public RecipeType type() {
        return RecipeType.ANVIL;
    }

    @Override
    public void registerNative(AlkatrazRecipe recipe) {
        // No native Bukkit anvil recipe registry exists; anvil recipes are applied
        // and gated via PrepareAnvilEvent interception.
    }

    @Override
    public void onPrepare(PrepareItemCraftEvent event) {
    }

    @Override
    public void onAnvil(PrepareAnvilEvent event) {
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        AnvilInventory inventory = event.getInventory();
        ItemStack base = inventory.getItem(0);
        ItemStack addition = inventory.getItem(1);
        if (base == null || addition == null) return;
        AlkatrazRecipe recipe = findRecipe(base, addition);
        if (recipe == null) return;
        if (RecipeGate.canCraft(player, recipe)) {
            event.setResult(recipe.getResult().clone());
        } else {
            event.setResult(null);
        }
    }

    private AlkatrazRecipe findRecipe(ItemStack base, ItemStack addition) {
        Set<NamespacedKey> keys = RecipeRegistry.getByStation(RecipeType.ANVIL);
        if (keys.isEmpty()) return null;
        for (NamespacedKey key : keys) {
            AlkatrazRecipe recipe = RecipeRegistry.get(key);
            if (recipe == null) continue;
            if (recipe.getBase() != null && recipe.getAddition() != null
                    && base.isSimilar(recipe.getBase())
                    && addition.isSimilar(recipe.getAddition())) {
                return recipe;
            }
        }
        return null;
    }
}
