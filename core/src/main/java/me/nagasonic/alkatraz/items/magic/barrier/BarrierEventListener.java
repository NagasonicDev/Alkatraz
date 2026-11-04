package me.nagasonic.alkatraz.items.magic.barrier;

import me.nagasonic.alkatraz.spells.effect.EffectRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class BarrierEventListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        BarrierManager.disposeFor(event.getPlayer(), BarrierConfig.ResetOn.QUIT);
        EffectRegistry.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        BarrierManager.disposeFor(event.getEntity(), BarrierConfig.ResetOn.DEATH);
        EffectRegistry.clear(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        // Null moment: dispose the active barrier but keep persisted HP/cooldown,
        // since world change is not one of BarrierConfig.ResetOn's moments.
        BarrierManager.disposeFor(event.getPlayer(), null);
    }
}
