package me.nagasonic.alkatraz.items.magic.recipe.unlock;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public final class UnlockSweep {

    private static final String CONFIG_PATH = "recipes.unlock_check_interval_ticks";
    private static final long DEFAULT_INTERVAL = 60L;

    private UnlockSweep() {}

    public static void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UnlockManager.refresh(player);
                }
            }
        }.runTaskTimer(Alkatraz.getInstance(), DEFAULT_INTERVAL, intervalTicks());
    }

    private static long intervalTicks() {
        YamlConfiguration config = ConfigManager.getConfig("config.yml").get();
        return config != null ? Math.max(1L, config.getLong(CONFIG_PATH, DEFAULT_INTERVAL)) : DEFAULT_INTERVAL;
    }
}
