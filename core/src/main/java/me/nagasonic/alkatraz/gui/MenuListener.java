package me.nagasonic.alkatraz.gui;

import me.nagasonic.alkatraz.gui.grimoire.GrimoireLecternState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Global listener for all menu interactions
 */
public class MenuListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Menu menu = Menu.getActiveMenu(player);
        if (menu == null) return;

        if (!menu.matches(event.getView().getTitle())) return;

        Inventory top = event.getView().getTopInventory();
        int raw = event.getRawSlot();
        boolean inTop = raw >= 0 && raw < top.getSize();

        if (!menu.dropZoneSlots().isEmpty()) {
            // Edit menus: the player's own inventory is off-limits entirely
            if (!inTop) {
                event.setCancelled(true);
                return;
            }
            // Non-drop-zone top slots run the menu handler (buttons) but never move items
            if (!menu.dropZoneSlots().contains(raw)) {
                ItemStack clicked = event.getCurrentItem();
                menu.handleClick(event, clicked);
                event.setCancelled(true);
                return;
            }
            // Drop-zone slots: only single-slot place / collect actions reach the menu
            InventoryAction action = event.getAction();
            boolean allowed = action == InventoryAction.PLACE_ONE
                    || action == InventoryAction.PLACE_ALL
                    || action == InventoryAction.COLLECT_TO_CURSOR;
            if (!allowed) {
                event.setCancelled(true);
                return;
            }
        }

        ItemStack clicked = event.getCurrentItem();
        boolean shouldCancel = menu.handleClick(event, clicked);

        if (shouldCancel) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Menu menu = Menu.getActiveMenu(player);
        if (menu == null) return;

        if (!menu.matches(event.getView().getTitle())) return;

        // Edit menus only: drags are never allowed to touch a non-drop-zone slot.
        // Non-edit menus declare no drop zones, so they keep their prior (unhandled) drag behavior.
        Inventory top = event.getView().getTopInventory();
        Set<Integer> dropZones = menu.dropZoneSlots();
        if (!dropZones.isEmpty()) {
            for (int slot : event.getRawSlots()) {
                if (slot < top.getSize() && !dropZones.contains(slot)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        // If it is an edit menu, cancel and let the menu resolve placement manually.
        if (!dropZones.isEmpty()) {
            event.setCancelled(true);
            menu.onDrag(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Menu menu = Menu.getActiveMenu(player);
        if (menu != null && menu.matches(event.getView().getTitle())) {
            try {
                Menu.removeActiveMenu(player);
                menu.onClose();
            } finally {
                if (GrimoireLecternState.isActive(player)) {
                    GrimoireLecternState.remove(player);
                }
            }
        } else if (GrimoireLecternState.isActive(player)) {
            GrimoireLecternState.remove(player);
        }
    }
}
