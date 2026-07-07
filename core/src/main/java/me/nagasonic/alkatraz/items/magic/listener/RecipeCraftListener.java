package me.nagasonic.alkatraz.items.magic.listener;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.configuration.requirement.Requirement;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.items.magic.recipe.MagicItemRecipeManager;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Arrays;
import java.util.List;

public class RecipeCraftListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        boolean hasMagicIngredient = hasMagicIngredient(event);
        Recipe recipe = event.getRecipe();
        if (recipe instanceof Keyed keyed) {
            Alkatraz.logInfo("[CraftListener] Detected recipe is keyed: " + keyed.getKey() + " with namespace: " + keyed.getKey().getNamespace());
        }

        if (recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equals("alkatraz")) {
            NamespacedKey recipeKey = keyed.getKey();
            ItemStack result = recipe.getResult();
            Alkatraz.logInfo("[CraftListener] Detected alkatraz recipe: " + recipeKey + " result=" + (result == null ? "null" : result.getType()) + " resultPDC=" + (result == null ? "N/A" : String.valueOf(result.hasItemMeta() ? result.getItemMeta().getPersistentDataContainer().getKeys() : "no-meta")));

            ItemStack[] matrix = event.getInventory().getMatrix();
            Alkatraz.logInfo("[CraftListener]  Matrix: " + Arrays.toString(Arrays.stream(matrix).map(RecipeCraftListener::describeStack).toArray()));

            // Block if a magic ingredient appears in a recipe that doesn't match (e.g. mixed up matrix)
            if (hasMagicIngredient && result != null && !MagicItemStack.isMagicItem(result) && !MagicItemStack.isEngravingItem(result)) {
                Alkatraz.logWarning("[CraftListener] Alkataz recipe " + recipeKey + " has magic ingredients but result is not a magic item — result PDC mismatch, blocking");
                event.getInventory().setResult(null);
                return;
            }

            List<Requirement> requirements = MagicItemRecipeManager.getRequirements(recipeKey);
            if (!requirements.isEmpty()) {
                if (!(event.getView().getPlayer() instanceof Player player)) {
                    Alkatraz.logWarning("[CraftListener]  Player check failed, blocking craft");
                    event.getInventory().setResult(null);
                    return;
                }
                for (Requirement req : requirements) {
                    if (!req.isMet(player)) {
                        Alkatraz.logInfo("[CraftListener]  Requirement not met: " + req.getClass().getSimpleName() + " for player " + player.getName() + ", blocking craft");
                        event.getInventory().setResult(null);
                        return;
                    }
                }
                Alkatraz.logInfo("[CraftListener]  All requirements met for player " + player.getName());
            }
            return;
        }

        if (hasMagicIngredient) {
            Alkatraz.logInfo("[CraftListener] Blocking non-alkatraz recipe because matrix contains magic item(s): " +
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
