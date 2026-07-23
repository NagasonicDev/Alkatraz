package me.nagasonic.alkatraz.hooks.placeholder;

import me.nagasonic.alkatraz.Alkatraz;
import me.nagasonic.alkatraz.playerdata.profiles.ProfileManager;
import me.nagasonic.alkatraz.playerdata.profiles.implementation.MagicProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public class LeaderboardPlaceholder implements Placeholder {

    private static class LeaderboardEntry {
        final UUID uuid;
        final String name;
        final double arcaneKnowledge;
        final int circleLevel;

        LeaderboardEntry(UUID uuid, String name, double arcaneKnowledge, int circleLevel) {
            this.uuid = uuid;
            this.name = name;
            this.arcaneKnowledge = arcaneKnowledge;
            this.circleLevel = circleLevel;
        }
    }

    private final long refreshIntervalMs;
    private final int maxEntries;
    private List<LeaderboardEntry> cachedBoard = new ArrayList<>();
    private long lastRefresh = 0;

    public LeaderboardPlaceholder(long refreshIntervalMinutes, int maxEntries) {
        this.refreshIntervalMs = refreshIntervalMinutes * 60 * 1000;
        this.maxEntries = maxEntries;
    }

    @Override
    public String name() {
        return "leaderboard";
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        refreshIfNeeded();

        if (params.equals("total_players")) {
            return String.valueOf(cachedBoard.size());
        }

        if (params.equals("rank")) {
            UUID uuid = player.getUniqueId();
            for (int i = 0; i < cachedBoard.size(); i++) {
                if (cachedBoard.get(i).uuid.equals(uuid)) {
                    return String.valueOf(i + 1);
                }
            }
            return "0";
        }

        if (params.startsWith("top_")) {
            String[] parts = params.split("_", 3);
            if (parts.length < 3) return "";
            int index;
            try {
                index = Integer.parseInt(parts[1]) - 1;
            } catch (NumberFormatException e) {
                return "";
            }
            if (index < 0 || index >= cachedBoard.size()) return "";
            LeaderboardEntry entry = cachedBoard.get(index);
            String field = parts[2];
            return switch (field) {
                case "name" -> entry.name;
                case "uuid" -> entry.uuid.toString();
                case "ak" -> String.valueOf(entry.arcaneKnowledge);
                case "circle" -> String.valueOf(entry.circleLevel);
                default -> "";
            };
        }

        return "";
    }

    private void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < refreshIntervalMs) return;
        lastRefresh = now;

        File playerDataDir = new File(Alkatraz.getInstance().getDataFolder().getParentFile(), "Alkatraz/playerdata");
        if (!playerDataDir.exists() || !playerDataDir.isDirectory()) return;

        File[] uuidFolders = playerDataDir.listFiles(File::isDirectory);
        if (uuidFolders == null) return;

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (File folder : uuidFolders) {
            try {
                UUID uuid = UUID.fromString(folder.getName());
                MagicProfile profile = ProfileManager.getProfile(uuid, MagicProfile.class);
                double ak = profile.getArcaneKnowledge();
                int circle = profile.getCircleLevel();
                OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                String name = offline.getName() != null ? offline.getName() : "Unknown";
                entries.add(new LeaderboardEntry(uuid, name, ak, circle));
            } catch (IllegalArgumentException ignored) {
            }
        }

        entries.sort((a, b) -> Double.compare(b.arcaneKnowledge, a.arcaneKnowledge));
        cachedBoard = entries.size() > maxEntries ? entries.subList(0, maxEntries) : entries;
    }
}
