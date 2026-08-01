package me.nagasonic.alkatraz.items.magic.recipe.adapter;

import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;

public interface CraftingTypeAdapter {

    RecipeType type();

    void registerNative(AlkatrazRecipe recipe);

    void onPrepare(PrepareItemCraftEvent event);

    default void onFurnaceStart(FurnaceStartSmeltEvent event) {
    }

    default void onSmith(PrepareSmithingEvent event) {
    }

    default void onStonecutterClick(InventoryClickEvent event) {
    }

    default void onBrew(BrewEvent event) {
    }

    default void onAnvil(PrepareAnvilEvent event) {
    }
}
