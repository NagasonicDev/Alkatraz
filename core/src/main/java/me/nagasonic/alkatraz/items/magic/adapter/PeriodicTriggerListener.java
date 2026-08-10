package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerDispatch;
import me.nagasonic.alkatraz.items.magic.trigger.WorldTimeTracker;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Fires periodic engraving triggers: {@code on_interval} every second, and
 * {@code on_day} / {@code on_night} when a world crosses dawn or dusk.
 */
public final class PeriodicTriggerListener {

    private static final long INTERVAL_TICKS = 20L;

    private final WorldTimeTracker timeTracker = new WorldTimeTracker();

    /** Starts the repeating sweep that powers this listener. */
    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                PeriodicTriggerListener.this.tick();
            }
        }.runTaskTimer(Alkatraz.getInstance(), INTERVAL_TICKS, INTERVAL_TICKS);
    }

    void tick() {
        for (World world : Bukkit.getWorlds()) {
            String transition = timeTracker.update(world.getUID(), world.getTime());
            if (transition == null) continue;
            Map<String, Object> params = new HashMap<>();
            params.put("world", world.getName());
            params.put("is_night", transition.equals("on_night"));
            for (Player player : world.getPlayers()) {
                TriggerDispatch.dispatch(player, transition, params);
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            TriggerDispatch.dispatch(player, "on_interval");
        }
    }
}
