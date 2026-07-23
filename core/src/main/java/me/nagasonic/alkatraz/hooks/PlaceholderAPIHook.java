package me.nagasonic.alkatraz.hooks;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.hooks.placeholder.*;
import org.bukkit.configuration.file.YamlConfiguration;

public class PlaceholderAPIHook extends PluginHook {
    public PlaceholderAPIHook() {
        super("PlaceholderAPI");
    }

    @Override
    public void ifPresent() {
        AlkatrazPlaceholder expansion = new AlkatrazPlaceholder();

        expansion.registerHandler(new StatsPlaceholder());
        expansion.registerHandler(new ElementPlaceholder());
        expansion.registerHandler(new SpellPlaceholder());
        expansion.registerHandler(new ResearchPlaceholder());
        expansion.registerHandler(new HotbarPlaceholder());

        YamlConfiguration config = Alkatraz.getPluginConfig();
        if (config.getBoolean("placeholders.leaderboard.enabled", true)) {
            long refreshMinutes = config.getLong("placeholders.leaderboard.refresh_interval_minutes", 5);
            int maxEntries = config.getInt("placeholders.leaderboard.max_entries", 10);
            expansion.registerHandler(new LeaderboardPlaceholder(refreshMinutes, maxEntries));
        }

        expansion.register();
        Alkatraz.logInfo("PlaceholderAPI hook registered successfully!");
    }
}
