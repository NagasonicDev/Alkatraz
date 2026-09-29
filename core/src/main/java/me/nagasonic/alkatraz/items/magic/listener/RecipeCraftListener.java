package me.nagasonic.alkatraz.items.magic.listener;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.magic.imbue.ImbueManager;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import me.nagasonic.alkatraz.items.magic.persistence.ItemDataKeys;
import me.nagasonic.alkatraz.items.magic.recipe.adapter.CraftingEventRouter;
import me.nagasonic.alkatraz.items.magic.recipe.unlock.UnlockManager;
import org.bukkit.Keyed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RecipeCraftListener implements Listener {

    private static final Map<UUID, Integer> lastMatrix = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getView().getPlayer() instanceof Player player && matrixChanged(player, event.getInventory().getMatrix())) {
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastMatrix.remove(event.getPlayer().getUniqueId());
    }

    /**
     * {@link PrepareItemCraftEvent} fires on every ingredient place/take/shift-click,
     * not just on a craft, so the unlock sweep is gated on a fingerprint of the
     * crafting matrix actually changing. The fingerprint mixes each cell's material
     * and amount plus, for magic items, the definition and instance payload, which
     * covers every matrix change the unlock sweep could care about without paying
     * {@code ItemStack#hashCode}'s meta copy.
     */
    private static boolean matrixChanged(Player player, ItemStack[] matrix) {
        int hash = 1;
        for (ItemStack cell : matrix) {
            hash = 31 * hash + cellHash(cell);
        }
        return !Objects.equals(lastMatrix.put(player.getUniqueId(), hash), hash);
    }

    private static int cellHash(ItemStack cell) {
        if (cell == null || cell.getType().isAir()) {
            return 0;
        }
        int hash = 31 * cell.getType().hashCode() + cell.getAmount();
        if (!cell.hasItemMeta()) {
            return hash;
        }
        ItemMeta meta = cell.getItemMeta();
        if (meta == null) {
            return hash;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String definition = pdc.get(ItemDataKeys.itemDefinition(), PersistentDataType.STRING);
        if (definition != null) {
            hash = 31 * hash + definition.hashCode();
        }
        String instance = pdc.get(ItemDataKeys.itemInstance(), PersistentDataType.STRING);
        if (instance != null) {
            hash = 31 * hash + instance.hashCode();
        }
        return hash;
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
