package me.nagasonic.alkatraz.items.magic.equipment;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.magic.condition.implementation.CooldownTracker;
import me.nagasonic.alkatraz.items.magic.itemstack.MagicItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Invalidates the {@link EquipmentService} profile cache whenever a player's
 * equipment can have changed, and releases per-player state on quit.
 * <p>
 * The profile cache avoids a full YAML deserialization of every equipped magic item
 * on each trigger dispatch, so it must be dropped on every path that can mutate
 * equipment. This listener covers the player-driven paths; the periodic
 * {@link EquipmentReconcileTask} fingerprints the same slots once a second and drops
 * the cache whenever it observes a change, which bounds staleness for changes made
 * by commands or other plugins.
 */
public final class EquipmentCacheListener implements Listener {

    private EquipmentCacheListener() {}

    public static void tryRegister(Alkatraz plugin) {
        plugin.getServer().getPluginManager().registerEvents(new EquipmentCacheListener(), plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        EquipmentService.invalidate(player.getUniqueId());
        CooldownTracker.clear(player.getUniqueId());
        try {
            EquipmentStorage.unload(player);
        } catch (Exception e) {
            Alkatraz.logWarning("Failed to flush equipment storage for " + player.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        EquipmentService.invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        EquipmentService.invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        EquipmentService.invalidate(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        EquipmentService.invalidate(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        EquipmentService.invalidate(event.getPlayer().getUniqueId());
    }

    /**
     * Interacting with a worn piece of equipment (a right-click that swaps or consumes
     * the held item, or a left-click that breaks it) can change the profile mid-tick.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR
                && action != Action.RIGHT_CLICK_BLOCK
                && action != Action.LEFT_CLICK_AIR
                && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) return;
        if (!MagicItemStack.isMagicItem(item)) return;
        EquipmentService.invalidate(event.getPlayer().getUniqueId());
    }
}
