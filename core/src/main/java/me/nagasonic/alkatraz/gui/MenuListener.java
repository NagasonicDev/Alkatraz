package me.nagasonic.alkatraz.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Global listener for all menu interactions
 */
public class MenuListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        Menu menu = Menu.getActiveMenu(player);
        if (menu == null) return;
        
        // Check if the clicked inventory belongs to this menu
        if (!menu.matches(event.getView().getTitle())) return;
        
        // Let the menu handle the click
        ItemStack clicked = event.getCurrentItem();
        boolean shouldCancel = menu.handleClick(event, clicked);
        
        if (shouldCancel) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        Menu menu = Menu.getActiveMenu(player);
        if (menu != null && menu.matches(event.getView().getTitle())) {
            Menu.removeActiveMenu(player);
        }
    }
}
