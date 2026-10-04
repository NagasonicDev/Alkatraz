package me.nagasonic.alkatraz.items.magic.listener;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.VerbosityLevel;
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
        ItemStack[] matrix = event.getInventory().getMatrix();
        Player player = event.getView().getPlayer() instanceof Player viewer ? viewer : null;
        String[] definitionKeys = player != null ? new String[matrix.length] : null;
        if (player != null && matrixChanged(player, matrix, definitionKeys)) {
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

        if (hasMagicIngredient(matrix, definitionKeys)) {
            if (Alkatraz.isLoggable(VerbosityLevel.HIGH)) {
                Alkatraz.logHigh("[CraftListener] Blocking non-alkatraz recipe because matrix contains magic item(s): " +
                        Arrays.toString(Arrays.stream(matrix).map(RecipeCraftListener::describeStack).toArray()));
            }
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
     *
     * <p>Each cell's meta is read once here and its raw definition string handed back in
     * {@code definitionKeys}, so {@link #hasMagicIngredient} can reuse it instead of
     * re-reading the meta of all nine cells. {@code definitionKeys} is {@code null} when the
     * view is not a player, in which case no fingerprint pass ran and the caller has to
     * probe every cell.
     */
    private static boolean matrixChanged(Player player, ItemStack[] matrix, String[] definitionKeys) {
        int hash = 1;
        for (int i = 0; i < matrix.length; i++) {
            hash = 31 * hash + cellHash(matrix[i], definitionKeys, i);
        }
        return !Objects.equals(lastMatrix.put(player.getUniqueId(), hash), hash);
    }

    private static int cellHash(ItemStack cell, String[] definitionKeys, int index) {
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
            definitionKeys[index] = definition;
            hash = 31 * hash + definition.hashCode();
        }
        String instance = pdc.get(ItemDataKeys.itemInstance(), PersistentDataType.STRING);
        if (instance != null) {
            hash = 31 * hash + instance.hashCode();
        }
        return hash;
    }

    /**
     * Whether any matrix cell is a magic item. A cell whose raw definition string was
     * already read by the fingerprint pass is confirmed with a single
     * {@link MagicItemStack#isMagicItem} probe (needed because a blank or unparseable
     * definition is not a magic item), and the scan stops at the first hit. When no
     * fingerprint pass ran ({@code definitionKeys} is {@code null}) every cell is probed,
     * because a cell with no cached definition string may still be a magic item.
     */
    private static boolean hasMagicIngredient(ItemStack[] matrix, String[] definitionKeys) {
        boolean preRead = definitionKeys != null;
        for (int i = 0; i < matrix.length; i++) {
            if (preRead) {
                String definition = definitionKeys[i];
                if (definition == null || definition.isBlank()) {
                    continue;
                }
            }
            ItemStack cell = matrix[i];
            if (cell == null || cell.getType().isAir()) {
                continue;
            }
            if (MagicItemStack.isMagicItem(cell)) {
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
