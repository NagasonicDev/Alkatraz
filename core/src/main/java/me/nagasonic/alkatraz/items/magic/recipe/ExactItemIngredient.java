package me.nagasonic.alkatraz.items.magic.recipe;

import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

public final class ExactItemIngredient implements Ingredient {
    private final ItemStack stack;

    public ExactItemIngredient(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public RecipeChoice toChoice() {
        return new RecipeChoice.ExactChoice(stack);
    }

    @Override
    public boolean matches(ItemStack item) {
        if (item == null || stack == null) return false;
        if (MagicItemStack.isMagicItem(stack)) {
            return MagicItemStack.readDefinitionKey(stack)
                    .flatMap(expected -> MagicItemStack.readDefinitionKey(item).map(expected::equals))
                    .orElse(false);
        }
        return item.isSimilar(stack);
    }

    @Override
    public String describe() {
        return stack.getType().name();
    }
}
