package me.nagasonic.alkatraz.items.magic.recipe;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

public interface Ingredient {
    RecipeChoice toChoice();
    boolean matches(ItemStack item);
    String describe();
}
