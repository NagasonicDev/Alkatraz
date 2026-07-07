package me.nagasonic.alkatraz.gui.implementation;

import me.nagasonic.alkatraz.gui.implementation.engraving.EngravingTableMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class WandTableEngineeringHandler {

    public static void handleEngineeringClick(WandTableSelectionMenu menu, InventoryClickEvent event) {
        Player viewer = menu.getViewer();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == org.bukkit.Material.AIR) return;

        if (event.getSlot() == 14) {
            menu.close();
            new EngravingTableMenu(viewer).open();
        }
    }
}