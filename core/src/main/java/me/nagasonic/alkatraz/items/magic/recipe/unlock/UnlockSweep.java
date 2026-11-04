package me.nagasonic.alkatraz.items.magic.recipe.unlock;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public final class UnlockSweep {

    private static final String CONFIG_PATH = "recipes.unlock_check_interval_ticks";
    private static final long DEFAULT_INTERVAL = 600L;

    private static BukkitTask task;
    private static int cursor;

    private UnlockSweep() {}

    public static void start() {
        if (task != null) task.cancel();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                sweep();
            }
        }.runTaskTimer(Alkatraz.getInstance(), DEFAULT_INTERVAL, intervalTicks());
    }

    public static void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Evaluates a rotating slice of the online players. The sweep is a safety net for
     * unlock conditions that changed without an explicit notification, so it keeps the
     * {@link UnlockManager} cache instead of invalidating it, and spreads the work over
     * several ticks so no single tick evaluates every player.
     */
    private static void sweep() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        int total = players.size();
        if (total == 0) {
            cursor = 0;
            return;
        }
        int batch = Math.max(1, (total + 3) / 4);
        for (int i = 0; i < batch; i++) {
            Player player = players.get(cursor % total);
            cursor = (cursor + 1) % total;
            UnlockManager.evaluate(player);
        }
    }

    private static long intervalTicks() {
        YamlConfiguration config = ConfigManager.getConfig("config.yml").get();
        if (config == null) return DEFAULT_INTERVAL;
        return Math.max(DEFAULT_INTERVAL, config.getLong(CONFIG_PATH, DEFAULT_INTERVAL));
    }
}
