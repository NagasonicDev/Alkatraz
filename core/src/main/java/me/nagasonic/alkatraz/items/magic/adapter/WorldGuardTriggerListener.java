package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.hooks.WorldGuardBridge;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerDispatch;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerPipeline;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fires {@code on_region_enter} / {@code on_region_leave} when a player moves
 * into or out of a WorldGuard region. No-ops when WorldGuard is absent.
 */
public final class WorldGuardTriggerListener implements Listener {

    private static final org.bukkit.NamespacedKey REGION_ENTER = TriggerPipeline.boundKey("on_region_enter");
    private static final org.bukkit.NamespacedKey REGION_LEAVE = TriggerPipeline.boundKey("on_region_leave");

    private final WorldGuardBridge bridge = WorldGuardBridge.getInstance();
    private final Map<UUID, Set<String>> lastRegions = new ConcurrentHashMap<>();

    /**
     * {@link WorldGuardBridge#regionsAt(Location)} costs several reflective {@code Method.invoke}
     * calls plus collection allocations, and would otherwise run for every block change even
     * when no definition declares a region trigger. {@code isBound} fails open, so this keeps
     * the pre-existing behaviour whenever the bound-trigger index is unavailable.
     */
    private static boolean regionTriggersBound() {
        return TriggerPipeline.isBound(REGION_ENTER) || TriggerPipeline.isBound(REGION_LEAVE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!bridge.isPresent()) return;
        if (!regionTriggersBound()) return;
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;
        Location from = event.getFrom();
        if (sameBlock(from, to)) return;

        Set<String> current = bridge.regionsAt(to);
        Set<String> previous = lastRegions.getOrDefault(player.getUniqueId(), Set.of());

        Map<String, Object> params = new HashMap<>();
        params.put("world", to.getWorld().getName());

        Set<String> entered = new HashSet<>(current);
        entered.removeAll(previous);
        for (String region : entered) {
            params.put("region", region);
            TriggerDispatch.dispatch(player, "on_region_enter", params);
        }

        Set<String> left = new HashSet<>(previous);
        left.removeAll(current);
        for (String region : left) {
            params.put("region", region);
            TriggerDispatch.dispatch(player, "on_region_leave", params);
        }

        lastRegions.put(player.getUniqueId(), current);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        lastRegions.remove(event.getPlayer().getUniqueId());
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getWorld() == b.getWorld()
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
