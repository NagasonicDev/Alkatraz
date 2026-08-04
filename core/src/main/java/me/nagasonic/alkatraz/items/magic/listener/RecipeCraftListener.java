package me.nagasonic.alkatraz.items.magic.listener;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.magic.imbue.ImbueManager;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.items.magic.recipe.adapter.CraftingEventRouter;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockManager;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Arrays;

public class RecipeCraftListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getView().getPlayer() instanceof Player player) {
            UnlockManager.evaluate(player);
        }
        Recipe recipe = event.getRecipe();

        if (recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equals("alkatraz")
                && keyed.getKey().getKey().startsWith("imbue_")) {
            handleImbuingCraft(event);
            return;
        }

        if (CraftingEventRouter.onPrepare(event)) {
            return;
        }

        if (hasMagicIngredient(event)) {
            Alkatraz.logHigh("[CraftListener] Blocking non-alkatraz recipe because matrix contains magic item(s): " +
                    Arrays.toString(Arrays.stream(event.getInventory().getMatrix()).map(RecipeCraftListener::describeStack).toArray()));
            event.getInventory().setResult(null);
        }
    }

    private static boolean hasMagicIngredient(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item != null && !item.getType().isAir() && MagicItemStack.isMagicItem(item)) {
                return true;
            }
        }
        return false;
    }

    private void handleImbuingCraft(PrepareItemCraftEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        ItemStack input = null;
        for (ItemStack item : matrix) {
            if (item != null && !item.getType().isAir() && !MagicItemStack.isMagicItem(item)) {
                input = item;
                break;
            }
        }
        if (input == null || !ImbueManager.isImbuable(input.getType())) {
            event.getInventory().setResult(null);
            return;
        }
        ItemStack result = ImbueManager.imbue(input);
        if (result == input) {
            event.getInventory().setResult(null);
            return;
        }
        event.getInventory().setResult(result);
    }

    private static String describeStack(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return "AIR";
        StringBuilder sb = new StringBuilder();
        sb.append(stack.getType()).append("x").append(stack.getAmount());
        boolean isMagic = MagicItemStack.isMagicItem(stack);
        boolean isEngraving = MagicItemStack.isEngravingItem(stack);
        if (isMagic) sb.append("[MAGIC]");
        if (isEngraving) sb.append("[ENGRAVING]");
        return sb.toString();
    }
}
