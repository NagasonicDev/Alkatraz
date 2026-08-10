package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.items.magic.trigger.TriggerDispatch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Fires inventory-related engraving triggers.
 */
public final class InventoryTriggerListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUseItem(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        Player player = event.getPlayer();

        Map<String, Object> params = new HashMap<>();
        params.put("action", event.getAction().name());
        if (event.getClickedBlock() != null) {
            params.put("clicked_block_type", event.getClickedBlock().getType().name());
        }
        TriggerDispatch.dispatchHeld(player, "on_use_item", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeftClickItem(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_AIR
                && event.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        Player player = event.getPlayer();

        Map<String, Object> params = new HashMap<>();
        params.put("action", event.getAction().name());
        if (event.getClickedBlock() != null) {
            params.put("clicked_block_type", event.getClickedBlock().getType().name());
        }
        TriggerDispatch.dispatchHeld(player, "on_left_click_item", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        TriggerDispatch.dispatch(player, "on_swap_hands");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();

        Map<String, Object> params = new HashMap<>();
        params.put("item_type", item.getType().name());
        params.put("amount", item.getAmount());
        TriggerDispatch.dispatch(player, "on_pickup_item", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> params = new HashMap<>();
        params.put("item_type", event.getItemDrop().getItemStack().getType().name());
        TriggerDispatch.dispatch(player, "on_drop_item", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsumeItem(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> params = new HashMap<>();
        params.put("item_type", event.getItem().getType().name());
        TriggerDispatch.dispatch(player, "on_consume_item", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        Player player = event.getPlayer();

        Map<String, Object> params = new HashMap<>();
        params.put("item_type", item.getType().name());
        params.put("damage", event.getDamage());
        TriggerDispatch.dispatch(player, "on_item_damage", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> params = new HashMap<>();
        params.put("item_type", event.getBrokenItem().getType().name());
        TriggerDispatch.dispatch(player, "on_item_break", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRepair(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.SMITHING) return;
        if (event.getCurrentItem() == null) return;

        Map<String, Object> params = new HashMap<>();
        params.put("item_type", event.getCurrentItem().getType().name());
        TriggerDispatch.dispatch(player, "on_repair", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        Map<String, Object> params = new HashMap<>();
        params.put("item_type", event.getItem().getType().name());
        TriggerDispatch.dispatch(player, "on_enchant", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpenInventory(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Map<String, Object> params = new HashMap<>();
        params.put("inventory_type", event.getInventory().getType().name());
        TriggerDispatch.dispatch(player, "on_open_inventory", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCloseInventory(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Map<String, Object> params = new HashMap<>();
        params.put("inventory_type", event.getInventory().getType().name());
        TriggerDispatch.dispatch(player, "on_close_inventory", params);
    }
}
