package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.hooks.WorldGuardBridge;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerDispatch;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

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

    private final WorldGuardBridge bridge = WorldGuardBridge.getInstance();
    private final Map<UUID, Set<String>> lastRegions = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!bridge.isPresent()) return;
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

    private static boolean sameBlock(Location a, Location b) {
        return a.getWorld() == b.getWorld()
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
