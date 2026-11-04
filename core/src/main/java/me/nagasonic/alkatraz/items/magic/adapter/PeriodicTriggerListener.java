package me.nagasonic.alkatraz.items.magic.adapter;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.api.magic.registry.MagicKeys;
import me.nagasonic.alkatraz.items.magic.equipment.EquipmentReconcileTask;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerDispatch;
import me.nagasonic.alkatraz.items.magic.trigger.TriggerPipeline;
import me.nagasonic.alkatraz.items.magic.trigger.WorldTimeTracker;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

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

    private static final NamespacedKey ON_INTERVAL = MagicKeys.alkatraz("on_interval");
    private static final NamespacedKey ON_DAY = MagicKeys.alkatraz("on_day");
    private static final NamespacedKey ON_NIGHT = MagicKeys.alkatraz("on_night");

    private final WorldTimeTracker timeTracker = new WorldTimeTracker();

    private static String triggerKey(String path) {
        return KEY_CACHE.computeIfAbsent(path, MagicKeys::alkatraz).getKey();
    }

    private static BukkitTask periodicTask;

    /** Starts the repeating sweep that powers this listener. */
    public void start() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
        periodicTask = new BukkitRunnable() {
            @Override
            public void run() {
                PeriodicTriggerListener.this.tick();
            }
        }.runTaskTimer(Alkatraz.getInstance(), INTERVAL_TICKS, INTERVAL_TICKS);
    }

    /** Cancels the repeating sweep. */
    public static void stop() {
        BukkitTask current = periodicTask;
        periodicTask = null;
        if (current != null) current.cancel();
    }

    void tick() {
        boolean intervalBound = TriggerPipeline.isBound(ON_INTERVAL);
        boolean dayBound = TriggerPipeline.isBound(ON_DAY);
        boolean nightBound = TriggerPipeline.isBound(ON_NIGHT);
        if (!intervalBound && !dayBound && !nightBound) return;

        String interval = triggerKey("on_interval");
        for (World world : Bukkit.getWorlds()) {
            String transition = timeTracker.update(world.getUID(), world.getTime());
            if (transition == null) continue;
            boolean transitionBound = ON_NIGHT.getKey().equals(transition) ? nightBound : dayBound;
            if (!transitionBound) continue;
            Map<String, Object> params = new HashMap<>();
            params.put("world", world.getName());
            params.put("is_night", transition.equals(triggerKey("on_night")));
            for (Player player : world.getPlayers()) {
                if (!EquipmentReconcileTask.wearsMagicItem(player)) continue;
                TriggerDispatch.dispatch(player, transition, params);
            }
        }

        if (!intervalBound) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!EquipmentReconcileTask.wearsMagicItem(player)) continue;
            TriggerDispatch.dispatch(player, interval);
        }
    }
}
