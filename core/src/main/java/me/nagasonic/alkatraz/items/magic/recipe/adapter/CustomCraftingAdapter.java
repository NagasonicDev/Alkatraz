package me.nagasonic.alkatraz.items.magic.recipe.adapter;

import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;

/**
 * Public extension point for third-party, GUI-driven crafting stations.
 *
 * <p>Custom recipes are declared as {@code type: custom} in magic/recipes YAML and are stored in
 * {@link me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry} under
 * {@link RecipeType#CUSTOM}. They are never registered with the Bukkit recipe manager; all
 * interaction is driven through {@link #handleClick(InventoryClickEvent, Player)}.</p>
 *
 * <p>Register an instance with
 * {@link CraftingEventRouter#register(CustomCraftingAdapter)}. There is no built-in custom
 * crafting GUI in this iteration.</p>
 */
public abstract class CustomCraftingAdapter implements CraftingTypeAdapter {

    @Override
    public final RecipeType type() {
        return RecipeType.CUSTOM;
    }

    @Override
    public final void registerNative(AlkatrazRecipe recipe) {
        // Custom stations are GUI-driven; recipes are never registered with Bukkit.
    }

    @Override
    public final void onPrepare(PrepareItemCraftEvent event) {
    }

    /**
     * Called for every {@link InventoryClickEvent} while this adapter is registered. The
     * implementation decides whether the clicked inventory belongs to its station.
     *
     * @param event  the click event
     * @param player the clicking player, or {@code null} for non-player viewers
     */
    public abstract void handleClick(InventoryClickEvent event, Player player);
}
