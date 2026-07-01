package me.nagasonic.alkatraz.progression.research;

import me.nagasonic.alkatraz.config.ConfigManager;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles awarding Research Points from configurable sources.
 */
public final class ResearchPointService {

    private static final String CONFIG = "progression.yml";
    private static Map<String, ResearchPointSource> sources = Map.of();

    private ResearchPointService() {}

    public static void reload() {
        YamlConfiguration config = ConfigManager.getConfig(CONFIG).get();
        ConfigurationSection section = config.getConfigurationSection("research_points.sources");
        Map<String, ResearchPointSource> next = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null) continue;
                next.put(key.toLowerCase(), new ResearchPointSource(
                        s.getBoolean("enabled", true),
                        s.getInt("amount", 1)
                ));
            }
        }
        sources = Map.copyOf(next);
    }

    public static void addPoints(Player player, String sourceId) {
        if (player == null || sourceId == null) return;
        ResearchPointSource source = sources.get(sourceId.toLowerCase());
        if (source == null || !source.enabled) return;
        addPoints(player, source.amount);
    }

    public static void addPoints(Player player, int amount) {
        if (player == null || amount == 0) return;
        MagicProfile profile = ProfileManager.getProfile(player.getUniqueId(), MagicProfile.class);
        profile.addResearchPoints(amount);
    }

    public static boolean hasSources() {
        return !sources.isEmpty();
    }

    private record ResearchPointSource(boolean enabled, int amount) {}
}
