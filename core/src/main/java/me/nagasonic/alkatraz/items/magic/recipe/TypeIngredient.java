package me.nagasonic.alkatraz.items.magic.recipe;

import me.nagasonic.alkatraz.items.magic.util.ItemTypeMapper;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.Set;

public final class TypeIngredient implements Ingredient {
    private final String type;

    public TypeIngredient(String type) {
        this.type = type;
    }

    @Override
    public RecipeChoice toChoice() {
        Set<Material> materials = ItemTypeMapper.getMaterials(type);
        return new RecipeChoice.MaterialChoice(materials.toArray(new Material[0]));
    }

    @Override
    public boolean matches(ItemStack item) {
        return item != null && ItemTypeMapper.hasType(item.getType(), type);
    }

    @Override
    public String describe() {
        return "type:" + type;
    }
}
