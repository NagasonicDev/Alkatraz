package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerDispatch;
import me.nagasonic.alkatraz.items.magic.trigger.WorldTimeTracker;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fires periodic engraving triggers: {@code on_interval} every second, and
 * {@code on_day} / {@code on_night} when a world crosses dawn or dusk.
 */
public final class PeriodicTriggerListener {

    private static final long INTERVAL_TICKS = 20L;

    /**
     * {@link MagicKeys#alkatraz(String)} allocates a new {@link NamespacedKey} on every
     * call, which this listener would otherwise do once per player per second.
     */
    private static final Map<String, NamespacedKey> KEY_CACHE = new ConcurrentHashMap<>();

    private final WorldTimeTracker timeTracker = new WorldTimeTracker();

    private static String triggerKey(String path) {
        return KEY_CACHE.computeIfAbsent(path, MagicKeys::alkatraz).getKey();
    }

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
            params.put("is_night", transition.equals(triggerKey("on_night")));
            for (Player player : world.getPlayers()) {
                TriggerDispatch.dispatch(player, transition, params);
            }
        }

        String interval = triggerKey("on_interval");
        for (Player player : Bukkit.getOnlinePlayers()) {
            TriggerDispatch.dispatch(player, interval);
        }
    }
}
