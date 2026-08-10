package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.items.magic.trigger.PlayerMoveTracker;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerDispatch;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Fires movement-related engraving triggers. Movement-state triggers
 * ({@code on_jump}, {@code on_land}, ...) are detected via a
 * {@link PlayerMoveTracker}.
 */
public final class MovementTriggerListener implements Listener {

    private final PlayerMoveTracker tracker = new PlayerMoveTracker();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSprintToggle(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        TriggerDispatch.dispatch(player, event.isSprinting() ? "on_sprint" : "on_stop_sprint");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwimToggle(EntityToggleSwimEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        TriggerDispatch.dispatch(player, event.isSwimming() ? "on_swim" : "on_stop_swim");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlideToggle(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        TriggerDispatch.dispatch(player, event.isGliding() ? "on_glide" : "on_stop_glide");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlightToggle(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        TriggerDispatch.dispatch(player, event.isFlying() ? "on_start_fly" : "on_stop_fly");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)) return;
        Map<String, Object> params = new HashMap<>();
        params.put("vehicle_type", event.getVehicle().getType().name());
        TriggerDispatch.dispatch(player, "on_enter_vehicle", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;
        Map<String, Object> params = new HashMap<>();
        params.put("vehicle_type", event.getVehicle().getType().name());
        TriggerDispatch.dispatch(player, "on_leave_vehicle", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnterPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        TriggerDispatch.dispatch(player, "on_enter_portal");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> params = new HashMap<>();
        params.put("cause", event.getCause() != null ? event.getCause().name() : "UNKNOWN");
        TriggerDispatch.dispatch(player, "on_teleport", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> params = new HashMap<>();
        params.put("from_world", event.getFrom().getName());
        params.put("to_world", player.getWorld().getName());
        TriggerDispatch.dispatch(player, "on_world_change", params);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        TriggerDispatch.dispatch(player, "on_bed_enter");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        TriggerDispatch.dispatch(player, "on_bed_leave");
    }

    /**
     * Tracks player movement and fires movement-state triggers when a state
     * transition is detected.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (sameBlock(from, to)) return;

        PlayerMoveTracker.MoveState state = new PlayerMoveTracker.MoveState(
                blockKey(to),
                to.getBlock().getType().name(),
                player.isOnGround(),
                player.isInWater(),
                TriggerDispatch.inLava(player),
                TriggerDispatch.isClimbing(player),
                to.getWorld().getBiome(to.getBlockX(), to.getBlockZ()).name(),
                to.getY()
        );

        Set<String> fired = tracker.update(player.getUniqueId(), state);
        for (String trigger : fired) {
            Map<String, Object> params = new HashMap<>();
            switch (trigger) {
                case PlayerMoveTracker.ENTER_BIOME -> params.put("biome", state.biome());
                case PlayerMoveTracker.LAND -> params.put("fall_distance", player.getFallDistance());
                case PlayerMoveTracker.STEP_ON_BLOCK -> params.put("block_type", state.blockType());
                default -> {
                }
            }
            TriggerDispatch.dispatch(player, trigger, params);
        }
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getWorld() == b.getWorld()
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private static String blockKey(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }
}
