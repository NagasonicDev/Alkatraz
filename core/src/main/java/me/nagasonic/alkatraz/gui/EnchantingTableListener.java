package me.nagasonic.alkatraz.gui;

import me.nagasonic.alkatraz.gui.implementation.EnchantingTableChoiceMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class EnchantingTableListener implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onEnchantingTableInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENCHANTING_TABLE) return;

        Player player = event.getPlayer();
        event.setCancelled(true);
        new EnchantingTableChoiceMenu(player, event.getClickedBlock().getLocation()).open();
    }
}
