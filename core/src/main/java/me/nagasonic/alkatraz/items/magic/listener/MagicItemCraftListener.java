package me.nagasonic.alkatraz.items.magic.listener;

import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.items.magic.recipe.AlkatrazRecipe;
import me.nagasonic.alkatraz.items.magic.recipe.Ingredient;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeRegistry;
import me.nagasonic.alkatraz.items.magic.recipe.RecipeType;
import me.nagasonic.alkatraz.items.magic.recipe.adapter.RecipeGate;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Matches shaped recipes whose magic-item ingredients are checked by item
 * definition instead of exact item equality. Native {@code ExactChoice} matching
 * compares the full item NBT, so non-stackable magic items (which carry a unique
 * per-item UUID) never match the native recipe. This listener drives the crafting
 * table for those recipes: it shows the result on {@link PrepareItemCraftEvent}
 * and performs the craft (consuming the matrix) when the result is taken.
 */
public final class MagicItemCraftListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (event.getRecipe() instanceof Keyed keyed && RecipeRegistry.get(keyed.getKey()) != null) {
            return;
        }
        AlkatrazRecipe recipe = findMatchingRecipe(event.getInventory().getMatrix());
        if (recipe == null) {
            return;
        }
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        if (!RecipeGate.canCraft(player, recipe)) {
            event.getInventory().setResult(null);
            return;
        }
        ItemStack result = recipe.getResult().clone();
        result.setAmount(recipe.getResultAmount());
        event.getInventory().setResult(result);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResultClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory crafting)) {
            return;
        }
        if (event.getRawSlot() != 0 || event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }
        if (event instanceof CraftItemEvent craft && craft.getRecipe() != null) {
            return;
        }
        Player player = event.getView().getPlayer() instanceof Player p ? p : null;
        if (player == null) {
            return;
        }
        ItemStack[] matrix = crafting.getMatrix();
        AlkatrazRecipe recipe = findMatchingRecipe(matrix);
        if (recipe == null) {
            return;
        }
        if (!RecipeGate.canCraft(player, recipe)) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        for (int i = 0; i < matrix.length; i++) {
            ItemStack cell = matrix[i];
            if (cell == null || cell.getType().isAir()) {
                continue;
            }
            if (cell.getAmount() > 1) {
                ItemStack remaining = cell.clone();
                remaining.setAmount(cell.getAmount() - 1);
                matrix[i] = remaining;
            } else {
                matrix[i] = null;
            }
        }
        crafting.setMatrix(matrix);
        ItemStack result = recipe.getResult().clone();
        result.setAmount(recipe.getResultAmount());
        MagicItemStack.refreshUniqueUuid(result);
        if (event.isShiftClick()) {
            player.getInventory().addItem(result).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        } else {
            event.setCursor(result);
        }
        player.updateInventory();
    }

    private static AlkatrazRecipe findMatchingRecipe(ItemStack[] matrix) {
        for (AlkatrazRecipe recipe : RecipeRegistry.getAll()) {
            if (recipe.getType() != RecipeType.SHAPED) {
                continue;
            }
            if (matchesShape(matrix, recipe)) {
                return recipe;
            }
        }
        return null;
    }

    private static boolean matchesShape(ItemStack[] matrix, AlkatrazRecipe recipe) {
        String[] shape = recipe.getShape();
        Map<Character, Ingredient> ingredients = recipe.getIngredientMap();
        if (shape == null || shape.length == 0 || ingredients == null) {
            return false;
        }
        int rows = shape.length;
        int cols = 0;
        for (String row : shape) {
            cols = Math.max(cols, row.length());
        }
        if (rows > 3 || cols > 3) {
            return false;
        }
        for (int dy = 0; dy <= 3 - rows; dy++) {
            for (int dx = 0; dx <= 3 - cols; dx++) {
                if (matchesAt(matrix, shape, ingredients, dx, dy)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesAt(ItemStack[] matrix, String[] shape,
                                     Map<Character, Ingredient> ingredients, int dx, int dy) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                ItemStack cell = matrix[row * 3 + col];
                char ch = ' ';
                int sr = row - dy;
                int sc = col - dx;
                if (sr >= 0 && sr < shape.length && sc >= 0 && sc < shape[sr].length()) {
                    ch = shape[sr].charAt(sc);
                }
                if (ch == ' ') {
                    if (cell != null && !cell.getType().isAir()) {
                        return false;
                    }
                } else {
                    Ingredient ingredient = ingredients.get(ch);
                    if (ingredient == null || !ingredient.matches(cell)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
