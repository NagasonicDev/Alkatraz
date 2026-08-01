package me.nagasonic.alkatraz.items.magic.recipe.unlock;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class UnlockListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UnlockManager.refresh(event.getPlayer());
    }
}
