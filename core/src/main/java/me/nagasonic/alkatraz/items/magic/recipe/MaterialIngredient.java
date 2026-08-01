package me.nagasonic.alkatraz.items.magic.recipe;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

public final class MaterialIngredient implements Ingredient {
    private final Material material;

    public MaterialIngredient(Material material) {
        this.material = material;
    }

    @Override
    public RecipeChoice toChoice() {
        return new RecipeChoice.MaterialChoice(material);
    }

    @Override
    public boolean matches(ItemStack item) {
        return item != null && item.getType() == material;
    }

    @Override
    public String describe() {
        return material.name();
    }
}
