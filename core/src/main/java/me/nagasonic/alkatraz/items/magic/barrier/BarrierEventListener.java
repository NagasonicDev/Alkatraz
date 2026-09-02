package me.nagasonic.alkatraz.items.magic.barrier;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class BarrierEventListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        BarrierManager.disposeFor(event.getPlayer(), BarrierConfig.ResetOn.QUIT);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        BarrierManager.disposeFor(event.getEntity(), BarrierConfig.ResetOn.DEATH);
    }
}
