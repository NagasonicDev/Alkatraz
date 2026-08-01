package me.nagasonic.alkatraz.items.magic.recipe;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

public final class TagIngredient implements Ingredient {
    private final Tag<Material> tag;

    public TagIngredient(Tag<Material> tag) {
        this.tag = tag;
    }

    @Override
    public RecipeChoice toChoice() {
        return new RecipeChoice.MaterialChoice(tag.getValues().toArray(new Material[0]));
    }

    @Override
    public boolean matches(ItemStack item) {
        return item != null && tag.isTagged(item.getType());
    }

    @Override
    public String describe() {
        return "tag:" + tag.getKey();
    }
}
